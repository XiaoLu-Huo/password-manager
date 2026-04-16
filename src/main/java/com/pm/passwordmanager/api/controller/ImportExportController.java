package com.pm.passwordmanager.api.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pm.passwordmanager.api.dto.request.ExportRequest;
import com.pm.passwordmanager.api.dto.response.ApiResponse;
import com.pm.passwordmanager.api.dto.response.ImportResultResponse;
import com.pm.passwordmanager.api.enums.ConflictStrategy;
import com.pm.passwordmanager.domain.service.AuthService;
import com.pm.passwordmanager.domain.service.ImportExportService;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 导入导出控制器。
 * 支持将凭证导出为加密 Excel 文件，以及从加密 Excel 文件导入凭证。
 */
@RestController
@RequestMapping("/api/import-export")
@RequiredArgsConstructor
@Tag(name = "导入导出", description = "凭证数据的 Excel 导入与导出接口")
public class ImportExportController {

    private final ImportExportService importExportService;
    private final AuthService authService;

    @PostMapping("/export")
    @Operation(summary = "导出凭证为加密 Excel 文件")
    public ResponseEntity<byte[]> export(@Valid @org.springframework.web.bind.annotation.RequestBody ExportRequest request) {
        Long userId = authService.getCurrentUserId();
        byte[] fileBytes = importExportService.exportAsEncryptedExcel(userId, request.getEncryptionPassword());

        String fileName = "vault_export_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(fileBytes.length)
                .body(fileBytes);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "从加密 Excel 文件导入凭证")
    public ApiResponse<ImportResultResponse> importExcel(
            @Parameter(description = "Excel 文件", required = true) @RequestPart("file") MultipartFile file,
            @Parameter(description = "文件密码", required = true) @RequestPart("filePassword") String filePassword,
            @Parameter(description = "冲突策略", required = true) @RequestPart("conflictStrategy") String conflictStrategy) {

        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_FORMAT);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_FORMAT);
        }

        ConflictStrategy strategy;
        try {
            strategy = ConflictStrategy.valueOf(conflictStrategy.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Long userId = authService.getCurrentUserId();
        try {
            byte[] fileContent = file.getBytes();
            ImportResultResponse result = importExportService.importFromExcel(userId, fileContent, filePassword, strategy);
            return ApiResponse.success(result);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_FORMAT);
        }
    }
}
