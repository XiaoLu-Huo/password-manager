package com.pm.passwordmanager.domain.assembler;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.pm.passwordmanager.domain.command.CreateCredentialCommand;
import com.pm.passwordmanager.domain.model.Credential;

/**
 * 领域层映射器。负责 Command → Domain Model 的转换。
 */
@Mapper(componentModel = "spring")
public interface CredentialModelAssembler {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordEncrypted", ignore = true)
    @Mapping(target = "iv", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Credential toModel(CreateCredentialCommand command, Long userId);
}
