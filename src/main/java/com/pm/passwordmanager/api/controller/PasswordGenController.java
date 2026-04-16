package com.pm.passwordmanager.api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pm.passwordmanager.api.dto.request.GeneratePasswordRequest;
import com.pm.passwordmanager.api.dto.response.ApiResponse;
import com.pm.passwordmanager.api.dto.response.GeneratedPasswordResponse;
import com.pm.passwordmanager.api.dto.response.PasswordStrengthResponse;
import com.pm.passwordmanager.infrastructure.persistence.entity.PasswordRuleEntity;
import com.pm.passwordmanager.infrastructure.persistence.entity.UserEntity;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.persistence.mapper.PasswordRuleMapper;
import com.pm.passwordmanager.infrastructure.persistence.mapper.UserMapper;
import com.pm.passwordmanager.domain.service.PasswordGeneratorService;
import com.pm.passwordmanager.infrastructure.encryption.PasswordStrengthEvaluator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 密码生成器控制器。
 * 提供密码生成、密码强度评估、密码规则 CRUD 等 API 端点。
 */
@RestController
@RequestMapping("/api/password-generator")
@RequiredArgsConstructor
@Tag(name = "密码生成器", description = "密码生成与密码规则管理接口")
public class PasswordGenController {

    private final PasswordGeneratorService passwordGeneratorService;
    private final PasswordStrengthEvaluator passwordStrengthEvaluator;
    private final PasswordRuleMapper passwordRuleMapper;
    private final UserMapper userMapper;

    @PostMapping("/generate")
    @Operation(summary = "生成密码", description = "根据默认规则或自定义规则生成随机密码")
    public ApiResponse<GeneratedPasswordResponse> generate(@Valid @RequestBody GeneratePasswordRequest request) {
        GeneratedPasswordResponse response = passwordGeneratorService.generatePassword(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/evaluate")
    @Operation(summary = "评估密码强度", description = "评估给定密码的强度等级")
    public ApiResponse<PasswordStrengthResponse> evaluate(@RequestBody String password) {
        PasswordStrengthResponse response = PasswordStrengthResponse.builder()
                .strengthLevel(passwordStrengthEvaluator.evaluate(password))
                .build();
        return ApiResponse.success(response);
    }

    @GetMapping("/rules")
    @Operation(summary = "查询密码规则列表", description = "获取当前用户的所有密码规则")
    public ApiResponse<List<PasswordRuleEntity>> listRules() {
        Long userId = getCurrentUserId();
        List<PasswordRuleEntity> rules = passwordGeneratorService.getRulesByUserId(userId);
        return ApiResponse.success(rules);
    }

    @GetMapping("/rules/{id}")
    @Operation(summary = "查询密码规则详情", description = "根据 ID 获取密码规则")
    public ApiResponse<PasswordRuleEntity> getRule(@PathVariable Long id) {
        PasswordRuleEntity rule = passwordGeneratorService.getRuleById(id);
        return ApiResponse.success(rule);
    }

    @PostMapping("/rules")
    @Operation(summary = "保存密码规则", description = "保存自定义密码规则以便后续复用")
    public ApiResponse<PasswordRuleEntity> saveRule(@RequestBody PasswordRuleEntity rule) {
        Long userId = getCurrentUserId();
        PasswordRuleEntity saved = passwordGeneratorService.saveRule(userId, rule);
        return ApiResponse.success(saved);
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "删除密码规则", description = "根据 ID 删除密码规则")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        passwordRuleMapper.deleteById(id);
        return ApiResponse.success();
    }

    private Long getCurrentUserId() {
        UserEntity user = userMapper.selectOne(null);
        if (user == null) {
            throw new BusinessException(ErrorCode.VAULT_LOCKED);
        }
        return user.getId();
    }
}
