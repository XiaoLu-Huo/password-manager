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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pm.passwordmanager.api.assembler.CredentialDtoMapper;
import com.pm.passwordmanager.api.dto.request.CreateCredentialRequest;
import com.pm.passwordmanager.api.dto.request.UpdateCredentialRequest;
import com.pm.passwordmanager.api.dto.response.ApiResponse;
import com.pm.passwordmanager.api.dto.response.CredentialListResponse;
import com.pm.passwordmanager.api.dto.response.CredentialResponse;
import com.pm.passwordmanager.domain.model.Credential;
import com.pm.passwordmanager.domain.service.AuthService;
import com.pm.passwordmanager.domain.service.CredentialService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 凭证管理控制器。
 * 通过 MapStruct 完成 Request → Command、Model → Response 的转换。
 */
@RestController
@RequestMapping("/api/credentials")
@RequiredArgsConstructor
@Tag(name = "凭证管理", description = "凭证 CRUD、搜索与标签筛选接口")
public class CredentialController {

    private final CredentialService credentialService;
    private final CredentialDtoMapper credentialDtoMapper;
    private final AuthService authService;

    @PostMapping
    @Operation(summary = "创建凭证")
    public ApiResponse<CredentialResponse> create(@Valid @RequestBody CreateCredentialRequest request) {
        Long userId = authService.getCurrentUserId();
        Credential credential = credentialService.createCredential(userId, credentialDtoMapper.toCommand(request));
        return ApiResponse.success(credentialDtoMapper.toResponse(credential));
    }

    @GetMapping
    @Operation(summary = "获取凭证列表")
    public ApiResponse<List<CredentialListResponse>> list(
            @Parameter(description = "按标签筛选") @RequestParam(required = false) String tag) {
        Long userId = authService.getCurrentUserId();
        List<Credential> credentials = (tag != null && !tag.isBlank())
                ? credentialService.filterByTag(userId, tag)
                : credentialService.listCredentials(userId);
        return ApiResponse.success(credentials.stream()
                .map(credentialDtoMapper::toListResponse).collect(Collectors.toList()));
    }

    @GetMapping("/search")
    @Operation(summary = "搜索凭证")
    public ApiResponse<List<CredentialListResponse>> search(
            @Parameter(description = "搜索关键词", required = true) @RequestParam String keyword) {
        Long userId = authService.getCurrentUserId();
        return ApiResponse.success(credentialService.searchCredentials(userId, keyword).stream()
                .map(credentialDtoMapper::toListResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取凭证详情")
    public ApiResponse<CredentialResponse> get(@PathVariable Long id) {
        return ApiResponse.success(credentialDtoMapper.toResponse(
                credentialService.getCredential(authService.getCurrentUserId(), id)));
    }

    @PostMapping("/{id}/reveal-password")
    @Operation(summary = "查看密码明文")
    public ApiResponse<String> revealPassword(@PathVariable Long id) {
        return ApiResponse.success(credentialService.revealPassword(authService.getCurrentUserId(), id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新凭证")
    public ApiResponse<CredentialResponse> update(
            @PathVariable Long id, @Valid @RequestBody UpdateCredentialRequest request) {
        Long userId = authService.getCurrentUserId();
        Credential credential = credentialService.updateCredential(userId, id, credentialDtoMapper.toCommand(request));
        return ApiResponse.success(credentialDtoMapper.toResponse(credential));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除凭证")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        credentialService.deleteCredential(authService.getCurrentUserId(), id);
        return ApiResponse.success();
    }
}
