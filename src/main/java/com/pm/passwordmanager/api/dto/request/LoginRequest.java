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
@Schema(description = "用户登录请求")
public class LoginRequest {

    @NotBlank(message = "登录标识符不能为空")
    @Schema(description = "登录标识符（用户名或邮箱）", example = "john_doe", requiredMode = Schema.RequiredMode.REQUIRED)
    private String identifier;

    @NotBlank(message = "主密码不能为空")
    @Schema(description = "主密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String masterPassword;
}
