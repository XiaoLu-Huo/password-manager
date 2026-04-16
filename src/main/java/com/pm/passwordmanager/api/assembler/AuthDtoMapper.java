package com.pm.passwordmanager.api.assembler;

import org.mapstruct.Mapper;

import com.pm.passwordmanager.api.dto.request.CreateMasterPasswordRequest;
import com.pm.passwordmanager.api.dto.request.UnlockVaultRequest;
import com.pm.passwordmanager.domain.command.SetupMasterPasswordCommand;
import com.pm.passwordmanager.domain.command.UnlockVaultCommand;

/**
 * 认证 DTO 映射器。负责 Request → Command 的转换。
 */
@Mapper(componentModel = "spring")
public interface AuthDtoMapper {

    SetupMasterPasswordCommand toCommand(CreateMasterPasswordRequest request);

    UnlockVaultCommand toCommand(UnlockVaultRequest request);
}
