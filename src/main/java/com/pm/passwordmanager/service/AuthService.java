package com.pm.passwordmanager.service;

import com.pm.passwordmanager.dto.request.CreateMasterPasswordRequest;
import com.pm.passwordmanager.dto.request.UnlockVaultRequest;
import com.pm.passwordmanager.dto.response.UnlockResultResponse;

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
    void setup(CreateMasterPasswordRequest request);

    /**
     * 解锁密码库。
     * 流程：检查锁定状态 → 验证密码 → 派生 KEK → 解密 DEK → 存入会话
     *
     * @param request 解锁请求
     * @return 解锁结果（包含是否需要 MFA 验证等信息）
     */
    UnlockResultResponse unlock(UnlockVaultRequest request);
}
