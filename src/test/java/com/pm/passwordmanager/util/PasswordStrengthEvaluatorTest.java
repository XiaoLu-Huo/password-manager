package com.pm.passwordmanager.util;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.pm.passwordmanager.enums.PasswordStrengthLevel;

class PasswordStrengthEvaluatorTest {

    private PasswordStrengthEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new PasswordStrengthEvaluator();
    }

    // --- WEAK cases ---

    @Test
    void should_returnWeak_when_passwordIsNull() {
        assertThat(evaluator.evaluate(null)).isEqualTo(PasswordStrengthLevel.WEAK);
    }

    @Test
    void should_returnWeak_when_passwordIsEmpty() {
        assertThat(evaluator.evaluate("")).isEqualTo(PasswordStrengthLevel.WEAK);
    }

    @Test
    void should_returnWeak_when_lengthLessThan8() {
        assertThat(evaluator.evaluate("Abc1!")).isEqualTo(PasswordStrengthLevel.WEAK);
    }

    @Test
    void should_returnWeak_when_length8ButOnly1CharType() {
        // 8 lowercase letters — only 1 char type
        assertThat(evaluator.evaluate("abcdefgh")).isEqualTo(PasswordStrengthLevel.WEAK);
    }

    @Test
    void should_returnWeak_when_length16ButOnly1CharType() {
        // 16 digits — only 1 char type
        assertThat(evaluator.evaluate("1234567890123456")).isEqualTo(PasswordStrengthLevel.WEAK);
    }

    // --- MEDIUM cases ---

    @Test
    void should_returnMedium_when_length8With2CharTypes() {
        // 8 chars: lowercase + digits
        assertThat(evaluator.evaluate("abcd1234")).isEqualTo(PasswordStrengthLevel.MEDIUM);
    }

    @Test
    void should_returnMedium_when_length15With2CharTypes() {
        // 15 chars: uppercase + lowercase
        assertThat(evaluator.evaluate("AbcDefGhiJklMno")).isEqualTo(PasswordStrengthLevel.MEDIUM);
    }

    @Test
    void should_returnMedium_when_length16With2CharTypes() {
        // 16 chars but only 2 char types → MEDIUM (not enough types for STRONG)
        assertThat(evaluator.evaluate("abcdefgh12345678")).isEqualTo(PasswordStrengthLevel.MEDIUM);
    }

    // --- STRONG cases ---

    @Test
    void should_returnStrong_when_length16With3CharTypes() {
        // 16 chars: lowercase + uppercase + digits
        assertThat(evaluator.evaluate("Abcdefgh12345678")).isEqualTo(PasswordStrengthLevel.STRONG);
    }

    @Test
    void should_returnStrong_when_length16With4CharTypes() {
        // 16 chars: all 4 types
        assertThat(evaluator.evaluate("Abcdefg1234567!@")).isEqualTo(PasswordStrengthLevel.STRONG);
    }

    @Test
    void should_returnStrong_when_veryLongWith3CharTypes() {
        // 20 chars: lowercase + digits + special
        assertThat(evaluator.evaluate("abcdef1234!@#$%^&*()")).isEqualTo(PasswordStrengthLevel.STRONG);
    }
}
