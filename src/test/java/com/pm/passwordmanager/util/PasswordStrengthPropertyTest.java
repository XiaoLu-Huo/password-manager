package com.pm.passwordmanager.infrastructure.encryption;

import static org.assertj.core.api.Assertions.assertThat;

import com.pm.passwordmanager.api.enums.PasswordStrengthLevel;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.StringLength;

/**
 * Property 13: 密码强度评估规则
 * 验证：对任意密码字符串，评估结果应符合长度和字符类型的组合规则。
 *
 * 规则：
 * - 长度 < 8 → WEAK
 * - 长度 8-15 且 ≥ 2 种字符类型 → MEDIUM
 * - 长度 ≥ 16 且 ≥ 3 种字符类型 → STRONG
 * - 不满足 MEDIUM 或 STRONG 条件时 → WEAK
 *
 * Validates: Requirements 6.1
 */
@Label("Feature: password-manager, Property 13: 密码强度评估规则")
class PasswordStrengthPropertyTest {

    private final PasswordStrengthEvaluator evaluator = new PasswordStrengthEvaluator();

    /**
     * 任意长度 < 8 的密码，强度应为 WEAK。
     *
     * **Validates: Requirements 6.1**
     */
    @Property(tries = 100)
    @Label("should_returnWeak_when_lengthLessThan8")
    void should_returnWeak_when_lengthLessThan8(
            @ForAll @StringLength(min = 1, max = 7) String password
    ) {
        assertThat(evaluator.evaluate(password)).isEqualTo(PasswordStrengthLevel.WEAK);
    }

    /**
     * 长度 8-15 且包含 ≥ 2 种字符类型的密码，强度应为 MEDIUM。
     *
     * **Validates: Requirements 6.1**
     */
    @Property(tries = 100)
    @Label("should_returnMedium_when_length8to15WithAtLeast2CharTypes")
    void should_returnMedium_when_length8to15WithAtLeast2CharTypes(
            @ForAll("mediumPasswords") String password
    ) {
        PasswordStrengthLevel result = evaluator.evaluate(password);
        assertThat(result).isEqualTo(PasswordStrengthLevel.MEDIUM);
    }

    /**
     * 长度 ≥ 16 且包含 ≥ 3 种字符类型的密码，强度应为 STRONG。
     *
     * **Validates: Requirements 6.1**
     */
    @Property(tries = 100)
    @Label("should_returnStrong_when_length16PlusWith3PlusCharTypes")
    void should_returnStrong_when_length16PlusWith3PlusCharTypes(
            @ForAll("strongPasswords") String password
    ) {
        PasswordStrengthLevel result = evaluator.evaluate(password);
        assertThat(result).isEqualTo(PasswordStrengthLevel.STRONG);
    }

    /**
     * 长度 8-15 但只有 1 种字符类型的密码，强度应为 WEAK。
     *
     * **Validates: Requirements 6.1**
     */
    @Property(tries = 100)
    @Label("should_returnWeak_when_length8to15With1CharType")
    void should_returnWeak_when_length8to15With1CharType(
            @ForAll("singleTypePasswords8to15") String password
    ) {
        assertThat(evaluator.evaluate(password)).isEqualTo(PasswordStrengthLevel.WEAK);
    }

    /**
     * 长度 ≥ 16 但只有 1-2 种字符类型的密码，不应为 STRONG。
     * 长度 ≥ 16 且 ≥ 2 种字符类型 → MEDIUM；仅 1 种 → WEAK。
     *
     * **Validates: Requirements 6.1**
     */
    @Property(tries = 100)
    @Label("should_notReturnStrong_when_length16PlusWithLessThan3CharTypes")
    void should_notReturnStrong_when_length16PlusWithLessThan3CharTypes(
            @ForAll("longPasswordsFewTypes") String password
    ) {
        PasswordStrengthLevel result = evaluator.evaluate(password);
        assertThat(result).isNotEqualTo(PasswordStrengthLevel.STRONG);
    }

    /**
     * 对任意非空密码，评估结果应始终是三个有效等级之一。
     *
     * **Validates: Requirements 6.1**
     */
    @Property(tries = 100)
    @Label("should_returnValidLevel_when_anyPassword")
    void should_returnValidLevel_when_anyPassword(
            @ForAll @StringLength(min = 1, max = 200) String password
    ) {
        PasswordStrengthLevel result = evaluator.evaluate(password);
        assertThat(result).isIn(PasswordStrengthLevel.WEAK, PasswordStrengthLevel.MEDIUM, PasswordStrengthLevel.STRONG);
    }

    /**
     * 对任意非空密码，评估结果应与根据规则手动计算的结果一致（oracle 属性）。
     *
     * **Validates: Requirements 6.1**
     */
    @Property(tries = 200)
    @Label("should_matchOracleResult_when_anyPassword")
    void should_matchOracleResult_when_anyPassword(
            @ForAll @StringLength(min = 1, max = 200) String password
    ) {
        PasswordStrengthLevel expected = oracle(password);
        PasswordStrengthLevel actual = evaluator.evaluate(password);
        assertThat(actual).isEqualTo(expected);
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> mediumPasswords() {
        return Arbitraries.integers().between(8, 15).flatMap(len ->
                Combinators.combine(
                        Arbitraries.strings().withCharRange('a', 'z').ofLength(len - 1),
                        Arbitraries.strings().withCharRange('0', '9').ofLength(1)
                ).as((base, digit) -> base + digit)
        );
    }

    @Provide
    Arbitrary<String> strongPasswords() {
        return Arbitraries.integers().between(16, 64).flatMap(len -> {
            int remaining = len - 3;
            return Combinators.combine(
                    Arbitraries.strings().withCharRange('a', 'z').ofLength(remaining),
                    Arbitraries.strings().withCharRange('A', 'Z').ofLength(1),
                    Arbitraries.strings().withCharRange('0', '9').ofLength(1),
                    Arbitraries.of('!', '@', '#', '$', '%').map(String::valueOf)
            ).as((base, upper, digit, special) -> base + upper + digit + special);
        });
    }

    @Provide
    Arbitrary<String> singleTypePasswords8to15() {
        return Arbitraries.oneOf(
                Arbitraries.integers().between(8, 15).flatMap(len ->
                        Arbitraries.strings().withCharRange('a', 'z').ofLength(len)),
                Arbitraries.integers().between(8, 15).flatMap(len ->
                        Arbitraries.strings().withCharRange('A', 'Z').ofLength(len)),
                Arbitraries.integers().between(8, 15).flatMap(len ->
                        Arbitraries.strings().withCharRange('0', '9').ofLength(len))
        );
    }

    @Provide
    Arbitrary<String> longPasswordsFewTypes() {
        return Arbitraries.oneOf(
                // 1 type only
                Arbitraries.integers().between(16, 40).flatMap(len ->
                        Arbitraries.strings().withCharRange('a', 'z').ofLength(len)),
                // 2 types only
                Arbitraries.integers().between(16, 40).flatMap(len ->
                        Combinators.combine(
                                Arbitraries.strings().withCharRange('a', 'z').ofLength(len - 1),
                                Arbitraries.strings().withCharRange('0', '9').ofLength(1)
                        ).as((base, digit) -> base + digit))
        );
    }

    // --- Oracle implementation ---

    private PasswordStrengthLevel oracle(String password) {
        int length = password.length();
        int types = countTypes(password);

        if (length >= 16 && types >= 3) {
            return PasswordStrengthLevel.STRONG;
        }
        if (length >= 8 && types >= 2) {
            return PasswordStrengthLevel.MEDIUM;
        }
        return PasswordStrengthLevel.WEAK;
    }

    private int countTypes(String password) {
        boolean upper = false, lower = false, digit = false, special = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (Character.isDigit(c)) digit = true;
            else special = true;
        }
        return (upper ? 1 : 0) + (lower ? 1 : 0) + (digit ? 1 : 0) + (special ? 1 : 0);
    }
}
