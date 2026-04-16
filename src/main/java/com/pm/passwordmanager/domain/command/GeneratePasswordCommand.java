package com.pm.passwordmanager.domain.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 生成密码命令对象。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratePasswordCommand {

    private Integer length;
    private Boolean includeUppercase;
    private Boolean includeLowercase;
    private Boolean includeDigits;
    private Boolean includeSpecial;
    private Boolean useDefault;
}
