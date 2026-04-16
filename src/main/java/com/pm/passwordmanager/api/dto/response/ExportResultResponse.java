package com.pm.passwordmanager.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "导出结果")
public class ExportResultResponse {

    @Schema(description = "导出文件名", example = "vault_export_20260401.xlsx")
    private String fileName;

    @Schema(description = "文件大小（字节）", example = "10240")
    private long fileSize;
}
