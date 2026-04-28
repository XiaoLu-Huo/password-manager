package com.pm.passwordmanager.domain.service;

import com.pm.passwordmanager.api.dto.response.UnlockResultResponse;
import com.pm.passwordmanager.domain.command.LoginCommand;
import com.pm.passwordmanager.domain.command.RegisterCommand;

/**
 * 认证服务接口。
 * 负责用户注册和登录认证。
 */
public interface AuthService {

    /**
     * 用户注册。
     * 流程：验证用户名/邮箱/密码 → 检查唯一性 → 生成 salt → Argon2id 哈希 → 生成 DEK → 用 KEK 加密 DEK → 存储
     *
     * @param command 注册命令
     */
    void register(RegisterCommand command);

    /**
     * 用户登录。
     * 流程：根据标识符查找用户 → 检查锁定状态 → 验证密码 → 检查 MFA → 派生 KEK → 解密 DEK → 存入会话（或等待 TOTP）
     *
     * @param command 登录命令
     * @return 登录结果（包含是否需要 MFA 验证等信息）
     */
    UnlockResultResponse login(LoginCommand command);

    /**
     * 验证 TOTP 码并完成解锁（MFA 启用时的第二步）。
     *
     * @param mfaToken 登录时返回的 MFA 临时令牌
     * @param totpCode TOTP 验证码
     * @return 解锁结果
     */
    UnlockResultResponse verifyTotpAndUnlock(String mfaToken, String totpCode);

    /**
     * 获取当前用户 ID（从 SessionContextHolder 获取）。
     *
     * @return 用户 ID
     */
    Long getCurrentUserId();

    /**
     * 检查系统是否可用（始终返回 true）。
     *
     * @return true
     */
    boolean isInitialized();
}
