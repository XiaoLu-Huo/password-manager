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
@TableName("pm_user")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("master_password_hash")
    private String masterPasswordHash;

    @TableField("salt")
    private String salt;

    @TableField("encryption_key_encrypted")
    private byte[] encryptionKeyEncrypted;

    @TableField("failed_attempts")
    private Integer failedAttempts;

    @TableField("locked_until")
    private LocalDateTime lockedUntil;

    @TableField("auto_lock_minutes")
    private Integer autoLockMinutes;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
