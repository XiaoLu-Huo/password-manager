package com.pm.passwordmanager.domain.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;

import com.pm.passwordmanager.api.dto.response.MfaSetupResponse;
import com.pm.passwordmanager.domain.model.MfaConfig;
import com.pm.passwordmanager.domain.repository.MfaConfigRepository;
import com.pm.passwordmanager.domain.service.MfaService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;
import com.pm.passwordmanager.infrastructure.totp.TotpUtil;

import lombok.RequiredArgsConstructor;

/**
 * MFA 服务实现。
 * 使用 TOTP 实现多因素认证，TOTP 密钥和恢复码使用 DEK 加密存储。
 */
@Service
@RequiredArgsConstructor
public class MfaServiceImpl implements MfaService {

    private final MfaConfigRepository mfaConfigRepository;
    private final TotpUtil totpUtil;
    private final EncryptionEngine encryptionEngine;
    private final SessionService sessionService;

    @Override
    public MfaSetupResponse initSetup(Long userId) {
        // 检查是否已启用 MFA
        MfaConfig existing = mfaConfigRepository.findByUserId(userId).orElse(null);
        if (existing != null && Boolean.TRUE.equals(existing.getEnabled())) {
            throw new BusinessException(ErrorCode.MFA_ALREADY_ENABLED);
        }

        byte[] dek = getRequiredDek(userId);

        // 生成 TOTP 密钥和恢复码
        String secret = totpUtil.generateSecret();
        String[] recoveryCodes = totpUtil.generateRecoveryCodes();
        String qrCodeDataUri = totpUtil.generateQrCodeDataUri(secret, "user@passwordmanager");

        // 加密 TOTP 密钥
        String encryptedSecret = encryptString(secret, dek);

        // 加密恢复码（逗号分隔后加密）
        String recoveryCodesJoined = String.join(",", recoveryCodes);
        String encryptedRecoveryCodes = encryptString(recoveryCodesJoined, dek);

        // 保存或更新 MFA 配置（enabled=false，等待用户验证后启用）
        if (existing != null) {
            existing.setTotpSecretEncrypted(encryptedSecret);
            existing.setRecoveryCodesEncrypted(encryptedRecoveryCodes);
            existing.setEnabled(false);
            existing.setUpdatedAt(LocalDateTime.now());
            mfaConfigRepository.updateById(existing);
        } else {
            MfaConfig config = MfaConfig.builder()
                    .userId(userId)
                    .totpSecretEncrypted(encryptedSecret)
                    .enabled(false)
                    .recoveryCodesEncrypted(encryptedRecoveryCodes)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            mfaConfigRepository.save(config);
        }

        List<String> recoveryCodeList = Arrays.asList(recoveryCodes);
        return MfaSetupResponse.builder()
                .qrCodeUri(qrCodeDataUri)
                .recoveryCodes(recoveryCodeList)
                .build();
    }

    @Override
    public void confirmEnable(Long userId, String totpCode) {
        MfaConfig config = mfaConfigRepository.findByUserId(userId).orElse(null);
        if (config == null) {
            throw new BusinessException(ErrorCode.MFA_NOT_ENABLED);
        }
        if (Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(ErrorCode.MFA_ALREADY_ENABLED);
        }

        byte[] dek = getRequiredDek(userId);

        // 解密 TOTP 密钥并验证码
        String secret = decryptString(config.getTotpSecretEncrypted(), dek);
        if (!totpUtil.verifyCode(secret, totpCode)) {
            throw new BusinessException(ErrorCode.TOTP_INVALID);
        }

        // 验证通过，正式启用 MFA
        config.setEnabled(true);
        config.setUpdatedAt(LocalDateTime.now());
        mfaConfigRepository.updateById(config);
    }

    @Override
    public boolean verifyTotp(Long userId, String totpCode) {
        MfaConfig config = mfaConfigRepository.findByUserId(userId).orElse(null);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(ErrorCode.MFA_NOT_ENABLED);
        }

        byte[] dek = getRequiredDek(userId);
        String secret = decryptString(config.getTotpSecretEncrypted(), dek);

        return totpUtil.verifyCode(secret, totpCode);
    }

    @Override
    public void disable(Long userId) {
        MfaConfig config = mfaConfigRepository.findByUserId(userId).orElse(null);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(ErrorCode.MFA_NOT_ENABLED);
        }

        config.setEnabled(false);
        config.setTotpSecretEncrypted(null);
        config.setRecoveryCodesEncrypted(null);
        config.setUpdatedAt(LocalDateTime.now());
        mfaConfigRepository.updateById(config);
    }

    @Override
    public boolean isMfaEnabled(Long userId) {
        MfaConfig config = mfaConfigRepository.findByUserId(userId).orElse(null);
        return config != null && Boolean.TRUE.equals(config.getEnabled());
    }

    private byte[] getRequiredDek(Long userId) {
        byte[] dek = sessionService.getDek(userId);
        if (dek == null) {
            throw new BusinessException(ErrorCode.VAULT_LOCKED);
        }
        return dek;
    }

    /** 使用 DEK 加密字符串，返回 Base64 编码的 "iv:ciphertext" 格式。 */
    private String encryptString(String plaintext, byte[] dek) {
        EncryptedData encrypted = encryptionEngine.encrypt(
                plaintext.getBytes(StandardCharsets.UTF_8), dek);
        String ivBase64 = Base64.getEncoder().encodeToString(encrypted.getIv());
        String ciphertextBase64 = Base64.getEncoder().encodeToString(encrypted.getCiphertext());
        return ivBase64 + ":" + ciphertextBase64;
    }

    /** 解密 Base64 编码的 "iv:ciphertext" 格式字符串。 */
    private String decryptString(String encryptedStr, byte[] dek) {
        String[] parts = encryptedStr.split(":");
        byte[] iv = Base64.getDecoder().decode(parts[0]);
        byte[] ciphertext = Base64.getDecoder().decode(parts[1]);
        byte[] plaintext = encryptionEngine.decrypt(new EncryptedData(ciphertext, iv), dek);
        return new String(plaintext, StandardCharsets.UTF_8);
    }
}
