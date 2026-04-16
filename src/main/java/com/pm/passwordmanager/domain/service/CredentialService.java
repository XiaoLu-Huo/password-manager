package com.pm.passwordmanager.domain.service;

import java.util.List;

import com.pm.passwordmanager.api.dto.request.CreateCredentialRequest;
import com.pm.passwordmanager.api.dto.request.UpdateCredentialRequest;
import com.pm.passwordmanager.api.dto.response.CredentialListResponse;
import com.pm.passwordmanager.api.dto.response.CredentialResponse;

/**
 * 凭证服务接口。
 * 负责凭证的 CRUD、搜索、标签筛选、密码解密查看等操作。
 */
public interface CredentialService {

    /**
     * 创建凭证。验证必填字段，加密密码后存储。
     * 支持自动生成密码（autoGenerate=true 时调用密码生成器）。
     */
    CredentialResponse createCredential(Long userId, CreateCredentialRequest request);

    /**
     * 获取用户的所有凭证摘要列表。
     */
    List<CredentialListResponse> listCredentials(Long userId);

    /**
     * 按关键词搜索凭证（匹配账户名称、用户名、URL）。
     */
    List<CredentialListResponse> searchCredentials(Long userId, String keyword);

    /**
     * 按标签筛选凭证。
     */
    List<CredentialListResponse> filterByTag(Long userId, String tag);

    /**
     * 获取凭证详情（密码以掩码显示）。
     */
    CredentialResponse getCredential(Long userId, Long credentialId);

    /**
     * 获取凭证的解密密码明文。
     */
    String revealPassword(Long userId, Long credentialId);

    /**
     * 更新凭证。密码变更时记录历史并验证新旧密码不同。
     */
    CredentialResponse updateCredential(Long userId, Long credentialId, UpdateCredentialRequest request);

    /**
     * 删除凭证。
     */
    void deleteCredential(Long userId, Long credentialId);
}
