package com.pm.passwordmanager.api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pm.passwordmanager.api.dto.response.ApiResponse;
import com.pm.passwordmanager.api.dto.response.PasswordHistoryResponse;
import com.pm.passwordmanager.domain.service.AuthService;
import com.pm.passwordmanager.domain.service.PasswordHistoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 密码历史查询控制器。
 * 提供凭证密码变更历史的查询和明文查看接口。
 */
@RestController
@RequestMapping("/api/credentials/{credentialId}/password-history")
@RequiredArgsConstructor
@Tag(name = "密码历史", description = "密码变更历史查询与明文查看接口")
public class PasswordHistoryController {

    private final PasswordHistoryService passwordHistoryService;
    private final AuthService authService;

    @GetMapping
    @Operation(summary = "获取凭证的密码变更历史", description = "返回最近 10 条历史记录，按变更时间倒序排列，密码以掩码显示")
    public ApiResponse<List<PasswordHistoryResponse>> getHistory(@PathVariable Long credentialId) {
        Long userId = authService.getCurrentUserId();
        return ApiResponse.success(passwordHistoryService.getHistory(userId, credentialId));
    }

    @PostMapping("/{historyId}/reveal")
    @Operation(summary = "查看历史密码明文", description = "解密并返回指定历史密码的明文内容")
    public ApiResponse<String> revealPassword(
            @PathVariable Long credentialId,
            @PathVariable Long historyId) {
        Long userId = authService.getCurrentUserId();
        return ApiResponse.success(passwordHistoryService.revealHistoryPassword(userId, credentialId, historyId));
    }
}
