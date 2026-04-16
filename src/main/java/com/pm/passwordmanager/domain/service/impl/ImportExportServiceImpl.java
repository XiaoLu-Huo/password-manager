package com.pm.passwordmanager.domain.service.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.excel.EasyExcel;
import com.pm.passwordmanager.api.dto.response.ImportResultResponse;
import com.pm.passwordmanager.api.enums.ConflictStrategy;
import com.pm.passwordmanager.domain.model.Credential;
import com.pm.passwordmanager.domain.repository.CredentialRepository;
import com.pm.passwordmanager.domain.service.ImportExportService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;
import com.pm.passwordmanager.infrastructure.encryption.ExcelEncryptionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 导入导出服务实现。
 * 导出：查询凭证 → 解密密码 → EasyExcel 写入 → POI 加密保护
 * 导入：POI 解密 → EasyExcel 读取 → 冲突策略处理 → 加密存储
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportExportServiceImpl implements ImportExportService {

    private final CredentialRepository credentialRepository;
    private final EncryptionEngine encryptionEngine;
    private final SessionService sessionService;

    @Override
    public byte[] exportAsEncryptedExcel(Long userId, String encryptionPassword) {
        byte[] dek = getActiveDek(userId);

        // 1. 查询用户所有凭证
        List<Credential> credentials = credentialRepository.findByUserId(userId);

        // 2. 解密密码并转换为 Excel 行
        List<CredentialExcelRow> rows = new ArrayList<>();
        for (Credential credential : credentials) {
            String plainPassword = decryptPassword(credential, dek);
            rows.add(CredentialExcelRow.builder()
                    .accountName(credential.getAccountName())
                    .username(credential.getUsername())
                    .password(plainPassword)
                    .url(credential.getUrl())
                    .notes(credential.getNotes())
                    .tags(credential.getTags())
                    .build());
        }

        // 3. 使用 EasyExcel 写入内存
        ByteArrayOutputStream excelOut = new ByteArrayOutputStream();
        try {
            EasyExcel.write(excelOut, CredentialExcelRow.class).sheet("Credentials").doWrite(rows);
        } catch (Exception e) {
            log.error("EasyExcel write failed", e);
            throw new BusinessException(ErrorCode.EXPORT_FAILED);
        }

        // 4. 使用 Apache POI 加密 Excel 文件
        try {
            return ExcelEncryptionUtil.encrypt(excelOut.toByteArray(), encryptionPassword);
        } catch (Exception e) {
            log.error("Excel encryption failed", e);
            throw new BusinessException(ErrorCode.EXPORT_FAILED);
        }
    }

    @Override
    @Transactional
    public ImportResultResponse importFromExcel(Long userId, byte[] fileContent, String filePassword,
                                                 ConflictStrategy strategy) {
        byte[] dek = getActiveDek(userId);

        // 1. 解密 Excel 文件
        byte[] decryptedExcel;
        try {
            decryptedExcel = ExcelEncryptionUtil.decrypt(fileContent, filePassword);
        } catch (Exception e) {
            log.error("Excel decryption error", e);
            throw new BusinessException(ErrorCode.IMPORT_DECRYPTION_FAILED);
        }
        if (decryptedExcel == null) {
            throw new BusinessException(ErrorCode.IMPORT_DECRYPTION_FAILED);
        }

        // 2. 使用 EasyExcel 读取数据
        List<CredentialExcelRow> rows;
        try {
            rows = EasyExcel.read(new ByteArrayInputStream(decryptedExcel))
                    .head(CredentialExcelRow.class)
                    .sheet()
                    .doReadSync();
        } catch (Exception e) {
            log.error("EasyExcel read failed", e);
            throw new BusinessException(ErrorCode.IMPORT_PARSE_ERROR);
        }

        if (rows == null || rows.isEmpty()) {
            return ImportResultResponse.builder()
                    .totalCount(0).importedCount(0).skippedCount(0).overwrittenCount(0)
                    .build();
        }

        // 3. 按冲突策略逐条处理
        int imported = 0;
        int skipped = 0;
        int overwritten = 0;

        for (CredentialExcelRow row : rows) {
            if (isBlank(row.getAccountName()) || isBlank(row.getUsername()) || isBlank(row.getPassword())) {
                skipped++;
                continue;
            }

            List<Credential> existing = credentialRepository.findByUserIdAndAccountName(userId, row.getAccountName());

            if (existing.isEmpty()) {
                // 无冲突，直接新增
                saveNewCredential(userId, row, dek);
                imported++;
            } else {
                switch (strategy) {
                    case OVERWRITE -> {
                        Credential target = existing.get(0);
                        updateCredentialFromRow(target, row, dek);
                        credentialRepository.updateById(target);
                        overwritten++;
                    }
                    case SKIP -> skipped++;
                    case KEEP_BOTH -> {
                        saveNewCredential(userId, row, dek);
                        imported++;
                    }
                }
            }
        }

        return ImportResultResponse.builder()
                .totalCount(rows.size())
                .importedCount(imported)
                .skippedCount(skipped)
                .overwrittenCount(overwritten)
                .build();
    }

    // ==================== 私有方法 ====================

    private byte[] getActiveDek(Long userId) {
        byte[] dek = sessionService.getDek(userId);
        if (dek == null) {
            throw new BusinessException(ErrorCode.VAULT_LOCKED);
        }
        return dek;
    }

    private String decryptPassword(Credential credential, byte[] dek) {
        EncryptedData encrypted = new EncryptedData(credential.getPasswordEncrypted(), credential.getIv());
        byte[] plaintext = encryptionEngine.decrypt(encrypted, dek);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private void saveNewCredential(Long userId, CredentialExcelRow row, byte[] dek) {
        EncryptedData encrypted = encryptionEngine.encrypt(
                row.getPassword().getBytes(StandardCharsets.UTF_8), dek);
        LocalDateTime now = LocalDateTime.now();

        Credential credential = Credential.builder()
                .userId(userId)
                .accountName(row.getAccountName())
                .username(row.getUsername())
                .passwordEncrypted(encrypted.getCiphertext())
                .iv(encrypted.getIv())
                .url(row.getUrl())
                .notes(row.getNotes())
                .tags(row.getTags())
                .createdAt(now)
                .updatedAt(now)
                .build();

        credentialRepository.save(credential);
    }

    private void updateCredentialFromRow(Credential credential, CredentialExcelRow row, byte[] dek) {
        EncryptedData encrypted = encryptionEngine.encrypt(
                row.getPassword().getBytes(StandardCharsets.UTF_8), dek);

        credential.setUsername(row.getUsername());
        credential.setUrl(row.getUrl());
        credential.setNotes(row.getNotes());
        credential.setTags(row.getTags());
        credential.setPasswordEncrypted(encrypted.getCiphertext());
        credential.setIv(encrypted.getIv());
        credential.setUpdatedAt(LocalDateTime.now());
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
