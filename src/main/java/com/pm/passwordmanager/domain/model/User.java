package com.pm.passwordmanager.domain.model;

import java.time.LocalDateTime;

import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户领域模型。
 * 包含主密码验证、账户锁定检查、失败计数等业务行为。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private static final java.util.regex.Pattern USERNAME_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9_-]{3,32}$");
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private Long id;
    private String username;
    private String email;
    private String masterPasswordHash;
    private String salt;
    private byte[] encryptionKeyEncrypted;
    private Integer failedAttempts;
    private LocalDateTime lockedUntil;
    private Integer autoLockMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 验证用户名格式：3-32 字符，仅允许字母、数字、下划线、连字符。
     */
    public static void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException(ErrorCode.USERNAME_INVALID_FORMAT);
        }
    }

    /**
     * 验证邮箱格式：基本邮箱格式校验。
     */
    public static void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(ErrorCode.EMAIL_INVALID_FORMAT);
        }
    }

    /**
     * 验证主密码复杂度：长度 ≥ 12，且包含大写字母、小写字母、数字、特殊字符中的至少三种。
     */
    public static void validatePasswordComplexity(String password) {
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
    public static int countCharacterTypes(String password) {
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

    /** 检查账户是否处于锁定状态。 */
    public void checkLockStatus() {
        if (lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil)) {
            long remainingMinutes = java.time.Duration.between(LocalDateTime.now(), lockedUntil).toMinutes() + 1;
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "账户已锁定，请 " + remainingMinutes + " 分钟后再试");
        }
    }

    /** 处理密码验证失败：递增失败次数，达到阈值时设置锁定。 */
    public void handleFailedAttempt() {
        int newFailedAttempts = (failedAttempts != null ? failedAttempts : 0) + 1;
        this.failedAttempts = newFailedAttempts;
        if (newFailedAttempts >= MAX_FAILED_ATTEMPTS) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES);
        }
    }

    /** 密码验证成功后重置失败计数。 */
    public void resetFailedAttempts() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }
}
