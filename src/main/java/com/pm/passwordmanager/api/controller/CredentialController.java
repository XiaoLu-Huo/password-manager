package com.pm.passwordmanager.api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pm.passwordmanager.api.dto.request.CreateCredentialRequest;
import com.pm.passwordmanager.api.dto.request.UpdateCredentialRequest;
import com.pm.passwordmanager.api.dto.response.ApiResponse;
import com.pm.passwordmanager.api.dto.response.CredentialListResponse;
import com.pm.passwordmanager.api.dto.response.CredentialResponse;
import com.pm.passwordmanager.infrastructure.persistence.entity.UserEntity;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.persistence.mapper.UserMapper;
import com.pm.passwordmanager.domain.service.CredentialService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 凭证管理控制器。
 * 提供凭证的创建、查询、搜索、更新、删除以及密码明文查看等 API 端点。
 */
@RestController
@RequestMapping("/api/credentials")
@RequiredArgsConstructor
@Tag(name = "凭证管理", description = "凭证 CRUD、搜索与标签筛选接口")
public class CredentialController {

    private final CredentialService credentialService;
    private final UserMapper userMapper;

    @PostMapping
    @Operation(summary = "创建凭证", description = "创建新凭证，支持自动生成密码")
    public ApiResponse<CredentialResponse> create(@Valid @RequestBody CreateCredentialRequest request) {
        Long userId = getCurrentUserId();
        CredentialResponse response = credentialService.createCredential(userId, request);
        return ApiResponse.success(response);
    }

    @GetMapping
    @Operation(summary = "获取凭证列表", description = "获取当前用户的所有凭证摘要信息")
    public ApiResponse<List<CredentialListResponse>> list(
            @Parameter(description = "按标签筛选") @RequestParam(required = false) String tag) {
        Long userId = getCurrentUserId();
        List<CredentialListResponse> list;
        if (tag != null && !tag.isBlank()) {
            list = credentialService.filterByTag(userId, tag);
        } else {
            list = credentialService.listCredentials(userId);
        }
        return ApiResponse.success(list);
    }

    @GetMapping("/search")
    @Operation(summary = "搜索凭证", description = "按关键词搜索凭证（匹配账户名称、用户名或 URL）")
    public ApiResponse<List<CredentialListResponse>> search(
            @Parameter(description = "搜索关键词", required = true) @RequestParam String keyword) {
        Long userId = getCurrentUserId();
        List<CredentialListResponse> results = credentialService.searchCredentials(userId, keyword);
        return ApiResponse.success(results);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取凭证详情", description = "获取凭证详情，密码以掩码显示")
    public ApiResponse<CredentialResponse> get(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        CredentialResponse response = credentialService.getCredential(userId, id);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/reveal-password")
    @Operation(summary = "查看密码明文", description = "解密并返回凭证密码明文")
    public ApiResponse<String> revealPassword(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        String password = credentialService.revealPassword(userId, id);
        return ApiResponse.success(password);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新凭证", description = "更新凭证信息，密码变更时记录历史")
    public ApiResponse<CredentialResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCredentialRequest request) {
        Long userId = getCurrentUserId();
        CredentialResponse response = credentialService.updateCredential(userId, id, request);
        return ApiResponse.success(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除凭证", description = "删除指定凭证")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        credentialService.deleteCredential(userId, id);
        return ApiResponse.success();
    }

    /** 获取当前用户 ID（单用户系统）。 */
    private Long getCurrentUserId() {
        UserEntity user = userMapper.selectOne(null);
        if (user == null) {
            throw new BusinessException(ErrorCode.VAULT_LOCKED);
        }
        return user.getId();
    }
}
