package com.pm.passwordmanager.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pm.passwordmanager.domain.assembler.CredentialModelAssembler;
import com.pm.passwordmanager.domain.command.CreateCredentialCommand;
import com.pm.passwordmanager.domain.model.Credential;
import com.pm.passwordmanager.domain.repository.CredentialRepository;
import com.pm.passwordmanager.domain.service.PasswordGeneratorService;
import com.pm.passwordmanager.domain.service.PasswordHistoryService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.domain.service.impl.CredentialServiceImpl;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property 7: 凭证必填字段验证
 * Validates: Requirements 3.1, 3.6
 */
@Label("Feature: password-manager, Property 7: 凭证必填字段验证")
class CredentialValidationPropertyTest {

    private static final Long USER_ID = 1L;
    private static final byte[] FAKE_DEK = new byte[32];

    private final CredentialRepository credentialRepository = mock(CredentialRepository.class);
    private final PasswordHistoryService passwordHistoryService = mock(PasswordHistoryService.class);
    private final EncryptionEngine encryptionEngine = mock(EncryptionEngine.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final PasswordGeneratorService passwordGeneratorService = mock(PasswordGeneratorService.class);
    private final CredentialModelAssembler credentialModelAssembler = mock(CredentialModelAssembler.class);

    private final CredentialServiceImpl service = new CredentialServiceImpl(
            credentialRepository, passwordHistoryService, encryptionEngine,
            sessionService, passwordGeneratorService, credentialModelAssembler);

    CredentialValidationPropertyTest() {
        when(sessionService.getDek(USER_ID)).thenReturn(FAKE_DEK);
        when(sessionService.isSessionActive(USER_ID)).thenReturn(true);
        when(encryptionEngine.encrypt(any(byte[].class), eq(FAKE_DEK)))
                .thenReturn(new EncryptedData(new byte[]{1, 2, 3}, new byte[]{4, 5, 6}));
        when(credentialRepository.save(any(Credential.class))).thenAnswer(inv -> {
            Credential c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });
        when(credentialModelAssembler.toModel(any(CreateCredentialCommand.class), eq(USER_ID)))
                .thenAnswer(inv -> {
                    CreateCredentialCommand cmd = inv.getArgument(0);
                    return Credential.builder()
                            .userId(USER_ID)
                            .accountName(cmd.getAccountName())
                            .username(cmd.getUsername())
                            .url(cmd.getUrl())
                            .notes(cmd.getNotes())
                            .tags(cmd.getTags())
                            .build();
                });
    }

    @Property(tries = 100)
    @Label("should_createSuccessfully_when_allRequiredFieldsPresent")
    void should_createSuccessfully_when_allRequiredFieldsPresent(
            @ForAll("nonBlankStrings") String accountName,
            @ForAll("nonBlankStrings") String username,
            @ForAll("nonBlankStrings") String password
    ) {
        CreateCredentialCommand command = CreateCredentialCommand.builder()
                .accountName(accountName).username(username).password(password).build();

        Credential result = service.createCredential(USER_ID, command);

        assertThat(result).isNotNull();
        assertThat(result.getAccountName()).isEqualTo(accountName);
        assertThat(result.getUsername()).isEqualTo(username);
    }

    @Property(tries = 100)
    @Label("should_rejectCreation_when_anyRequiredFieldMissing")
    void should_rejectCreation_when_anyRequiredFieldMissing(
            @ForAll("commandsWithMissingField") CreateCredentialCommand command
    ) {
        assertThatThrownBy(() -> service.createCredential(USER_ID, command))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CREDENTIAL_REQUIRED_FIELDS_MISSING));
    }

    @Provide
    Arbitrary<String> nonBlankStrings() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(50).alpha().numeric();
    }

    @Provide
    Arbitrary<CreateCredentialCommand> commandsWithMissingField() {
        Arbitrary<String> validStrings = nonBlankStrings();
        Arbitrary<String> blankStrings = Arbitraries.of(null, "", "   ", "\t", "\n");
        Arbitrary<Integer> blankFieldIndex = Arbitraries.integers().between(0, 2);

        return Combinators.combine(validStrings, validStrings, validStrings, blankStrings, blankFieldIndex)
                .as((acct, user, pwd, blank, idx) -> {
                    String accountName = idx == 0 ? blank : acct;
                    String username = idx == 1 ? blank : user;
                    String password = idx == 2 ? blank : pwd;
                    return CreateCredentialCommand.builder()
                            .accountName(accountName).username(username).password(password).build();
                });
    }
}
