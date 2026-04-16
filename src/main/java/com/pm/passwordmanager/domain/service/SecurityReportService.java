package com.pm.passwordmanager.domain.service;

import java.util.List;

import com.pm.passwordmanager.api.dto.response.SecurityReportResponse;
import com.pm.passwordmanager.domain.model.Credential;

/**
 * 安全报告服务接口。
 * 提供弱密码检测、重复密码检测、超期未更新检测和安全报告统计。
 * Service 层返回领域模型，由 Controller 层负责 DTO 转换。
 */
public interface SecurityReportService {

    /** 获取安全报告统计（总凭证数、弱密码数、重复密码数、超期数）。 */
    SecurityReportResponse getReport(Long userId);

    /** 获取弱密码凭证列表。 */
    List<Credential> getWeakPasswordCredentials(Long userId);

    /** 获取重复密码凭证列表。 */
    List<Credential> getDuplicatePasswordCredentials(Long userId);

    /** 获取超期未更新（>90天）凭证列表。 */
    List<Credential> getExpiredPasswordCredentials(Long userId);
}
