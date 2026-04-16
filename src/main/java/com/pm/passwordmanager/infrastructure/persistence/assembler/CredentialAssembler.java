package com.pm.passwordmanager.infrastructure.persistence.assembler;

import org.mapstruct.Mapper;

import com.pm.passwordmanager.domain.model.Credential;
import com.pm.passwordmanager.infrastructure.persistence.entity.CredentialEntity;

/**
 * 凭证 Entity ↔ Domain Model 转换器。
 * 由 CredentialRepositoryImpl 使用，隔离持久化层与领域层。
 */
@Mapper(componentModel = "spring")
public interface CredentialAssembler {

    Credential toDomain(CredentialEntity entity);

    CredentialEntity toEntity(Credential model);
}
