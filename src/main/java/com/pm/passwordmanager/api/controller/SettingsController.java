package com.pm.passwordmanager.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pm.passwordmanager.api.dto.request.UpdateSettingsRequest;
import com.pm.passwordmanager.api.dto.response.ApiResponse;
import com.pm.passwordmanager.api.dto.response.SettingsResponse;
import com.pm.passwordmanager.domain.model.User;
import com.pm.passwordmanager.domain.repository.UserRepository;
import com.pm.passwordmanager.domain.service.AuthService;
import com.pm.passwordmanager.domain.service.MfaService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 设置控制器。
 * 提供用户设置的查询和更新接口，当前支持自动锁定超时时间配置。
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "设置管理", description = "用户设置相关接口")
public class SettingsController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final MfaService mfaService;

    @GetMapping
    @Operation(summary = "获取用户设置", description = "返回当前用户的设置信息，包括自动锁定超时时间和 MFA 状态")
    public ApiResponse<SettingsResponse> getSettings() {
        Long userId = authService.getCurrentUserId();
        User user = userRepository.findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));

        Integer autoLock = user.getAutoLockMinutes();
        SettingsResponse response = SettingsResponse.builder()
                .autoLockMinutes(autoLock != null ? autoLock : 5)
                .mfaEnabled(mfaService.isMfaEnabled(userId))
                .build();
        return ApiResponse.success(response);
    }

    @PutMapping
    @Operation(summary = "更新用户设置", description = "更新自动锁定超时时间（1-60 分钟）")
    public ApiResponse<Void> updateSettings(@Valid @RequestBody UpdateSettingsRequest request) {
        Long userId = authService.getCurrentUserId();

        int minutes = request.getAutoLockMinutes();
        if (minutes < 1 || minutes > 60) {
            throw new BusinessException(ErrorCode.AUTO_LOCK_TIMEOUT_OUT_OF_RANGE);
        }

        // Update in-memory session timeout
        sessionService.setAutoLockTimeout(userId, minutes);

        // Persist to database
        User user = userRepository.findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        user.setAutoLockMinutes(minutes);
        userRepository.updateById(user);

        return ApiResponse.success();
    }
}
