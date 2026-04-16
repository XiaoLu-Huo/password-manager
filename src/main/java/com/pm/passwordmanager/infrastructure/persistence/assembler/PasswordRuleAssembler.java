package com.pm.passwordmanager.infrastructure.persistence.assembler;

import org.mapstruct.Mapper;

import com.pm.passwordmanager.domain.model.PasswordRule;
import com.pm.passwordmanager.infrastructure.persistence.entity.PasswordRuleEntity;

/**
 * 密码规则 Entity ↔ Domain Model 转换器。
 * 由 PasswordRuleRepositoryImpl 使用，隔离持久化层与领域层。
 */
@Mapper(componentModel = "spring")
public interface PasswordRuleAssembler {

    PasswordRule toDomain(PasswordRuleEntity entity);

    PasswordRuleEntity toEntity(PasswordRule model);
}
