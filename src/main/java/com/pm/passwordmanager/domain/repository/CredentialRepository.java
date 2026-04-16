package com.pm.passwordmanager.domain.repository;

import java.util.List;
import java.util.Optional;

import com.pm.passwordmanager.domain.model.Credential;

/**
 * 凭证仓储接口。面向领域模型定义，由基础设施层实现。
 */
public interface CredentialRepository {

    Credential save(Credential credential);

    Optional<Credential> findById(Long id);

    List<Credential> findByUserId(Long userId);

    List<Credential> searchByKeyword(Long userId, String keyword);

    List<Credential> filterByTag(Long userId, String tag);

    void updateById(Credential credential);

    void deleteById(Long id);

    /** 按用户 ID 和账户名称查找凭证（用于导入冲突检测）。 */
    List<Credential> findByUserIdAndAccountName(Long userId, String accountName);
}
