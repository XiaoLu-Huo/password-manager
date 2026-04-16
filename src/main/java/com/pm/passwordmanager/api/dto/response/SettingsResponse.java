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
@Schema(description = "用户设置响应")
public class SettingsResponse {

    @Schema(description = "自动锁定超时时间（分钟）", example = "5")
    private Integer autoLockMinutes;
}
