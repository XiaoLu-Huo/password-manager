package com.pm.passwordmanager.dto.request;

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
@Schema(description = "启用 MFA 请求")
public class EnableMfaRequest {

    @NotBlank(message = "TOTP 验证码不能为空")
    @Schema(description = "TOTP 验证码，用于确认绑定", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String totpCode;
}
