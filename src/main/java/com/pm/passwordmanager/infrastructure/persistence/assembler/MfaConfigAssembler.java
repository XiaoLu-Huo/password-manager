package com.pm.passwordmanager.infrastructure.persistence.assembler;

import org.mapstruct.Mapper;

import com.pm.passwordmanager.domain.model.MfaConfig;
import com.pm.passwordmanager.infrastructure.persistence.entity.MfaConfigEntity;

/**
 * MFA 配置 Entity ↔ Domain Model 转换器。
 * 由 MfaConfigRepositoryImpl 使用，隔离持久化层与领域层。
 */
@Mapper(componentModel = "spring")
public interface MfaConfigAssembler {

    MfaConfig toDomain(MfaConfigEntity entity);

    MfaConfigEntity toEntity(MfaConfig model);
}
