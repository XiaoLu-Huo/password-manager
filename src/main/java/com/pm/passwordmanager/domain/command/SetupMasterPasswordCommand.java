package com.pm.passwordmanager.domain.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 创建主密码命令对象。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetupMasterPasswordCommand {

    private String masterPassword;
}
