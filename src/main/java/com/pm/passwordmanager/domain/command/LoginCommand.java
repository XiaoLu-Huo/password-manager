package com.pm.passwordmanager.domain.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 用户登录命令对象。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginCommand {

    private String identifier;
    private String masterPassword;
}
