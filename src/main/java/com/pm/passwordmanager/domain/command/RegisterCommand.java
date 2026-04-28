package com.pm.passwordmanager.domain.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 用户注册命令对象。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterCommand {

    private String username;
    private String email;
    private String masterPassword;
}
