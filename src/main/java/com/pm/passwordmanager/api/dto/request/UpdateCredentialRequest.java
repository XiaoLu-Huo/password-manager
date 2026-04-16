package com.pm.passwordmanager.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新凭证请求")
public class UpdateCredentialRequest {

    @Schema(description = "账户名称", example = "GitHub")
    private String accountName;

    @Schema(description = "用户名", example = "user@example.com")
    private String username;

    @Schema(description = "新密码")
    private String password;

    @Schema(description = "关联 URL", example = "https://github.com")
    private String url;

    @Schema(description = "备注")
    private String notes;

    @Schema(description = "分类标签，逗号分隔", example = "开发,代码托管")
    private String tags;
}
