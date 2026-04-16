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
@Schema(description = "创建凭证请求")
public class CreateCredentialRequest {

    @NotBlank(message = "账户名称不能为空")
    @Schema(description = "账户名称", example = "GitHub", requiredMode = Schema.RequiredMode.REQUIRED)
    private String accountName;

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Schema(description = "密码（autoGenerate 为 true 时可不填）")
    private String password;

    @Schema(description = "关联 URL", example = "https://github.com")
    private String url;

    @Schema(description = "备注")
    private String notes;

    @Schema(description = "分类标签，逗号分隔", example = "开发,代码托管")
    private String tags;

    @Schema(description = "是否自动生成密码", example = "false")
    private Boolean autoGenerate;
}
