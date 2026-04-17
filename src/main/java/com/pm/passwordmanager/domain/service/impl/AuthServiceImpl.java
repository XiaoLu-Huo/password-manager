package com.pm.passwordmanager.domain.service.impl;

import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.stereotype.Service;

import com.pm.passwordmanager.api.dto.response.UnlockResultResponse;
import com.pm.passwordmanager.domain.command.SetupMasterPasswordCommand;
import com.pm.passwordmanager.domain.command.UnlockVaultCommand;
import com.pm.passwordmanager.domain.model.User;
import com.pm.passwordmanager.domain.repository.UserRepository;
import com.pm.passwordmanager.domain.service.AuthService;
import com.pm.passwordmanager.domain.service.MfaService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.encryption.Argon2Hasher;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;

import com.pm.passwordmanager.infrastructure.config.SessionContextHolder;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int KEK_LENGTH_BYTES = 32;

    private final UserRepository userRepository;
    private final Argon2Hasher argon2Hasher;
    private final EncryptionEngine encryptionEngine;
    private final SessionService sessionService;
    private final MfaService mfaService;

    /**
     * Temporary storage for DEK pending MFA verification.
     * Key: userId, Value: decrypted DEK bytes.
     */
    private final java.util.concurrent.ConcurrentHashMap<Long, byte[]> pendingMfaDek = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void setup(SetupMasterPasswordCommand command) {
        String masterPassword = command.getMasterPassword();

        // 1. 验证密码复杂度（委托给领域模型）
        User.validatePasswordComplexity(masterPassword);

        // 2. 生成随机 salt
        byte[] salt = argon2Hasher.generateSalt();

        // 3. Argon2id 哈希密码（用于存储验证）
        String passwordHash = argon2Hasher.hash(masterPassword, salt);

        // 4. 派生 KEK
        byte[] kek = argon2Hasher.deriveKey(masterPassword, salt, KEK_LENGTH_BYTES);

        // 5. 生成随机 DEK
        byte[] dek = encryptionEngine.generateDek();

        // 6. 用 KEK 加密 DEK
        EncryptedData encryptedDek = encryptionEngine.encrypt(dek, kek);

        // 7. 将 IV + ciphertext 合并存储
        byte[] encryptedDekBlob = combineIvAndCiphertext(encryptedDek);

        // 8. 构建领域模型并通过仓储持久化
        User user = User.builder()
                .masterPasswordHash(passwordHash)
                .salt(Base64.getEncoder().encodeToString(salt))
                .encryptionKeyEncrypted(encryptedDekBlob)
                .failedAttempts(0)
                .autoLockMinutes(15)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
    }

    @Override
    public UnlockResultResponse unlock(UnlockVaultCommand command) {
        String masterPassword = command.getMasterPassword();

        // 1. 查询用户记录（单用户系统，取第一条）
        User user = getUser();

        // 2. 检查是否被锁定（委托给领域模型）
        user.checkLockStatus();

        // 3. 验证密码
        byte[] salt = Base64.getDecoder().decode(user.getSalt());
        boolean passwordCorrect = argon2Hasher.verify(masterPassword, salt, user.getMasterPasswordHash());

        if (!passwordCorrect) {
            user.handleFailedAttempt();
            userRepository.updateById(user);
            throw new BusinessException(ErrorCode.MASTER_PASSWORD_WRONG);
        }

        // 4. 密码正确：重置失败计数（委托给领域模型）
        user.resetFailedAttempts();
        userRepository.updateById(user);

        // 5. 派生 KEK 并解密 DEK
        byte[] kek = argon2Hasher.deriveKey(masterPassword, salt, KEK_LENGTH_BYTES);
        EncryptedData encryptedDek = splitIvAndCiphertext(user.getEncryptionKeyEncrypted());
        byte[] dek = encryptionEngine.decrypt(encryptedDek, kek);

        // 6. 检查 MFA 是否启用
        if (mfaService.isMfaEnabled(user.getId())) {
            pendingMfaDek.put(user.getId(), dek);
            return UnlockResultResponse.builder()
                    .mfaRequired(true)
                    .sessionToken(null)
                    .build();
        }

        // 7. MFA 未启用：直接将 DEK 存入会话
        sessionService.storeDek(user.getId(), dek);

        return UnlockResultResponse.builder()
                .mfaRequired(false)
                .sessionToken(sessionService.generateToken(user.getId()))
                .build();
    }

    @Override
    public UnlockResultResponse verifyTotpAndUnlock(String totpCode) {
        User user = getUser();

        // 1. 检查是否有待验证的 MFA 会话
        byte[] dek = pendingMfaDek.get(user.getId());
        if (dek == null) {
            throw new BusinessException(ErrorCode.VAULT_LOCKED);
        }

        // 2. 临时存入 DEK 以便 MfaService 解密 TOTP 密钥
        sessionService.storeDek(user.getId(), dek);

        // 3. 验证 TOTP 码
        boolean valid = mfaService.verifyTotp(user.getId(), totpCode);
        if (!valid) {
            sessionService.clearSession(user.getId());
            throw new BusinessException(ErrorCode.TOTP_INVALID);
        }

        // 4. 验证通过：清除待验证状态，DEK 已在会话中
        pendingMfaDek.remove(user.getId());

        return UnlockResultResponse.builder()
                .mfaRequired(false)
                .sessionToken(sessionService.generateToken(user.getId()))
                .build();
    }

    @Override
    public Long getCurrentUserId() {
        // Prefer the userId set by SessionInterceptor (token-validated)
        Long userId = SessionContextHolder.getCurrentUserId();
        if (userId != null) {
            return userId;
        }
        // Fallback for non-intercepted paths (e.g., setup/unlock)
        return getUser().getId();
    }

    @Override
    public boolean isInitialized() {
        return userRepository.findFirst().isPresent();
    }

    private User getUser() {
        return userRepository.findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.VAULT_LOCKED));
    }

    /** 将 IV 和密文合并为单个字节数组：[IV (12 bytes)] + [ciphertext] */
    private byte[] combineIvAndCiphertext(EncryptedData encryptedData) {
        byte[] iv = encryptedData.getIv();
        byte[] ciphertext = encryptedData.getCiphertext();
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        return combined;
    }

    /** 从合并的字节数组中拆分 IV 和密文：前 12 字节为 IV，其余为密文 */
    private EncryptedData splitIvAndCiphertext(byte[] combined) {
        byte[] iv = new byte[12];
        byte[] ciphertext = new byte[combined.length - 12];
        System.arraycopy(combined, 0, iv, 0, 12);
        System.arraycopy(combined, 12, ciphertext, 0, ciphertext.length);
        return new EncryptedData(ciphertext, iv);
    }
}
