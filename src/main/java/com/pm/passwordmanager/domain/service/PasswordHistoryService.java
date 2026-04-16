package com.pm.passwordmanager.domain.service;

import java.util.List;

import com.pm.passwordmanager.api.dto.response.PasswordHistoryResponse;

/**
 * 密码历史领域服务接口。
 * 提供密码变更历史的查询、明文查看和记录功能。
 */
public interface PasswordHistoryService {

    /**
     * 获取凭证的密码变更历史（最近 10 条，按时间倒序）。
     *
     * @param userId       用户 ID
     * @param credentialId 凭证 ID
     * @return 密码历史列表（掩码显示）
     */
    List<PasswordHistoryResponse> getHistory(Long userId, Long credentialId);

    /**
     * 获取某条历史密码的明文（解密后返回）。
     *
     * @param userId    用户 ID
     * @param credentialId 凭证 ID
     * @param historyId 历史记录 ID
     * @return 解密后的明文密码
     */
    String revealHistoryPassword(Long userId, Long credentialId, Long historyId);

    /**
     * 记录密码变更（在密码更新时由 CredentialService 调用）。
     * 超过 10 条时删除最早的记录。
     *
     * @param credentialId       凭证 ID
     * @param oldPasswordEncrypted 旧密码密文
     * @param iv                   旧密码 IV
     */
    void recordPasswordChange(Long credentialId, byte[] oldPasswordEncrypted, byte[] iv);
}
