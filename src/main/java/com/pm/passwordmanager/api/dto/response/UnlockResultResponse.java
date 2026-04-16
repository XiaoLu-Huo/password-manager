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
@Schema(description = "解锁密码库结果")
public class UnlockResultResponse {

    @Schema(description = "是否需要 MFA 二次验证", example = "false")
    private boolean mfaRequired;

    @Schema(description = "会话令牌（MFA 验证通过后返回）")
    private String sessionToken;
}
