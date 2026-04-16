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
@TableName("password_history")
public class PasswordHistoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("credential_id")
    private Long credentialId;

    @TableField("password_encrypted")
    private byte[] passwordEncrypted;

    @TableField("iv")
    private byte[] iv;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
