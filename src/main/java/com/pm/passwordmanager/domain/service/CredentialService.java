package com.pm.passwordmanager.domain.service;

import java.util.List;

import com.pm.passwordmanager.domain.command.CreateCredentialCommand;
import com.pm.passwordmanager.domain.command.UpdateCredentialCommand;
import com.pm.passwordmanager.domain.model.Credential;

/**
 * 凭证领域服务接口。
 * 面向领域模型和命令对象，不依赖 API 层 DTO。
 */
public interface CredentialService {

    /** 创建凭证。 */
    Credential createCredential(Long userId, CreateCredentialCommand command);

    /** 获取用户的所有凭证。 */
    List<Credential> listCredentials(Long userId);

    /** 按关键词搜索凭证。 */
    List<Credential> searchCredentials(Long userId, String keyword);

    /** 按标签筛选凭证。 */
    List<Credential> filterByTag(Long userId, String tag);

    /** 获取凭证详情。 */
    Credential getCredential(Long userId, Long credentialId);

    /** 获取凭证的解密密码明文。 */
    String revealPassword(Long userId, Long credentialId);

    /** 更新凭证。 */
    Credential updateCredential(Long userId, Long credentialId, UpdateCredentialCommand command);

    /** 删除凭证。 */
    void deleteCredential(Long userId, Long credentialId);
}
