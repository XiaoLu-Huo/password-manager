package com.pm.passwordmanager.api.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "密码历史记录")
public class PasswordHistoryResponse {

    @Schema(description = "历史记录 ID", example = "1")
    private Long id;

    @Schema(description = "掩码密码", example = "••••••")
    private String maskedPassword;

    @Schema(description = "密码变更时间")
    private LocalDateTime changedAt;
}
