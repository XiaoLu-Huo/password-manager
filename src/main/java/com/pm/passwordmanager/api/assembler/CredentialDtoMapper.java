package com.pm.passwordmanager.api.assembler;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.pm.passwordmanager.api.dto.request.CreateCredentialRequest;
import com.pm.passwordmanager.api.dto.request.UpdateCredentialRequest;
import com.pm.passwordmanager.api.dto.response.CredentialListResponse;
import com.pm.passwordmanager.api.dto.response.CredentialResponse;
import com.pm.passwordmanager.domain.command.CreateCredentialCommand;
import com.pm.passwordmanager.domain.command.UpdateCredentialCommand;
import com.pm.passwordmanager.domain.model.Credential;

/**
 * 凭证 DTO 映射器。负责 Request → Command、Model → Response 的转换。
 */
@Mapper(componentModel = "spring")
public interface CredentialDtoMapper {

    CreateCredentialCommand toCommand(CreateCredentialRequest request);

    UpdateCredentialCommand toCommand(UpdateCredentialRequest request);

    @Mapping(target = "maskedPassword", constant = "••••••")
    CredentialResponse toResponse(Credential credential);

    CredentialListResponse toListResponse(Credential credential);
}
