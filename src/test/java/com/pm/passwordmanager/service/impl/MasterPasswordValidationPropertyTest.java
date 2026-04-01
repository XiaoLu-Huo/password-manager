package com.pm.passwordmanager.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property 1: 主密码复杂度验证
 * For any 字符串，主密码验证函数应当且仅当该字符串长度 ≥ 12 且包含大写字母、小写字母、数字、特殊字符中的至少三种时返回通过。
 *
 * Validates: Requirements 1.2
 */
@Label("Feature: password-manager, Property 1: 主密码复杂度验证")
class MasterPasswordValidationPropertyTest {

    private final AuthServiceImpl authService = new AuthServiceImpl(null, null, null, null);

    /**
     * 长度 ≥ 12 且包含至少三种字符类型的密码应通过验证。
     *
     * **Validates: Requirements 1.2**
     */
    @Property(tries = 100)
    @Label("should_acceptPassword_when_lengthAtLeast12AndAtLeast3CharTypes")
    void should_acceptPassword_when_lengthAtLeast12AndAtLeast3CharTypes(
            @ForAll("validMasterPasswords") String password
    ) {
        // Should not throw any exception
        authService.validatePasswordComplexity(password);

        // Verify our oracle agrees
        assertThat(password.length()).isGreaterThanOrEqualTo(12);
        assertThat(countCharacterTypes(password)).isGreaterThanOrEqualTo(3);
    }

    /**
     * 长度 < 12 的密码应被拒绝，无论包含多少种字符类型。
     *
     * **Validates: Requirements 1.2**
     */
    @Property(tries = 100)
    @Label("should_rejectPassword_when_lengthLessThan12")
    void should_rejectPassword_when_lengthLessThan12(
            @ForAll("shortPasswords") String password
    ) {
        assertThatThrownBy(() -> authService.validatePasswordComplexity(password))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MASTER_PASSWORD_TOO_WEAK));
    }

    /**
     * 长度 ≥ 12 但只包含 1-2 种字符类型的密码应被拒绝。
     *
     * **Validates: Requirements 1.2**
     */
    @Property(tries = 100)
    @Label("should_rejectPassword_when_lengthOkButFewerThan3CharTypes")
    void should_rejectPassword_when_lengthOkButFewerThan3CharTypes(
            @ForAll("longPasswordsFewTypes") String password
    ) {
        assertThat(password.length()).isGreaterThanOrEqualTo(12);
        assertThat(countCharacterTypes(password)).isLessThan(3);

        assertThatThrownBy(() -> authService.validatePasswordComplexity(password))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MASTER_PASSWORD_TOO_WEAK));
    }

    /**
     * 对任意字符串，验证结果应与 oracle 一致：
     * 通过当且仅当长度 ≥ 12 且字符类型 ≥ 3。
     *
     * **Validates: Requirements 1.2**
     */
    @Property(tries = 200)
    @Label("should_matchOracleResult_when_anyString")
    void should_matchOracleResult_when_anyString(
            @ForAll("anyPasswords") String password
    ) {
        boolean shouldPass = password.length() >= 12 && countCharacterTypes(password) >= 3;

        if (shouldPass) {
            authService.validatePasswordComplexity(password);
        } else {
            assertThatThrownBy(() -> authService.validatePasswordComplexity(password))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.MASTER_PASSWORD_TOO_WEAK));
        }
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> validMasterPasswords() {
        return Arbitraries.integers().between(12, 64).flatMap(len -> {
            // Ensure at least 3 character types: lowercase + uppercase + digit (+ optional special)
            int remaining = len - 3;
            return Combinators.combine(
                    Arbitraries.strings().withCharRange('a', 'z').ofLength(Math.max(remaining, 1)),
                    Arbitraries.strings().withCharRange('A', 'Z').ofLength(1),
                    Arbitraries.strings().withCharRange('0', '9').ofLength(1),
                    Arbitraries.of('!', '@', '#', '$', '%').map(String::valueOf)
            ).as((base, upper, digit, special) -> {
                String result = base + upper + digit + special;
                // Trim to exact length if needed
                if (result.length() > len) {
                    return result.substring(0, len);
                }
                return result;
            }).filter(s -> s.length() >= 12 && countCharacterTypes(s) >= 3);
        });
    }

    @Provide
    Arbitrary<String> shortPasswords() {
        return Arbitraries.integers().between(1, 11).flatMap(len ->
                Combinators.combine(
                        Arbitraries.strings().withCharRange('a', 'z').ofLength(Math.max(len - 2, 1)),
                        Arbitraries.strings().withCharRange('A', 'Z').ofMinLength(0).ofMaxLength(Math.min(1, len)),
                        Arbitraries.strings().withCharRange('0', '9').ofMinLength(0).ofMaxLength(Math.min(1, len))
                ).as((base, upper, digit) -> {
                    String result = base + upper + digit;
                    if (result.length() > len) {
                        return result.substring(0, len);
                    }
                    return result;
                }).filter(s -> s.length() >= 1 && s.length() < 12)
        );
    }

    @Provide
    Arbitrary<String> longPasswordsFewTypes() {
        return Arbitraries.oneOf(
                // 1 type only: lowercase
                Arbitraries.integers().between(12, 40).flatMap(len ->
                        Arbitraries.strings().withCharRange('a', 'z').ofLength(len)),
                // 1 type only: uppercase
                Arbitraries.integers().between(12, 40).flatMap(len ->
                        Arbitraries.strings().withCharRange('A', 'Z').ofLength(len)),
                // 2 types: lowercase + digits
                Arbitraries.integers().between(12, 40).flatMap(len ->
                        Combinators.combine(
                                Arbitraries.strings().withCharRange('a', 'z').ofLength(len - 1),
                                Arbitraries.strings().withCharRange('0', '9').ofLength(1)
                        ).as((base, digit) -> base + digit)),
                // 2 types: uppercase + lowercase
                Arbitraries.integers().between(12, 40).flatMap(len ->
                        Combinators.combine(
                                Arbitraries.strings().withCharRange('a', 'z').ofLength(len - 1),
                                Arbitraries.strings().withCharRange('A', 'Z').ofLength(1)
                        ).as((base, upper) -> base + upper))
        );
    }

    @Provide
    Arbitrary<String> anyPasswords() {
        return Arbitraries.oneOf(
                // Random ASCII strings of various lengths
                Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(64),
                // Specifically short strings
                Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(11),
                // Specifically long strings with mixed chars
                Arbitraries.integers().between(12, 64).flatMap(len ->
                        Arbitraries.strings().ascii().ofLength(len))
        );
    }

    // --- Oracle helper ---

    private int countCharacterTypes(String password) {
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        return (hasUpper ? 1 : 0) + (hasLower ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
    }
}
