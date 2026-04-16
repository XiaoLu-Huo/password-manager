package com.pm.passwordmanager.domain.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * MFA 配置领域模型。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaConfig {

    private Long id;
    private Long userId;
    private String totpSecretEncrypted;
    private Boolean enabled;
    private String recoveryCodesEncrypted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
