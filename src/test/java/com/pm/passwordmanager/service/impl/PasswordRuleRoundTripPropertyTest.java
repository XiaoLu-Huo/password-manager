package com.pm.passwordmanager.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.pm.passwordmanager.domain.model.PasswordRule;
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
import net.jqwik.api.lifecycle.BeforeProperty;

/**
 * Property 6: 密码规则保存往返
 * For any 有效的自定义密码规则，保存后再查询应返回与原始规则等价的配置数据。
 *
 * **Validates: Requirements 2.6**
 */
@Label("Feature: password-manager, Property 6: 密码规则保存往返")
class PasswordRuleRoundTripPropertyTest {

    private PasswordRuleRepository passwordRuleRepository;
    private PasswordGeneratorServiceImpl service;
    private final List<PasswordRule> storedRules = new ArrayList<>();
    private long idCounter;

    @BeforeProperty
    void setUp() {
        passwordRuleRepository = mock(PasswordRuleRepository.class);
        SecureRandomUtil secureRandomUtil = new SecureRandomUtil();
        PasswordStrengthEvaluator evaluator = new PasswordStrengthEvaluator();
        service = new PasswordGeneratorServiceImpl(secureRandomUtil, evaluator, passwordRuleRepository);
        storedRules.clear();
        idCounter = 1;

        // Simulate save: capture the model, assign an ID, and store it
        when(passwordRuleRepository.save(any(PasswordRule.class))).thenAnswer(invocation -> {
            PasswordRule rule = invocation.getArgument(0);
            rule.setId(idCounter++);
            storedRules.add(rule);
            return rule;
        });

        // Simulate findById: return the stored model matching the ID
        when(passwordRuleRepository.findById(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return storedRules.stream()
                    .filter(r -> r.getId().equals(id))
                    .findFirst();
        });
    }

    /**
     * 保存自定义规则后，通过 ID 查询应返回等价配置。
     *
     * **Validates: Requirements 2.6**
     */
    @Property(tries = 100)
    @Label("should_returnEquivalentRule_when_savedAndQueriedById")
    void should_returnEquivalentRule_when_savedAndQueriedById(
            @ForAll("validPasswordRules") PasswordRuleInput input
    ) {
        // Build the rule domain model from generated input
        PasswordRule rule = PasswordRule.builder()
                .ruleName(input.ruleName)
                .length(input.length)
                .includeUppercase(input.includeUppercase)
                .includeLowercase(input.includeLowercase)
                .includeDigits(input.includeDigits)
                .includeSpecial(input.includeSpecial)
                .isDefault(input.isDefault)
                .build();

        // Save the rule
        Long userId = input.userId;
        PasswordRule saved = service.saveRule(userId, rule);

        // Query back by ID
        PasswordRule queried = service.getRuleById(saved.getId());

        // Verify round-trip equivalence
        assertThat(queried).isNotNull();
        assertThat(queried.getId()).isEqualTo(saved.getId());
        assertThat(queried.getUserId()).isEqualTo(userId);
        assertThat(queried.getRuleName()).isEqualTo(input.ruleName);
        assertThat(queried.getLength()).isEqualTo(input.length);
        assertThat(queried.getIncludeUppercase()).isEqualTo(input.includeUppercase);
        assertThat(queried.getIncludeLowercase()).isEqualTo(input.includeLowercase);
        assertThat(queried.getIncludeDigits()).isEqualTo(input.includeDigits);
        assertThat(queried.getIncludeSpecial()).isEqualTo(input.includeSpecial);
        assertThat(queried.getIsDefault()).isEqualTo(input.isDefault);
    }

    // --- Providers ---

    @Provide
    Arbitrary<PasswordRuleInput> validPasswordRules() {
        Arbitrary<Long> userIds = Arbitraries.longs().between(1, 10000);
        Arbitrary<String> ruleNames = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(50)
                .map(s -> "Rule_" + s);
        Arbitrary<Integer> lengths = Arbitraries.integers().between(8, 128);
        Arbitrary<Boolean> booleans = Arbitraries.of(true, false);

        return Combinators.combine(userIds, ruleNames, lengths, booleans, booleans, booleans, booleans, booleans)
                .as((userId, name, length, upper, lower, digits, special, isDefault) -> {
                    boolean hasAny = upper || lower || digits || special;
                    if (!hasAny) {
                        upper = true;
                    }
                    return new PasswordRuleInput(userId, name, length, upper, lower, digits, special, isDefault);
                });
    }

    // --- Helper class ---

    static class PasswordRuleInput {
        final Long userId;
        final String ruleName;
        final int length;
        final boolean includeUppercase;
        final boolean includeLowercase;
        final boolean includeDigits;
        final boolean includeSpecial;
        final boolean isDefault;

        PasswordRuleInput(Long userId, String ruleName, int length,
                          boolean includeUppercase, boolean includeLowercase,
                          boolean includeDigits, boolean includeSpecial, boolean isDefault) {
            this.userId = userId;
            this.ruleName = ruleName;
            this.length = length;
            this.includeUppercase = includeUppercase;
            this.includeLowercase = includeLowercase;
            this.includeDigits = includeDigits;
            this.includeSpecial = includeSpecial;
            this.isDefault = isDefault;
        }

        @Override
        public String toString() {
            return String.format(
                    "PasswordRuleInput{userId=%d, name='%s', length=%d, upper=%s, lower=%s, digits=%s, special=%s, default=%s}",
                    userId, ruleName, length, includeUppercase, includeLowercase, includeDigits, includeSpecial, isDefault);
        }
    }
}
