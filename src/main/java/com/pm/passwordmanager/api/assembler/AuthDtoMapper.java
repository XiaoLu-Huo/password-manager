package com.pm.passwordmanager.api.assembler;

import org.mapstruct.Mapper;

import com.pm.passwordmanager.api.dto.request.LoginRequest;
import com.pm.passwordmanager.api.dto.request.RegisterRequest;
import com.pm.passwordmanager.domain.command.LoginCommand;
import com.pm.passwordmanager.domain.command.RegisterCommand;

/**
 * 认证 DTO 映射器。负责 Request → Command 的转换。
 */
@Mapper(componentModel = "spring")
public interface AuthDtoMapper {

    RegisterCommand toCommand(RegisterRequest request);

    LoginCommand toCommand(LoginRequest request);
}
