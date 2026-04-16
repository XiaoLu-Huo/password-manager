package com.pm.passwordmanager.domain.service;

import com.pm.passwordmanager.api.dto.response.ImportResultResponse;
import com.pm.passwordmanager.api.enums.ConflictStrategy;

/**
 * 导入导出服务接口。
 * 支持将凭证导出为加密 Excel 文件，以及从加密 Excel 文件导入凭证。
 */
public interface ImportExportService {

    /**
     * 导出用户凭证为加密 Excel 文件。
     *
     * @param userId             用户 ID
     * @param encryptionPassword Excel 文件加密密码
     * @return 加密后的 Excel 文件字节数组
     */
    byte[] exportAsEncryptedExcel(Long userId, String encryptionPassword);

    /**
     * 从加密 Excel 文件导入凭证。
     *
     * @param userId      用户 ID
     * @param fileContent Excel 文件字节内容
     * @param filePassword Excel 文件密码
     * @param strategy    冲突策略
     * @return 导入结果统计
     */
    ImportResultResponse importFromExcel(Long userId, byte[] fileContent, String filePassword, ConflictStrategy strategy);
}
