package com.pm.passwordmanager.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("password_rule")
public class PasswordRuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("rule_name")
    private String ruleName;

    @TableField("length")
    private Integer length;

    @TableField("include_uppercase")
    private Boolean includeUppercase;

    @TableField("include_lowercase")
    private Boolean includeLowercase;

    @TableField("include_digits")
    private Boolean includeDigits;

    @TableField("include_special")
    private Boolean includeSpecial;

    @TableField("is_default")
    private Boolean isDefault;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
