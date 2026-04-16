package com.pm.passwordmanager.domain.service;

import com.pm.passwordmanager.api.dto.response.MfaSetupResponse;

/**
 * MFA 服务接口。
 * 负责 TOTP 多因素认证的启用、验证和禁用。
 */
public interface MfaService {

    /**
     * 初始化 MFA 设置：生成 TOTP 密钥、二维码和恢复码。
     * 此时 MFA 尚未正式启用，需要用户验证 TOTP 码后才确认启用。
     *
     * @param userId 用户 ID
     * @return MFA 设置信息（二维码 Data URI 和恢复码）
     */
    MfaSetupResponse initSetup(Long userId);

    /**
     * 确认启用 MFA：验证用户提交的 TOTP 码，验证通过后正式启用 MFA。
     *
     * @param userId   用户 ID
     * @param totpCode 用户输入的 TOTP 验证码
     */
    void confirmEnable(Long userId, String totpCode);

    /**
     * 验证 TOTP 验证码。
     *
     * @param userId   用户 ID
     * @param totpCode 用户输入的 TOTP 验证码
     * @return 验证通过返回 true
     */
    boolean verifyTotp(Long userId, String totpCode);

    /**
     * 禁用 MFA。
     *
     * @param userId 用户 ID
     */
    void disable(Long userId);

    /**
     * 检查用户是否已启用 MFA。
     *
     * @param userId 用户 ID
     * @return 已启用返回 true
     */
    boolean isMfaEnabled(Long userId);
}
