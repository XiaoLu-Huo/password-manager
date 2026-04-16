package com.pm.passwordmanager.domain.service;

import java.util.List;

import com.pm.passwordmanager.api.dto.response.SecurityReportResponse;
import com.pm.passwordmanager.api.enums.PasswordStrengthLevel;
import com.pm.passwordmanager.domain.model.Credential;

/**
 * 安全报告服务接口。
 * 提供密码强度检测、重复密码检测、超期未更新检测和安全报告统计。
 */
public interface SecurityReportService {

    /** 获取安全报告统计。 */
    SecurityReportResponse getReport(Long userId);

    /** 按密码强度等级获取凭证列表。 */
    List<Credential> getCredentialsByStrengthLevel(Long userId, PasswordStrengthLevel level);

    /** 获取重复密码凭证列表。 */
    List<Credential> getDuplicatePasswordCredentials(Long userId);

    /** 获取超期未更新（>90天）凭证列表。 */
    List<Credential> getExpiredPasswordCredentials(Long userId);
}
