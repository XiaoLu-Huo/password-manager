package com.pm.passwordmanager.api.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pm.passwordmanager.api.dto.request.CreateMasterPasswordRequest;
import com.pm.passwordmanager.api.dto.request.EnableMfaRequest;
import com.pm.passwordmanager.api.dto.request.UnlockVaultRequest;
import com.pm.passwordmanager.api.dto.request.VerifyTotpRequest;
import com.pm.passwordmanager.api.dto.response.ApiResponse;
import com.pm.passwordmanager.api.dto.response.MfaSetupResponse;
import com.pm.passwordmanager.api.dto.response.UnlockResultResponse;
import com.pm.passwordmanager.infrastructure.persistence.entity.UserEntity;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.persistence.mapper.UserMapper;
import com.pm.passwordmanager.domain.service.AuthService;
import com.pm.passwordmanager.domain.service.MfaService;
import com.pm.passwordmanager.domain.service.SessionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 认证控制器。
 * 提供主密码设置、密码库解锁/锁定、MFA 管理等 API 端点。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "主密码与 MFA 认证相关接口")
public class AuthController {

    private final AuthService authService;
    private final MfaService mfaService;
    private final SessionService sessionService;
    private final UserMapper userMapper;

    @PostMapping("/setup")
    @Operation(summary = "首次创建主密码", description = "引导用户创建主密码，验证复杂度后加密存储")
    public ApiResponse<Void> setup(@Valid @RequestBody CreateMasterPasswordRequest request) {
        authService.setup(request);
        return ApiResponse.success();
    }

    @PostMapping("/unlock")
    @Operation(summary = "解锁密码库", description = "验证主密码，解锁密码库。如启用 MFA 则返回需要 TOTP 验证")
    public ApiResponse<UnlockResultResponse> unlock(@Valid @RequestBody UnlockVaultRequest request) {
        UnlockResultResponse result = authService.unlock(request);
        return ApiResponse.success(result);
    }

    @PostMapping("/verify-totp")
    @Operation(summary = "验证 TOTP 码", description = "MFA 启用时的第二步验证，提交 TOTP 验证码完成解锁")
    public ApiResponse<UnlockResultResponse> verifyTotp(@Valid @RequestBody VerifyTotpRequest request) {
        UnlockResultResponse result = authService.verifyTotpAndUnlock(request.getTotpCode());
        return ApiResponse.success(result);
    }

    @PostMapping("/lock")
    @Operation(summary = "手动锁定密码库", description = "清除内存中的 DEK 和所有解密数据")
    public ApiResponse<Void> lock() {
        Long userId = getCurrentUserId();
        sessionService.clearSession(userId);
        return ApiResponse.success();
    }

    @PostMapping("/mfa/enable")
    @Operation(summary = "启用 MFA", description = "两步流程：不传 totpCode 初始化设置（返回二维码和恢复码），传入 totpCode 确认启用")
    public ApiResponse<MfaSetupResponse> enableMfa(@RequestBody(required = false) EnableMfaRequest request) {
        Long userId = getCurrentUserId();
        String totpCode = (request != null) ? request.getTotpCode() : null;

        if (totpCode == null || totpCode.isBlank()) {
            MfaSetupResponse setupResponse = mfaService.initSetup(userId);
            return ApiResponse.success(setupResponse);
        } else {
            mfaService.confirmEnable(userId, totpCode);
            return ApiResponse.success(null);
        }
    }

    @PostMapping("/mfa/disable")
    @Operation(summary = "禁用 MFA", description = "在通过完整认证后禁用多因素认证")
    public ApiResponse<Void> disableMfa() {
        Long userId = getCurrentUserId();
        mfaService.disable(userId);
        return ApiResponse.success();
    }

    /**
     * 获取当前用户 ID。
     * 单用户系统，查询数据库中唯一的用户记录。
     */
    private Long getCurrentUserId() {
        UserEntity user = userMapper.selectOne(null);
        if (user == null) {
            throw new BusinessException(ErrorCode.VAULT_LOCKED);
        }
        return user.getId();
    }
}
