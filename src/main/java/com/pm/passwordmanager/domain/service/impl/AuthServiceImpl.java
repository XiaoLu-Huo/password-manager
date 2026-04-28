package com.pm.passwordmanager.domain.service.impl;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.pm.passwordmanager.api.dto.response.UnlockResultResponse;
import com.pm.passwordmanager.domain.command.LoginCommand;
import com.pm.passwordmanager.domain.command.RegisterCommand;
import com.pm.passwordmanager.domain.model.User;
import com.pm.passwordmanager.domain.repository.UserRepository;
import com.pm.passwordmanager.domain.service.AuthService;
import com.pm.passwordmanager.domain.service.MfaService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.config.SessionContextHolder;
import com.pm.passwordmanager.infrastructure.encryption.Argon2Hasher;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;

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
    private final ConcurrentHashMap<Long, byte[]> pendingMfaDek = new ConcurrentHashMap<>();

    /**
     * Maps MFA temporary token to userId for the MFA verification step.
     */
    private final ConcurrentHashMap<String, Long> mfaTokenToUserId = new ConcurrentHashMap<>();

    @Override
    public void register(RegisterCommand command) {
        String username = command.getUsername();
        String email = command.getEmail();
        String masterPassword = command.getMasterPassword();

        // 1. 验证用户名、邮箱、密码复杂度
        User.validateUsername(username);
        User.validateEmail(email);
        User.validatePasswordComplexity(masterPassword);

        // 2. 检查用户名唯一性
        if (userRepository.findByUsername(username).isPresent()) {
            throw new BusinessException(ErrorCode.USERNAME_DUPLICATE);
        }

        // 3. 检查邮箱唯一性
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATE);
        }

        // 4. 生成随机 salt
        byte[] salt = argon2Hasher.generateSalt();

        // 5. Argon2id 哈希密码
        String passwordHash = argon2Hasher.hash(masterPassword, salt);

        // 6. 派生 KEK
        byte[] kek = argon2Hasher.deriveKey(masterPassword, salt, KEK_LENGTH_BYTES);

        // 7. 生成随机 DEK
        byte[] dek = encryptionEngine.generateDek();

        // 8. 用 KEK 加密 DEK
        EncryptedData encryptedDek = encryptionEngine.encrypt(dek, kek);
        byte[] encryptedDekBlob = combineIvAndCiphertext(encryptedDek);

        // 9. 构建用户并保存
        User user = User.builder()
                .username(username)
                .email(email)
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
    public UnlockResultResponse login(LoginCommand command) {
        String identifier = command.getIdentifier();
        String masterPassword = command.getMasterPassword();

        // 1. 根据 identifier 是否包含 @ 决定查找策略
        User user;
        if (identifier.contains("@")) {
            user = userRepository.findByEmail(identifier).orElse(null);
        } else {
            user = userRepository.findByUsername(identifier).orElse(null);
        }

        // 2. 用户不存在 → 通用错误（不泄露用户是否存在）
        if (user == null) {
            throw new BusinessException(ErrorCode.CREDENTIALS_INVALID);
        }

        // 3. 检查锁定状态
        user.checkLockStatus();

        // 4. 验证密码
        byte[] salt = Base64.getDecoder().decode(user.getSalt());
        boolean passwordCorrect = argon2Hasher.verify(masterPassword, salt, user.getMasterPasswordHash());

        if (!passwordCorrect) {
            user.handleFailedAttempt();
            userRepository.updateById(user);
            throw new BusinessException(ErrorCode.CREDENTIALS_INVALID);
        }

        // 5. 密码正确：重置失败计数
        user.resetFailedAttempts();
        userRepository.updateById(user);

        // 6. 派生 KEK 并解密 DEK
        byte[] kek = argon2Hasher.deriveKey(masterPassword, salt, KEK_LENGTH_BYTES);
        EncryptedData encryptedDek = splitIvAndCiphertext(user.getEncryptionKeyEncrypted());
        byte[] dek = encryptionEngine.decrypt(encryptedDek, kek);

        // 7. 检查 MFA 是否启用
        if (mfaService.isMfaEnabled(user.getId())) {
            String mfaToken = UUID.randomUUID().toString();
            mfaTokenToUserId.put(mfaToken, user.getId());
            pendingMfaDek.put(user.getId(), dek);
            return UnlockResultResponse.builder()
                    .mfaRequired(true)
                    .sessionToken(mfaToken)
                    .build();
        }

        // 8. MFA 未启用：直接将 DEK 存入会话
        sessionService.storeDek(user.getId(), dek);

        return UnlockResultResponse.builder()
                .mfaRequired(false)
                .sessionToken(sessionService.generateToken(user.getId()))
                .build();
    }

    @Override
    public UnlockResultResponse verifyTotpAndUnlock(String mfaToken, String totpCode) {
        // 1. 从 mfaToken 查找 userId
        Long userId = mfaTokenToUserId.get(mfaToken);
        if (userId == null) {
            throw new BusinessException(ErrorCode.VAULT_LOCKED);
        }

        // 2. 获取待验证的 DEK
        byte[] dek = pendingMfaDek.get(userId);
        if (dek == null) {
            throw new BusinessException(ErrorCode.VAULT_LOCKED);
        }

        // 3. 临时存入 DEK 以便 MfaService 解密 TOTP 密钥
        sessionService.storeDek(userId, dek);

        // 4. 验证 TOTP 码
        boolean valid = mfaService.verifyTotp(userId, totpCode);
        if (!valid) {
            sessionService.clearSession(userId);
            throw new BusinessException(ErrorCode.TOTP_INVALID);
        }

        // 5. 验证通过：清除待验证状态
        pendingMfaDek.remove(userId);
        mfaTokenToUserId.remove(mfaToken);

        return UnlockResultResponse.builder()
                .mfaRequired(false)
                .sessionToken(sessionService.generateToken(userId))
                .build();
    }

    @Override
    public Long getCurrentUserId() {
        Long userId = SessionContextHolder.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }
        return userId;
    }

    @Override
    public boolean isInitialized() {
        return true;
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
