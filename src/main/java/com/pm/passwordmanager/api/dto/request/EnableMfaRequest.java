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
@Schema(description = "启用 MFA 请求")
public class EnableMfaRequest {

    @Schema(description = "TOTP 验证码，用于确认绑定。首次调用不传此字段以获取二维码和恢复码，第二次调用传入验证码以确认启用。", example = "123456")
    private String totpCode;
}
