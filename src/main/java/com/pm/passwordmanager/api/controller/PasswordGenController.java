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
import com.pm.passwordmanager.domain.model.PasswordRule;
import com.pm.passwordmanager.domain.service.AuthService;
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
    private final AuthService authService;

    @PostMapping("/generate")
    @Operation(summary = "生成密码")
    public ApiResponse<GeneratedPasswordResponse> generate(@Valid @RequestBody GeneratePasswordRequest request) {
        return ApiResponse.success(passwordGeneratorService.generatePassword(request));
    }

    @PostMapping("/evaluate")
    @Operation(summary = "评估密码强度")
    public ApiResponse<PasswordStrengthResponse> evaluate(@RequestBody String password) {
        return ApiResponse.success(PasswordStrengthResponse.builder()
                .strengthLevel(passwordStrengthEvaluator.evaluate(password)).build());
    }

    @GetMapping("/rules")
    @Operation(summary = "查询密码规则列表")
    public ApiResponse<List<PasswordRule>> listRules() {
        return ApiResponse.success(passwordGeneratorService.getRulesByUserId(authService.getCurrentUserId()));
    }

    @GetMapping("/rules/{id}")
    @Operation(summary = "查询密码规则详情")
    public ApiResponse<PasswordRule> getRule(@PathVariable Long id) {
        return ApiResponse.success(passwordGeneratorService.getRuleById(id));
    }

    @PostMapping("/rules")
    @Operation(summary = "保存密码规则")
    public ApiResponse<PasswordRule> saveRule(@RequestBody PasswordRule rule) {
        return ApiResponse.success(passwordGeneratorService.saveRule(authService.getCurrentUserId(), rule));
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "删除密码规则")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        passwordGeneratorService.deleteRule(id);
        return ApiResponse.success();
    }
}
