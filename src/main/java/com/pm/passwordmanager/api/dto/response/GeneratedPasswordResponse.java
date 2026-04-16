package com.pm.passwordmanager.api.dto.response;

import com.pm.passwordmanager.api.enums.PasswordStrengthLevel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "生成密码结果")
public class GeneratedPasswordResponse {

    @Schema(description = "生成的密码")
    private String password;

    @Schema(description = "密码强度等级")
    private PasswordStrengthLevel strengthLevel;
}
