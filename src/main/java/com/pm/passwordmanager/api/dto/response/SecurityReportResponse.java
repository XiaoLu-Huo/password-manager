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
@Schema(description = "安全报告统计")
public class SecurityReportResponse {

    @Schema(description = "总凭证数量", example = "42")
    private int totalCredentials;

    @Schema(description = "弱密码数量", example = "5")
    private int weakPasswordCount;

    @Schema(description = "中等强度密码数量", example = "10")
    private int mediumPasswordCount;

    @Schema(description = "重复密码数量", example = "3")
    private int duplicatePasswordCount;

    @Schema(description = "超期未更新密码数量（>90天）", example = "8")
    private int expiredPasswordCount;
}
