package com.pm.passwordmanager.api.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MFA 设置结果")
public class MfaSetupResponse {

    @Schema(description = "TOTP 二维码 URI，供认证器应用扫描")
    private String qrCodeUri;

    @Schema(description = "一次性恢复码列表，用于 TOTP 设备丢失时恢复访问")
    private List<String> recoveryCodes;
}
