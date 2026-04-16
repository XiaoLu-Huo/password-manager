package com.pm.passwordmanager.infrastructure.persistence.assembler;

import org.mapstruct.Mapper;

import com.pm.passwordmanager.domain.model.User;
import com.pm.passwordmanager.infrastructure.persistence.entity.UserEntity;

/**
 * 用户 Entity ↔ Domain Model 转换器。
 * 由 UserRepositoryImpl 使用，隔离持久化层与领域层。
 */
@Mapper(componentModel = "spring")
public interface UserAssembler {

    User toDomain(UserEntity entity);

    UserEntity toEntity(User model);
}
