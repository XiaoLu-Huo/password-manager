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
@Schema(description = "解锁密码库请求")
public class UnlockVaultRequest {

    @NotBlank(message = "主密码不能为空")
    @Schema(description = "主密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String masterPassword;
}
