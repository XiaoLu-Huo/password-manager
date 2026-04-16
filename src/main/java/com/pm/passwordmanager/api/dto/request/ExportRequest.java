package com.pm.passwordmanager.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "导出凭证请求")
public class ExportRequest {

    @NotBlank(message = "加密密码不能为空")
    @Schema(description = "导出 Excel 文件的加密密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encryptionPassword;
}
