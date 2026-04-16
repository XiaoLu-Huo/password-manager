package com.pm.passwordmanager.api.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pm.passwordmanager.api.assembler.CredentialDtoMapper;
import com.pm.passwordmanager.api.dto.response.ApiResponse;
import com.pm.passwordmanager.api.dto.response.CredentialListResponse;
import com.pm.passwordmanager.api.dto.response.SecurityReportResponse;
import com.pm.passwordmanager.api.enums.PasswordStrengthLevel;
import com.pm.passwordmanager.domain.service.AuthService;
import com.pm.passwordmanager.domain.service.SecurityReportService;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/security-report")
@RequiredArgsConstructor
@Tag(name = "安全报告", description = "密码安全性评估与报告接口")
public class SecurityReportController {

    private final SecurityReportService securityReportService;
    private final CredentialDtoMapper credentialDtoMapper;
    private final AuthService authService;

    @GetMapping
    @Operation(summary = "获取安全报告统计")
    public ApiResponse<SecurityReportResponse> getReport() {
        Long userId = authService.getCurrentUserId();
        return ApiResponse.success(securityReportService.getReport(userId));
    }

    @GetMapping("/strength/{level}")
    @Operation(summary = "按密码强度等级获取凭证列表", description = "level: WEAK / MEDIUM / STRONG")
    public ApiResponse<List<CredentialListResponse>> getCredentialsByStrength(
            @PathVariable String level) {
        PasswordStrengthLevel strengthLevel;
        try {
            strengthLevel = PasswordStrengthLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Long userId = authService.getCurrentUserId();
        return ApiResponse.success(securityReportService.getCredentialsByStrengthLevel(userId, strengthLevel)
                .stream().map(credentialDtoMapper::toListResponse).collect(Collectors.toList()));
    }

    @GetMapping("/duplicate")
    @Operation(summary = "获取重复密码凭证列表")
    public ApiResponse<List<CredentialListResponse>> getDuplicatePasswordCredentials() {
        Long userId = authService.getCurrentUserId();
        return ApiResponse.success(securityReportService.getDuplicatePasswordCredentials(userId)
                .stream().map(credentialDtoMapper::toListResponse).collect(Collectors.toList()));
    }

    @GetMapping("/expired")
    @Operation(summary = "获取超期未更新凭证列表")
    public ApiResponse<List<CredentialListResponse>> getExpiredPasswordCredentials() {
        Long userId = authService.getCurrentUserId();
        return ApiResponse.success(securityReportService.getExpiredPasswordCredentials(userId)
                .stream().map(credentialDtoMapper::toListResponse).collect(Collectors.toList()));
    }
}
