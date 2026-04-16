package com.pm.passwordmanager.domain.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 解锁密码库命令对象。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnlockVaultCommand {

    private String masterPassword;
}
