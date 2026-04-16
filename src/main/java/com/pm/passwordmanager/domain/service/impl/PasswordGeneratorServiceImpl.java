package com.pm.passwordmanager.domain.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.pm.passwordmanager.api.dto.response.GeneratedPasswordResponse;
import com.pm.passwordmanager.domain.command.GeneratePasswordCommand;
import com.pm.passwordmanager.domain.model.PasswordRule;
import com.pm.passwordmanager.domain.repository.PasswordRuleRepository;
import com.pm.passwordmanager.domain.service.PasswordGeneratorService;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.encryption.PasswordStrengthEvaluator;
import com.pm.passwordmanager.infrastructure.encryption.SecureRandomUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordGeneratorServiceImpl implements PasswordGeneratorService {

    private static final int DEFAULT_LENGTH = 16;
    private static final String UPPERCASE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE_CHARS = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGIT_CHARS = "0123456789";
    private static final String SPECIAL_CHARS = "!@#$%^&*()-_=+[]{}|;:',.<>?/`~";

    private final SecureRandomUtil secureRandomUtil;
    private final PasswordStrengthEvaluator passwordStrengthEvaluator;
    private final PasswordRuleRepository passwordRuleRepository;

    @Override
    public GeneratedPasswordResponse generatePassword(GeneratePasswordCommand command) {
        int length;
        boolean includeUppercase;
        boolean includeLowercase;
        boolean includeDigits;
        boolean includeSpecial;

        if (Boolean.TRUE.equals(command.getUseDefault())) {
            length = DEFAULT_LENGTH;
            includeUppercase = true;
            includeLowercase = true;
            includeDigits = true;
            includeSpecial = true;
        } else {
            length = command.getLength() != null ? command.getLength() : DEFAULT_LENGTH;
            includeUppercase = Boolean.TRUE.equals(command.getIncludeUppercase());
            includeLowercase = Boolean.TRUE.equals(command.getIncludeLowercase());
            includeDigits = Boolean.TRUE.equals(command.getIncludeDigits());
            includeSpecial = Boolean.TRUE.equals(command.getIncludeSpecial());
        }

        validateGenerationParams(length, includeUppercase, includeLowercase, includeDigits, includeSpecial);

        String password = doGenerate(length, includeUppercase, includeLowercase, includeDigits, includeSpecial);

        return GeneratedPasswordResponse.builder()
                .password(password)
                .strengthLevel(passwordStrengthEvaluator.evaluate(password))
                .build();
    }

    @Override
    public PasswordRule saveRule(Long userId, PasswordRule rule) {
        rule.setUserId(userId);
        // Check for duplicate rule name
        passwordRuleRepository.findByUserIdAndRuleName(userId, rule.getRuleName())
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.RULE_NAME_DUPLICATE);
                });
        return passwordRuleRepository.save(rule);
    }

    @Override
    public List<PasswordRule> getRulesByUserId(Long userId) {
        return passwordRuleRepository.findByUserId(userId);
    }

    @Override
    public PasswordRule getRuleById(Long ruleId) {
        return passwordRuleRepository.findById(ruleId).orElse(null);
    }

    @Override
    public PasswordRule updateRule(Long userId, Long ruleId, PasswordRule rule) {
        PasswordRule existing = passwordRuleRepository.findById(ruleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RULE_NOT_FOUND));
        // Check for duplicate rule name (exclude self)
        passwordRuleRepository.findByUserIdAndRuleName(userId, rule.getRuleName())
                .ifPresent(dup -> {
                    if (!dup.getId().equals(ruleId)) {
                        throw new BusinessException(ErrorCode.RULE_NAME_DUPLICATE);
                    }
                });
        existing.setRuleName(rule.getRuleName());
        existing.setLength(rule.getLength());
        existing.setIncludeUppercase(rule.getIncludeUppercase());
        existing.setIncludeLowercase(rule.getIncludeLowercase());
        existing.setIncludeDigits(rule.getIncludeDigits());
        existing.setIncludeSpecial(rule.getIncludeSpecial());
        return passwordRuleRepository.updateById(existing);
    }

    @Override
    public void deleteRule(Long ruleId) {
        passwordRuleRepository.deleteById(ruleId);
    }

    private void validateGenerationParams(int length, boolean upper, boolean lower, boolean digits, boolean special) {
        if (length < 8) {
            throw new BusinessException(ErrorCode.PASSWORD_LENGTH_TOO_SHORT);
        }
        if (length > 128) {
            throw new BusinessException(ErrorCode.PASSWORD_LENGTH_TOO_LONG);
        }
        if (!upper && !lower && !digits && !special) {
            throw new BusinessException(ErrorCode.NO_CHAR_TYPE_SELECTED);
        }
    }

    private String doGenerate(int length, boolean upper, boolean lower, boolean digits, boolean special) {
        StringBuilder poolBuilder = new StringBuilder();
        List<String> enabledPools = new ArrayList<>();

        if (upper) {
            poolBuilder.append(UPPERCASE_CHARS);
            enabledPools.add(UPPERCASE_CHARS);
        }
        if (lower) {
            poolBuilder.append(LOWERCASE_CHARS);
            enabledPools.add(LOWERCASE_CHARS);
        }
        if (digits) {
            poolBuilder.append(DIGIT_CHARS);
            enabledPools.add(DIGIT_CHARS);
        }
        if (special) {
            poolBuilder.append(SPECIAL_CHARS);
            enabledPools.add(SPECIAL_CHARS);
        }

        String pool = poolBuilder.toString();

        List<Character> passwordChars = new ArrayList<>(length);
        for (String enabledPool : enabledPools) {
            passwordChars.add(enabledPool.charAt(secureRandomUtil.generateRandomInt(enabledPool.length())));
        }

        for (int i = passwordChars.size(); i < length; i++) {
            passwordChars.add(pool.charAt(secureRandomUtil.generateRandomInt(pool.length())));
        }

        for (int i = passwordChars.size() - 1; i > 0; i--) {
            int j = secureRandomUtil.generateRandomInt(i + 1);
            Collections.swap(passwordChars, i, j);
        }

        StringBuilder result = new StringBuilder(length);
        for (char c : passwordChars) {
            result.append(c);
        }
        return result.toString();
    }
}
