package com.pm.passwordmanager.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "凭证详情")
public class CredentialResponse {

    @Schema(description = "凭证 ID", example = "1")
    private Long id;

    @Schema(description = "账户名称", example = "GitHub")
    private String accountName;

    @Schema(description = "用户名", example = "user@example.com")
    private String username;

    @Schema(description = "掩码密码", example = "••••••")
    private String maskedPassword;

    @Schema(description = "关联 URL", example = "https://github.com")
    private String url;

    @Schema(description = "备注")
    private String notes;

    @Schema(description = "分类标签", example = "开发,代码托管")
    private String tags;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "最后修改时间")
    private LocalDateTime updatedAt;
}
