package com.pm.passwordmanager.domain.repository;

import java.util.List;
import java.util.Optional;

import com.pm.passwordmanager.domain.model.PasswordRule;

/**
 * 密码规则仓储接口。面向领域模型定义，由基础设施层实现。
 */
public interface PasswordRuleRepository {

    /** 保存密码规则。 */
    PasswordRule save(PasswordRule rule);

    /** 根据用户 ID 查询所有密码规则。 */
    List<PasswordRule> findByUserId(Long userId);

    /** 根据 ID 查询密码规则。 */
    Optional<PasswordRule> findById(Long id);

    /** 根据 ID 删除密码规则。 */
    void deleteById(Long id);
}
