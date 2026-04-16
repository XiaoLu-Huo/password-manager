package com.pm.passwordmanager.api.dto.request;

import com.pm.passwordmanager.api.enums.ConflictStrategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "导入凭证请求")
public class ImportRequest {

    @NotBlank(message = "文件密码不能为空")
    @Schema(description = "Excel 文件的加密密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String filePassword;

    @NotNull(message = "冲突策略不能为空")
    @Schema(description = "导入冲突策略：OVERWRITE-覆盖 / SKIP-跳过 / KEEP_BOTH-保留两者", requiredMode = Schema.RequiredMode.REQUIRED)
    private ConflictStrategy conflictStrategy;
}
