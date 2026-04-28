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
@Schema(description = "验证 TOTP 验证码请求")
public class VerifyTotpRequest {

    @NotBlank(message = "MFA 令牌不能为空")
    @Schema(description = "登录时返回的 MFA 临时令牌", requiredMode = Schema.RequiredMode.REQUIRED)
    private String mfaToken;

    @NotBlank(message = "TOTP 验证码不能为空")
    @Schema(description = "TOTP 动态验证码（6位数字）", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String totpCode;
}
