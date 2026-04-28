package com.pm.passwordmanager.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户注册请求")
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名，3-32字符，仅允许字母、数字、下划线、连字符", example = "john_doe", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不合法")
    @Schema(description = "邮箱地址", example = "john@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "主密码不能为空")
    @Schema(description = "主密码，长度≥12，需包含至少三种字符类型", example = "MyStr0ng!Pass", requiredMode = Schema.RequiredMode.REQUIRED)
    private String masterPassword;
}
