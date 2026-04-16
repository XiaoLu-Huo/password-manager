package com.pm.passwordmanager.domain.service;

import com.pm.passwordmanager.api.dto.response.UnlockResultResponse;
import com.pm.passwordmanager.domain.command.SetupMasterPasswordCommand;
import com.pm.passwordmanager.domain.command.UnlockVaultCommand;

/**
 * 认证服务接口。
 * 负责主密码的创建和密码库的解锁。
 */
/**
 * 认证服务接口。
 * 负责主密码的创建和密码库的解锁。
 */
public interface AuthService {

    /**
     * 首次创建主密码。
     * 流程：验证复杂度 → 生成 salt → Argon2id 哈希 → 生成 DEK → 用 KEK 加密 DEK → 存储
     *
     * @param request 创建主密码请求
     */
    void setup(SetupMasterPasswordCommand command);

    /**
     * 解锁密码库。
     * 流程：检查锁定状态 → 验证密码 → 检查 MFA → 派生 KEK → 解密 DEK → 存入会话（或等待 TOTP）
     *
     * @param command 解锁命令
     * @return 解锁结果（包含是否需要 MFA 验证等信息）
     */
    UnlockResultResponse unlock(UnlockVaultCommand command);

    /**
     * 验证 TOTP 码并完成解锁（MFA 启用时的第二步）。
     *
     * @param totpCode TOTP 验证码
     * @return 解锁结果
     */
    UnlockResultResponse verifyTotpAndUnlock(String totpCode);

    /**
     * 获取当前用户 ID（单用户系统）。
     *
     * @return 用户 ID
     */
    Long getCurrentUserId();
}
