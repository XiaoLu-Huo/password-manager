package com.pm.passwordmanager.infrastructure.encryption;

import org.springframework.stereotype.Component;

import com.pm.passwordmanager.api.enums.PasswordStrengthLevel;

/**
 * 密码强度评估器。
 * 根据密码长度和字符类型数量评估密码强度。
 * <p>
 * 规则：
 * <ul>
 *   <li>长度 < 8 → WEAK</li>
 *   <li>长度 8-15 且 ≥ 2 种字符类型 → MEDIUM</li>
 *   <li>长度 ≥ 16 且 ≥ 3 种字符类型 → STRONG</li>
 *   <li>不满足 MEDIUM 或 STRONG 条件时 → WEAK</li>
 * </ul>
 * 字符类型包括：大写字母、小写字母、数字、特殊字符。
 */
@Component
public class PasswordStrengthEvaluator {

    /**
     * 评估密码强度。
     *
     * @param password 待评估的密码
     * @return 密码强度等级
     */
    public PasswordStrengthLevel evaluate(String password) {
        if (password == null || password.isEmpty()) {
            return PasswordStrengthLevel.WEAK;
        }

        int length = password.length();
        int charTypeCount = countCharacterTypes(password);

        if (length >= 16 && charTypeCount >= 3) {
            return PasswordStrengthLevel.STRONG;
        }
        if (length >= 8 && charTypeCount >= 2) {
            return PasswordStrengthLevel.MEDIUM;
        }
        return PasswordStrengthLevel.WEAK;
    }

    /**
     * 统计密码中包含的字符类型数量。
     * 字符类型：大写字母、小写字母、数字、特殊字符。
     *
     * @param password 密码
     * @return 字符类型数量（0-4）
     */
    private int countCharacterTypes(String password) {
        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUppercase = true;
            } else if (Character.isLowerCase(c)) {
                hasLowercase = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSpecial = true;
            }
        }

        int count = 0;
        if (hasUppercase) count++;
        if (hasLowercase) count++;
        if (hasDigit) count++;
        if (hasSpecial) count++;
        return count;
    }
}
