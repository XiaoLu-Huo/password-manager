package com.pm.passwordmanager.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pm.passwordmanager.api.assembler.AuthDtoMapper;
import com.pm.passwordmanager.api.dto.request.EnableMfaRequest;
import com.pm.passwordmanager.api.dto.request.VerifyTotpRequest;
import com.pm.passwordmanager.api.dto.response.ApiResponse;
import com.pm.passwordmanager.api.dto.response.MfaSetupResponse;
import com.pm.passwordmanager.api.dto.response.UnlockResultResponse;
import com.pm.passwordmanager.domain.service.AuthService;
import com.pm.passwordmanager.domain.service.MfaService;
import com.pm.passwordmanager.domain.service.SessionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 认证控制器。
 * 提供用户注册、登录、MFA 管理等 API 端点。
 * TODO: Task 6 将完成完整的 API 层重构（新增 RegisterRequest/LoginRequest DTO）。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户注册、登录与 MFA 认证相关接口")
public class AuthController {

    private final AuthService authService;
    private final AuthDtoMapper authDtoMapper;
    private final MfaService mfaService;
    private final SessionService sessionService;

    @GetMapping("/status")
    @Operation(summary = "检查系统是否可用")
    public ApiResponse<Boolean> status() {
        return ApiResponse.success(authService.isInitialized());
    }

    // TODO: Task 6.3 - POST /api/auth/register and POST /api/auth/login endpoints

    @PostMapping("/verify-totp")
    @Operation(summary = "验证 TOTP 码")
    public ApiResponse<UnlockResultResponse> verifyTotp(@Valid @RequestBody VerifyTotpRequest request) {
        return ApiResponse.success(authService.verifyTotpAndUnlock(request.getMfaToken(), request.getTotpCode()));
    }

    @PostMapping("/lock")
    @Operation(summary = "手动锁定密码库")
    public ApiResponse<Void> lock() {
        sessionService.clearSession(authService.getCurrentUserId());
        return ApiResponse.success();
    }

    @PostMapping("/mfa/enable")
    @Operation(summary = "启用 MFA")
    public ApiResponse<MfaSetupResponse> enableMfa(@RequestBody(required = false) EnableMfaRequest request) {
        Long userId = authService.getCurrentUserId();
        String totpCode = (request != null) ? request.getTotpCode() : null;

        if (totpCode == null || totpCode.isBlank()) {
            return ApiResponse.success(mfaService.initSetup(userId));
        } else {
            mfaService.confirmEnable(userId, totpCode);
            return ApiResponse.success(null);
        }
    }

    @PostMapping("/mfa/disable")
    @Operation(summary = "禁用 MFA")
    public ApiResponse<Void> disableMfa() {
        mfaService.disable(authService.getCurrentUserId());
        return ApiResponse.success();
    }
}
