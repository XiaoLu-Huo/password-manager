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
@Schema(description = "密码强度评估结果")
public class PasswordStrengthResponse {

    @Schema(description = "密码强度等级：WEAK / MEDIUM / STRONG")
    private PasswordStrengthLevel strengthLevel;
}
