package com.pm.passwordmanager.domain.repository;

import java.util.Optional;

import com.pm.passwordmanager.domain.model.User;

/**
 * 用户仓储接口。面向领域模型定义，由基础设施层实现。
 */
public interface UserRepository {

    /** 查询第一个用户（单用户系统）。 */
    Optional<User> findFirst();

    /** 按用户名查找用户。 */
    Optional<User> findByUsername(String username);

    /** 按邮箱查找用户。 */
    Optional<User> findByEmail(String email);

    /** 保存新用户。 */
    User save(User user);

    /** 更新用户信息。 */
    void updateById(User user);
}
