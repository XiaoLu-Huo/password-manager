package com.pm.passwordmanager.domain.service.impl;

import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.stereotype.Service;

import com.pm.passwordmanager.api.dto.request.CreateMasterPasswordRequest;
import com.pm.passwordmanager.api.dto.request.UnlockVaultRequest;
import com.pm.passwordmanager.api.dto.response.UnlockResultResponse;
import com.pm.passwordmanager.infrastructure.persistence.entity.UserEntity;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.persistence.mapper.UserMapper;
import com.pm.passwordmanager.domain.service.AuthService;
import com.pm.passwordmanager.domain.service.MfaService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.infrastructure.encryption.Argon2Hasher;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;
    private static final int KEK_LENGTH_BYTES = 32;

    private final UserMapper userMapper;
    private final Argon2Hasher argon2Hasher;
    private final EncryptionEngine encryptionEngine;
    private final SessionService sessionService;
    private final MfaService mfaService;

    /**
     * Temporary storage for DEK pending MFA verification.
     * Key: userId, Value: decrypted DEK bytes.
     * In production this would use a more robust mechanism, but for single-user local app this suffices.
     */
    private final java.util.concurrent.ConcurrentHashMap<Long, byte[]> pendingMfaDek = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void setup(CreateMasterPasswordRequest request) {
        String masterPassword = request.getMasterPassword();

        // 1. 验证密码复杂度
        validatePasswordComplexity(masterPassword);

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

        // 8. 存储用户记录
        UserEntity user = UserEntity.builder()
                .masterPasswordHash(passwordHash)
                .salt(Base64.getEncoder().encodeToString(salt))
                .encryptionKeyEncrypted(encryptedDekBlob)
                .failedAttempts(0)
                .autoLockMinutes(5)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userMapper.insert(user);
    }

    @Override
    public UnlockResultResponse unlock(UnlockVaultRequest request) {
        String masterPassword = request.getMasterPassword();

        // 1. 查询用户记录（单用户系统，取第一条）
        UserEntity user = getUser();

        // 2. 检查是否被锁定
        checkLockStatus(user);

        // 3. 验证密码
        byte[] salt = Base64.getDecoder().decode(user.getSalt());
        boolean passwordCorrect = argon2Hasher.verify(masterPassword, salt, user.getMasterPasswordHash());

        if (!passwordCorrect) {
            handleFailedAttempt(user);
            throw new BusinessException(ErrorCode.MASTER_PASSWORD_WRONG);
        }

        // 4. 密码正确：重置失败计数
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userMapper.updateById(user);

        // 5. 派生 KEK 并解密 DEK
        byte[] kek = argon2Hasher.deriveKey(masterPassword, salt, KEK_LENGTH_BYTES);
        EncryptedData encryptedDek = splitIvAndCiphertext(user.getEncryptionKeyEncrypted());
        byte[] dek = encryptionEngine.decrypt(encryptedDek, kek);

        // 6. 检查 MFA 是否启用
        if (mfaService.isMfaEnabled(user.getId())) {
            // MFA 启用：暂存 DEK，等待 TOTP 验证
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
                .sessionToken(null)
                .build();
    }

    @Override
    public UnlockResultResponse verifyTotpAndUnlock(String totpCode) {
        UserEntity user = getUser();

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
            // 验证失败：清除临时会话
            sessionService.clearSession(user.getId());
            throw new BusinessException(ErrorCode.TOTP_INVALID);
        }

        // 4. 验证通过：清除待验证状态，DEK 已在会话中
        pendingMfaDek.remove(user.getId());

        return UnlockResultResponse.builder()
                .mfaRequired(false)
                .sessionToken("authenticated")
                .build();
    }

    /**
     * 验证主密码复杂度：长度 ≥ 12，且包含大写字母、小写字母、数字、特殊字符中的至少三种。
     */
    public void validatePasswordComplexity(String password) {
        if (password == null || password.length() < 12) {
            throw new BusinessException(ErrorCode.MASTER_PASSWORD_TOO_WEAK);
        }

        int typeCount = countCharacterTypes(password);
        if (typeCount < 3) {
            throw new BusinessException(ErrorCode.MASTER_PASSWORD_TOO_WEAK);
        }
    }

    /**
     * 统计密码中包含的字符类型数量。
     *
     * @return 包含的字符类型数（0-4）
     */
    public int countCharacterTypes(String password) {
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSpecial = true;
            }
        }

        int count = 0;
        if (hasUpper) count++;
        if (hasLower) count++;
        if (hasDigit) count++;
        if (hasSpecial) count++;
        return count;
    }

    private UserEntity getUser() {
        UserEntity user = userMapper.selectOne(null);
        if (user == null) {
            throw new BusinessException(ErrorCode.VAULT_LOCKED);
        }
        return user;
    }

    private void checkLockStatus(UserEntity user) {
        if (user.getLockedUntil() != null && LocalDateTime.now().isBefore(user.getLockedUntil())) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
    }

    private void handleFailedAttempt(UserEntity user) {
        int newFailedAttempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(newFailedAttempts);

        if (newFailedAttempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
        }

        userMapper.updateById(user);
    }

    /**
     * 将 IV 和密文合并为单个字节数组：[IV (12 bytes)] + [ciphertext]
     */
    private byte[] combineIvAndCiphertext(EncryptedData encryptedData) {
        byte[] iv = encryptedData.getIv();
        byte[] ciphertext = encryptedData.getCiphertext();
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        return combined;
    }

    /**
     * 从合并的字节数组中拆分 IV 和密文：前 12 字节为 IV，其余为密文
     */
    private EncryptedData splitIvAndCiphertext(byte[] combined) {
        byte[] iv = new byte[12];
        byte[] ciphertext = new byte[combined.length - 12];
        System.arraycopy(combined, 0, iv, 0, 12);
        System.arraycopy(combined, 12, ciphertext, 0, ciphertext.length);
        return new EncryptedData(ciphertext, iv);
    }
}
