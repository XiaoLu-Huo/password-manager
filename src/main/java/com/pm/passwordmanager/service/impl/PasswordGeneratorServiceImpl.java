package com.pm.passwordmanager.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pm.passwordmanager.dto.request.GeneratePasswordRequest;
import com.pm.passwordmanager.dto.response.GeneratedPasswordResponse;
import com.pm.passwordmanager.entity.PasswordRuleEntity;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.mapper.PasswordRuleMapper;
import com.pm.passwordmanager.service.PasswordGeneratorService;
import com.pm.passwordmanager.util.PasswordStrengthEvaluator;
import com.pm.passwordmanager.util.SecureRandomUtil;

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
    private final PasswordRuleMapper passwordRuleMapper;

    @Override
    public GeneratedPasswordResponse generatePassword(GeneratePasswordRequest request) {
        int length;
        boolean includeUppercase;
        boolean includeLowercase;
        boolean includeDigits;
        boolean includeSpecial;

        if (Boolean.TRUE.equals(request.getUseDefault())) {
            // 默认规则：16位，含所有字符类型
            length = DEFAULT_LENGTH;
            includeUppercase = true;
            includeLowercase = true;
            includeDigits = true;
            includeSpecial = true;
        } else {
            // 自定义规则
            length = request.getLength() != null ? request.getLength() : DEFAULT_LENGTH;
            includeUppercase = Boolean.TRUE.equals(request.getIncludeUppercase());
            includeLowercase = Boolean.TRUE.equals(request.getIncludeLowercase());
            includeDigits = Boolean.TRUE.equals(request.getIncludeDigits());
            includeSpecial = Boolean.TRUE.equals(request.getIncludeSpecial());
        }

        // 输入验证
        validateGenerationParams(length, includeUppercase, includeLowercase, includeDigits, includeSpecial);

        // 生成密码
        String password = doGenerate(length, includeUppercase, includeLowercase, includeDigits, includeSpecial);

        return GeneratedPasswordResponse.builder()
                .password(password)
                .strengthLevel(passwordStrengthEvaluator.evaluate(password))
                .build();
    }

    @Override
    public PasswordRuleEntity saveRule(Long userId, PasswordRuleEntity rule) {
        rule.setUserId(userId);
        passwordRuleMapper.insert(rule);
        return rule;
    }

    @Override
    public List<PasswordRuleEntity> getRulesByUserId(Long userId) {
        LambdaQueryWrapper<PasswordRuleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PasswordRuleEntity::getUserId, userId)
               .orderByDesc(PasswordRuleEntity::getCreatedAt);
        return passwordRuleMapper.selectList(wrapper);
    }

    @Override
    public PasswordRuleEntity getRuleById(Long ruleId) {
        return passwordRuleMapper.selectById(ruleId);
    }

    /**
     * 验证密码生成参数。
     */
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

    /**
     * 使用 CSPRNG 生成密码，确保每种启用的字符类型至少包含一个字符。
     */
    private String doGenerate(int length, boolean upper, boolean lower, boolean digits, boolean special) {
        // 构建字符池
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

        // 先从每种启用类型中各取一个字符，保证每种类型至少出现一次
        List<Character> passwordChars = new ArrayList<>(length);
        for (String enabledPool : enabledPools) {
            passwordChars.add(enabledPool.charAt(secureRandomUtil.generateRandomInt(enabledPool.length())));
        }

        // 剩余位置从完整字符池中随机选取
        for (int i = passwordChars.size(); i < length; i++) {
            passwordChars.add(pool.charAt(secureRandomUtil.generateRandomInt(pool.length())));
        }

        // 使用 Fisher-Yates 洗牌打乱顺序，避免前几位总是固定类型
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
