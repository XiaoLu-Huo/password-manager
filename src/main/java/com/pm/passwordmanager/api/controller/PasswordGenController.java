package com.pm.passwordmanager.api.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pm.passwordmanager.api.assembler.PasswordGenDtoMapper;
import com.pm.passwordmanager.api.dto.request.GeneratePasswordRequest;
import com.pm.passwordmanager.api.dto.response.ApiResponse;
import com.pm.passwordmanager.api.dto.response.GeneratedPasswordResponse;
import com.pm.passwordmanager.api.dto.response.PasswordRuleResponse;
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
 * 通过 MapStruct 完成 Request → Command、Model → Response 的转换。
 */
@RestController
@RequestMapping("/api/password-generator")
@RequiredArgsConstructor
@Tag(name = "密码生成器", description = "密码生成与密码规则管理接口")
public class PasswordGenController {

    private final PasswordGeneratorService passwordGeneratorService;
    private final PasswordGenDtoMapper passwordGenDtoMapper;
    private final PasswordStrengthEvaluator passwordStrengthEvaluator;
    private final AuthService authService;

    @PostMapping("/generate")
    @Operation(summary = "生成密码")
    public ApiResponse<GeneratedPasswordResponse> generate(@Valid @RequestBody GeneratePasswordRequest request) {
        return ApiResponse.success(passwordGeneratorService.generatePassword(passwordGenDtoMapper.toCommand(request)));
    }

    @PostMapping("/evaluate")
    @Operation(summary = "评估密码强度")
    public ApiResponse<PasswordStrengthResponse> evaluate(@RequestBody String password) {
        return ApiResponse.success(PasswordStrengthResponse.builder()
                .strengthLevel(passwordStrengthEvaluator.evaluate(password)).build());
    }

    @GetMapping("/rules")
    @Operation(summary = "查询密码规则列表")
    public ApiResponse<List<PasswordRuleResponse>> listRules() {
        return ApiResponse.success(passwordGeneratorService.getRulesByUserId(authService.getCurrentUserId()).stream()
                .map(passwordGenDtoMapper::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/rules/{id}")
    @Operation(summary = "查询密码规则详情")
    public ApiResponse<PasswordRuleResponse> getRule(@PathVariable Long id) {
        return ApiResponse.success(passwordGenDtoMapper.toResponse(passwordGeneratorService.getRuleById(id)));
    }

    @PostMapping("/rules")
    @Operation(summary = "保存密码规则")
    public ApiResponse<PasswordRuleResponse> saveRule(@RequestBody PasswordRule rule) {
        return ApiResponse.success(passwordGenDtoMapper.toResponse(
                passwordGeneratorService.saveRule(authService.getCurrentUserId(), rule)));
    }

    @PutMapping("/rules/{id}")
    @Operation(summary = "更新密码规则")
    public ApiResponse<PasswordRuleResponse> updateRule(@PathVariable Long id, @RequestBody PasswordRule rule) {
        return ApiResponse.success(passwordGenDtoMapper.toResponse(
                passwordGeneratorService.updateRule(authService.getCurrentUserId(), id, rule)));
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "删除密码规则")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        passwordGeneratorService.deleteRule(id);
        return ApiResponse.success();
    }
}
