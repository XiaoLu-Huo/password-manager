package com.pm.passwordmanager.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import com.pm.passwordmanager.domain.command.GeneratePasswordCommand;
import com.pm.passwordmanager.api.dto.response.GeneratedPasswordResponse;
import com.pm.passwordmanager.domain.repository.PasswordRuleRepository;
import com.pm.passwordmanager.infrastructure.encryption.PasswordStrengthEvaluator;
import com.pm.passwordmanager.infrastructure.encryption.SecureRandomUtil;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import org.mockito.Mockito;

/**
 * Property 5: 密码生成器遵循配置规则
 * 验证：对任意有效的密码规则配置（长度 8-128，至少选择一种字符类型），
 * 生成的密码长度应等于配置的长度，且仅包含配置中启用的字符类型，
 * 并且至少包含每种启用类型的一个字符。
 *
 * **Validates: Requirements 2.4**
 */
@Label("Feature: password-manager, Property 5: 密码生成器遵循配置规则")
class PasswordGeneratorPropertyTest {

    private static final String UPPERCASE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE_CHARS = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGIT_CHARS = "0123456789";
    private static final String SPECIAL_CHARS = "!@#$%^&*()-_=+[]{}|;:',.<>?/`~";

    private final PasswordGeneratorServiceImpl service;

    PasswordGeneratorPropertyTest() {
        // Use a real SecureRandom-backed SecureRandomUtil via spy
        SecureRandomUtil realSecureRandomUtil = new SecureRandomUtil();
        PasswordStrengthEvaluator realEvaluator = new PasswordStrengthEvaluator();
        PasswordRuleRepository mockRepository = Mockito.mock(PasswordRuleRepository.class);
        this.service = new PasswordGeneratorServiceImpl(realSecureRandomUtil, realEvaluator, mockRepository);
    }

    /**
     * 生成密码长度应等于配置长度，仅包含启用的字符类型，且每种启用类型至少一个。
     *
     * **Validates: Requirements 2.4**
     */
    @Property(tries = 100)
    @Label("should_generatePasswordMatchingConfig_when_validRuleProvided")
    void should_generatePasswordMatchingConfig_when_validRuleProvided(
            @ForAll("validPasswordConfigs") PasswordConfig config
    ) {
        GeneratePasswordCommand command = GeneratePasswordCommand.builder()
                .length(config.length)
                .includeUppercase(config.includeUppercase)
                .includeLowercase(config.includeLowercase)
                .includeDigits(config.includeDigits)
                .includeSpecial(config.includeSpecial)
                .useDefault(false)
                .build();

        GeneratedPasswordResponse response = service.generatePassword(command);
        String password = response.getPassword();

        // 1. Length equals configured length
        assertThat(password).hasSize(config.length);

        // 2. Only contains characters from enabled types
        String allowedPool = buildAllowedPool(config);
        for (char c : password.toCharArray()) {
            assertThat(allowedPool).contains(String.valueOf(c));
        }

        // 3. At least one character from each enabled type
        if (config.includeUppercase) {
            assertThat(password).containsPattern("[A-Z]");
        }
        if (config.includeLowercase) {
            assertThat(password).containsPattern("[a-z]");
        }
        if (config.includeDigits) {
            assertThat(password).containsPattern("[0-9]");
        }
        if (config.includeSpecial) {
            assertThat(containsAnySpecial(password)).isTrue();
        }
    }

    // --- Providers ---

    @Provide
    Arbitrary<PasswordConfig> validPasswordConfigs() {
        Arbitrary<Integer> lengths = Arbitraries.integers().between(8, 128);
        // Generate at least one true among the four booleans
        Arbitrary<List<Boolean>> charTypeFlags = Arbitraries.of(
                // All 15 non-empty subsets of {upper, lower, digits, special}
                Arrays.asList(true, false, false, false),
                Arrays.asList(false, true, false, false),
                Arrays.asList(false, false, true, false),
                Arrays.asList(false, false, false, true),
                Arrays.asList(true, true, false, false),
                Arrays.asList(true, false, true, false),
                Arrays.asList(true, false, false, true),
                Arrays.asList(false, true, true, false),
                Arrays.asList(false, true, false, true),
                Arrays.asList(false, false, true, true),
                Arrays.asList(true, true, true, false),
                Arrays.asList(true, true, false, true),
                Arrays.asList(true, false, true, true),
                Arrays.asList(false, true, true, true),
                Arrays.asList(true, true, true, true)
        );

        return Combinators.combine(lengths, charTypeFlags).as((len, flags) -> {
            // Ensure length >= number of enabled types (each type needs at least 1 char)
            int enabledCount = (int) flags.stream().filter(b -> b).count();
            int adjustedLen = Math.max(len, enabledCount);
            // Cap at 128
            adjustedLen = Math.min(adjustedLen, 128);
            return new PasswordConfig(adjustedLen, flags.get(0), flags.get(1), flags.get(2), flags.get(3));
        });
    }

    // --- Helpers ---

    private String buildAllowedPool(PasswordConfig config) {
        StringBuilder pool = new StringBuilder();
        if (config.includeUppercase) pool.append(UPPERCASE_CHARS);
        if (config.includeLowercase) pool.append(LOWERCASE_CHARS);
        if (config.includeDigits) pool.append(DIGIT_CHARS);
        if (config.includeSpecial) pool.append(SPECIAL_CHARS);
        return pool.toString();
    }

    private boolean containsAnySpecial(String password) {
        for (char c : password.toCharArray()) {
            if (SPECIAL_CHARS.indexOf(c) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Simple config record for password generation parameters.
     */
    static class PasswordConfig {
        final int length;
        final boolean includeUppercase;
        final boolean includeLowercase;
        final boolean includeDigits;
        final boolean includeSpecial;

        PasswordConfig(int length, boolean includeUppercase, boolean includeLowercase,
                       boolean includeDigits, boolean includeSpecial) {
            this.length = length;
            this.includeUppercase = includeUppercase;
            this.includeLowercase = includeLowercase;
            this.includeDigits = includeDigits;
            this.includeSpecial = includeSpecial;
        }

        @Override
        public String toString() {
            return String.format("PasswordConfig{length=%d, upper=%s, lower=%s, digits=%s, special=%s}",
                    length, includeUppercase, includeLowercase, includeDigits, includeSpecial);
        }
    }
}
