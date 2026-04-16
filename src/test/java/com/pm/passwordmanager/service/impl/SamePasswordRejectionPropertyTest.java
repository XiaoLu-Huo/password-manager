package com.pm.passwordmanager.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pm.passwordmanager.domain.command.UpdateCredentialCommand;
import com.pm.passwordmanager.domain.model.Credential;
import com.pm.passwordmanager.domain.repository.CredentialRepository;
import com.pm.passwordmanager.domain.service.PasswordGeneratorService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.domain.service.impl.CredentialServiceImpl;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;
import com.pm.passwordmanager.infrastructure.persistence.mapper.PasswordHistoryMapper;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property 12: 新旧密码不同验证
 * Validates: Requirements 5.5
 */
@Label("Feature: password-manager, Property 12: 新旧密码不同验证")
class SamePasswordRejectionPropertyTest {

    private static final Long USER_ID = 1L;
    private static final Long CREDENTIAL_ID = 100L;
    private static final byte[] FAKE_DEK = new byte[32];

    private final CredentialRepository credentialRepository = mock(CredentialRepository.class);
    private final PasswordHistoryMapper passwordHistoryMapper = mock(PasswordHistoryMapper.class);
    private final EncryptionEngine encryptionEngine = mock(EncryptionEngine.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final PasswordGeneratorService passwordGeneratorService = mock(PasswordGeneratorService.class);

    private final CredentialServiceImpl service = new CredentialServiceImpl(
            credentialRepository, passwordHistoryMapper, encryptionEngine, sessionService, passwordGeneratorService, null);

    SamePasswordRejectionPropertyTest() {
        when(sessionService.getDek(USER_ID)).thenReturn(FAKE_DEK);
        when(sessionService.isSessionActive(USER_ID)).thenReturn(true);
    }

    @Property(tries = 100)
    @Label("should_rejectUpdate_when_newPasswordSameAsCurrent")
    void should_rejectUpdate_when_newPasswordSameAsCurrent(
            @ForAll("nonBlankPasswords") String currentPassword
    ) {
        byte[] encryptedBytes = ("enc_" + currentPassword).getBytes(StandardCharsets.UTF_8);
        byte[] iv = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};

        Credential credential = Credential.builder()
                .id(CREDENTIAL_ID).userId(USER_ID)
                .accountName("TestAccount").username("testuser")
                .passwordEncrypted(encryptedBytes).iv(iv)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        when(credentialRepository.findById(CREDENTIAL_ID)).thenReturn(Optional.of(credential));
        when(encryptionEngine.decrypt(any(EncryptedData.class), eq(FAKE_DEK)))
                .thenReturn(currentPassword.getBytes(StandardCharsets.UTF_8));

        UpdateCredentialCommand command = UpdateCredentialCommand.builder()
                .password(currentPassword).build();

        assertThatThrownBy(() -> service.updateCredential(USER_ID, CREDENTIAL_ID, command))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SAME_PASSWORD));
    }

    @Property(tries = 100)
    @Label("should_acceptUpdate_when_newPasswordDifferentFromCurrent")
    void should_acceptUpdate_when_newPasswordDifferentFromCurrent(
            @ForAll("distinctPasswordPairs") String[] passwords
    ) {
        String currentPassword = passwords[0];
        String newPassword = passwords[1];

        byte[] encryptedBytes = ("enc_" + currentPassword).getBytes(StandardCharsets.UTF_8);
        byte[] iv = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};

        Credential credential = Credential.builder()
                .id(CREDENTIAL_ID).userId(USER_ID)
                .accountName("TestAccount").username("testuser")
                .passwordEncrypted(encryptedBytes).iv(iv)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        when(credentialRepository.findById(CREDENTIAL_ID)).thenReturn(Optional.of(credential));
        when(encryptionEngine.decrypt(any(EncryptedData.class), eq(FAKE_DEK)))
                .thenReturn(currentPassword.getBytes(StandardCharsets.UTF_8));
        when(encryptionEngine.encrypt(any(byte[].class), eq(FAKE_DEK)))
                .thenReturn(new EncryptedData(new byte[]{10, 20, 30}, new byte[]{4, 5, 6}));
        when(passwordHistoryMapper.insert(any())).thenReturn(1);
        when(passwordHistoryMapper.selectCount(any())).thenReturn(1L);

        UpdateCredentialCommand command = UpdateCredentialCommand.builder()
                .password(newPassword).build();

        Credential result = service.updateCredential(USER_ID, CREDENTIAL_ID, command);
        assertThat(result).isNotNull();
        assertThat(result.getAccountName()).isEqualTo("TestAccount");
    }

    @Provide
    Arbitrary<String> nonBlankPasswords() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(64).alpha().numeric();
    }

    @Provide
    Arbitrary<String[]> distinctPasswordPairs() {
        return nonBlankPasswords().flatMap(current ->
                nonBlankPasswords()
                        .filter(newPwd -> !newPwd.equals(current))
                        .map(newPwd -> new String[]{current, newPwd}));
    }
}
