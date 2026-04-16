package com.pm.passwordmanager.domain.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 密码规则领域模型。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordRule {

    private Long id;
    private Long userId;
    private String ruleName;
    private Integer length;
    private Boolean includeUppercase;
    private Boolean includeLowercase;
    private Boolean includeDigits;
    private Boolean includeSpecial;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
