package com.pm.passwordmanager.api.assembler;

import org.mapstruct.Mapper;

import com.pm.passwordmanager.api.dto.request.GeneratePasswordRequest;
import com.pm.passwordmanager.api.dto.response.PasswordRuleResponse;
import com.pm.passwordmanager.domain.command.GeneratePasswordCommand;
import com.pm.passwordmanager.domain.model.PasswordRule;

/**
 * 密码生成器 DTO 映射器。负责 Request → Command、Model → Response 的转换。
 */
@Mapper(componentModel = "spring")
public interface PasswordGenDtoMapper {

    GeneratePasswordCommand toCommand(GeneratePasswordRequest request);

    PasswordRuleResponse toResponse(PasswordRule rule);
}
