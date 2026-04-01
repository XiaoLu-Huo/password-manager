package com.pm.passwordmanager.service;

/**
 * 会话服务接口。
 * 管理内存中的 DEK 会话，支持自动锁定超时检测。
 */
public interface SessionService {

    /**
     * 存储用户的 DEK 到内存会话中，并记录活动时间。
     *
     * @param userId 用户 ID
     * @param dek    数据加密密钥
     */
    void storeDek(Long userId, byte[] dek);

    /**
     * 获取用户的 DEK。如果会话已超时则自动清除并返回 null。
     *
     * @param userId 用户 ID
     * @return DEK 字节数组，如果会话不存在或已过期则返回 null
     */
    byte[] getDek(Long userId);

    /**
     * 清除用户的会话（锁定密码库），安全擦除内存中的 DEK。
     *
     * @param userId 用户 ID
     */
    void clearSession(Long userId);

    /**
     * 检查用户会话是否存在且有效（未超时）。
     *
     * @param userId 用户 ID
     * @return 会话有效返回 true
     */
    boolean isSessionActive(Long userId);

    /**
     * 刷新用户的最后活动时间，防止自动锁定。
     *
     * @param userId 用户 ID
     */
    void refreshActivity(Long userId);

    /**
     * 设置用户的自动锁定超时时间。
     *
     * @param userId  用户 ID
     * @param minutes 超时时间（分钟，1-60）
     */
    void setAutoLockTimeout(Long userId, int minutes);

    /**
     * 获取用户的自动锁定超时时间（分钟）。
     *
     * @param userId 用户 ID
     * @return 超时时间（分钟），默认 5 分钟
     */
    int getAutoLockTimeout(Long userId);
}
