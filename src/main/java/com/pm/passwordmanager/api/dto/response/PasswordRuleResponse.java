package com.pm.passwordmanager.api.dto.response;

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
@Schema(description = "密码规则")
public class PasswordRuleResponse {

    @Schema(description = "规则 ID", example = "1")
    private Long id;

    @Schema(description = "规则名称", example = "强密码规则")
    private String ruleName;

    @Schema(description = "密码长度", example = "16")
    private Integer length;

    @Schema(description = "是否包含大写字母", example = "true")
    private Boolean includeUppercase;

    @Schema(description = "是否包含小写字母", example = "true")
    private Boolean includeLowercase;

    @Schema(description = "是否包含数字", example = "true")
    private Boolean includeDigits;

    @Schema(description = "是否包含特殊字符", example = "true")
    private Boolean includeSpecial;

    @Schema(description = "是否为默认规则", example = "false")
    private Boolean isDefault;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
