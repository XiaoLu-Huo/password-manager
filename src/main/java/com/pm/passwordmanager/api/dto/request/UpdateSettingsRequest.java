package com.pm.passwordmanager.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新设置请求")
public class UpdateSettingsRequest {

    @NotNull(message = "自动锁定超时时间不能为空")
    @Min(value = 1, message = "自动锁定超时时间必须在 1-60 分钟之间")
    @Max(value = 60, message = "自动锁定超时时间必须在 1-60 分钟之间")
    @Schema(description = "自动锁定超时时间（分钟，1-60）", example = "5", minimum = "1", maximum = "60", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer autoLockMinutes;
}
