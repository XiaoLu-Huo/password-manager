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
@Schema(description = "首次创建主密码请求")
public class CreateMasterPasswordRequest {

    @NotBlank(message = "主密码不能为空")
    @Schema(description = "主密码，长度≥12，需包含至少三种字符类型", example = "MyStr0ng!Pass", requiredMode = Schema.RequiredMode.REQUIRED)
    private String masterPassword;
}
