package com.pm.passwordmanager.domain.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 创建凭证命令对象。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCredentialCommand {

    private String accountName;
    private String username;
    private String password;
    private String url;
    private String notes;
    private String tags;
    private Boolean autoGenerate;
}
