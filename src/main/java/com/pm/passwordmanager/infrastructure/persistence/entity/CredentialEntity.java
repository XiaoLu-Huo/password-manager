package com.pm.passwordmanager.infrastructure.persistence.entity;

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
@TableName("pm_credential")
public class CredentialEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("account_name")
    private String accountName;

    @TableField("username")
    private String username;

    @TableField("password_encrypted")
    private byte[] passwordEncrypted;

    @TableField("url")
    private String url;

    @TableField("notes")
    private String notes;

    @TableField("tags")
    private String tags;

    @TableField("iv")
    private byte[] iv;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
