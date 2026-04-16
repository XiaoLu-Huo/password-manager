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
@Schema(description = "凭证列表摘要")
public class CredentialListResponse {

    @Schema(description = "凭证 ID", example = "1")
    private Long id;

    @Schema(description = "账户名称", example = "GitHub")
    private String accountName;

    @Schema(description = "用户名", example = "user@example.com")
    private String username;

    @Schema(description = "关联 URL", example = "https://github.com")
    private String url;

    @Schema(description = "分类标签", example = "开发,代码托管")
    private String tags;
}
