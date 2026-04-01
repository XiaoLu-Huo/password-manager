package com.pm.passwordmanager.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "生成密码请求")
public class GeneratePasswordRequest {

    @Min(value = 8, message = "密码长度不能少于 8 个字符")
    @Max(value = 128, message = "密码长度不能超过 128 个字符")
    @Schema(description = "密码长度（8-128）", example = "16", minimum = "8", maximum = "128")
    private Integer length;

    @Schema(description = "是否包含大写字母", example = "true")
    private Boolean includeUppercase;

    @Schema(description = "是否包含小写字母", example = "true")
    private Boolean includeLowercase;

    @Schema(description = "是否包含数字", example = "true")
    private Boolean includeDigits;

    @Schema(description = "是否包含特殊字符", example = "true")
    private Boolean includeSpecial;

    @Schema(description = "是否使用默认规则（默认16位，含所有字符类型）", example = "false")
    private Boolean useDefault;
}
