package com.pm.passwordmanager.api.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pm.passwordmanager.api.assembler.CredentialDtoMapper;
import com.pm.passwordmanager.api.dto.response.ApiResponse;
import com.pm.passwordmanager.api.dto.response.CredentialListResponse;
import com.pm.passwordmanager.api.dto.response.SecurityReportResponse;
import com.pm.passwordmanager.domain.service.AuthService;
import com.pm.passwordmanager.domain.service.SecurityReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 安全报告控制器。
 * 提供密码安全性评估报告及弱密码、重复密码、超期未更新凭证的查询接口。
 */
@RestController
@RequestMapping("/api/security-report")
@RequiredArgsConstructor
@Tag(name = "安全报告", description = "密码安全性评估与报告接口")
public class SecurityReportController {

    private final SecurityReportService securityReportService;
    private final CredentialDtoMapper credentialDtoMapper;
    private final AuthService authService;

    @GetMapping
    @Operation(summary = "获取安全报告", description = "返回总凭证数、弱密码数、重复密码数、超期未更新密码数的统计信息")
    public ApiResponse<SecurityReportResponse> getReport() {
        Long userId = authService.getCurrentUserId();
        return ApiResponse.success(securityReportService.getReport(userId));
    }

    @GetMapping("/weak")
    @Operation(summary = "获取弱密码凭证列表", description = "返回所有密码强度为弱的凭证")
    public ApiResponse<List<CredentialListResponse>> getWeakPasswordCredentials() {
        Long userId = authService.getCurrentUserId();
        return ApiResponse.success(securityReportService.getWeakPasswordCredentials(userId).stream()
                .map(credentialDtoMapper::toListResponse).collect(Collectors.toList()));
    }

    @GetMapping("/duplicate")
    @Operation(summary = "获取重复密码凭证列表", description = "返回所有使用重复密码的凭证")
    public ApiResponse<List<CredentialListResponse>> getDuplicatePasswordCredentials() {
        Long userId = authService.getCurrentUserId();
        return ApiResponse.success(securityReportService.getDuplicatePasswordCredentials(userId).stream()
                .map(credentialDtoMapper::toListResponse).collect(Collectors.toList()));
    }

    @GetMapping("/expired")
    @Operation(summary = "获取超期未更新凭证列表", description = "返回所有密码超过 90 天未更新的凭证")
    public ApiResponse<List<CredentialListResponse>> getExpiredPasswordCredentials() {
        Long userId = authService.getCurrentUserId();
        return ApiResponse.success(securityReportService.getExpiredPasswordCredentials(userId).stream()
                .map(credentialDtoMapper::toListResponse).collect(Collectors.toList()));
    }
}
