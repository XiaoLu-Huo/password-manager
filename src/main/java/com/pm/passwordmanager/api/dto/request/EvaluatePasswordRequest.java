package com.pm.passwordmanager.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "密码强度评估请求")
public class EvaluatePasswordRequest {

    @NotBlank(message = "密码不能为空")
    @Schema(description = "待评估的密码")
    private String password;
}
