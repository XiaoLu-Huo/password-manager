package com.pm.passwordmanager.domain.repository;

import java.util.Optional;

import com.pm.passwordmanager.domain.model.MfaConfig;

/**
 * MFA 配置仓储接口。面向领域模型定义，由基础设施层实现。
 */
public interface MfaConfigRepository {

    /** 根据用户 ID 查询 MFA 配置。 */
    Optional<MfaConfig> findByUserId(Long userId);

    /** 保存新的 MFA 配置。 */
    MfaConfig save(MfaConfig mfaConfig);

    /** 更新 MFA 配置。 */
    void updateById(MfaConfig mfaConfig);
}
