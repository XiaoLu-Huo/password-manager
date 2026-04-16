package com.pm.passwordmanager.api.assembler;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.pm.passwordmanager.api.dto.response.PasswordHistoryResponse;
import com.pm.passwordmanager.infrastructure.persistence.entity.PasswordHistoryEntity;

/**
 * 密码历史 DTO 映射器。负责 Entity → Response 的转换。
 */
@Mapper(componentModel = "spring")
public interface PasswordHistoryDtoMapper {

    @Mapping(target = "maskedPassword", constant = "••••••")
    @Mapping(source = "createdAt", target = "changedAt")
    PasswordHistoryResponse toResponse(PasswordHistoryEntity entity);
}
