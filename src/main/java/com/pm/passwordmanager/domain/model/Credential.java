package com.pm.passwordmanager.domain.model;

import java.time.LocalDateTime;

import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 凭证领域模型。
 * 包含凭证的业务行为：必填字段验证、标签匹配、密码变更检查等。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Credential {

    private Long id;
    private Long userId;
    private String accountName;
    private String username;
    private byte[] passwordEncrypted;
    private byte[] iv;
    private String url;
    private String notes;
    private String tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 验证创建凭证时的必填字段完整性。 */
    public void validateRequiredFields(String plainPassword) {
        if (isBlank(accountName) || isBlank(username) || isBlank(plainPassword)) {
            throw new BusinessException(ErrorCode.CREDENTIAL_REQUIRED_FIELDS_MISSING);
        }
    }

    /** 验证新密码与当前密码不同。 */
    public void validatePasswordChange(String newPassword, String currentPassword) {
        if (newPassword.equals(currentPassword)) {
            throw new BusinessException(ErrorCode.SAME_PASSWORD);
        }
    }

    /** 应用非空字段的更新。 */
    public void applyUpdate(String accountName, String username, String url, String notes, String tags) {
        if (accountName != null) this.accountName = accountName;
        if (username != null) this.username = username;
        if (url != null) this.url = url;
        if (notes != null) this.notes = notes;
        if (tags != null) this.tags = tags;
        this.updatedAt = LocalDateTime.now();
    }

    /** 更新加密后的密码数据。 */
    public void updateEncryptedPassword(byte[] passwordEncrypted, byte[] iv) {
        this.passwordEncrypted = passwordEncrypted;
        this.iv = iv;
        this.updatedAt = LocalDateTime.now();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
