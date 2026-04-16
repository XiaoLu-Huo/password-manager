package com.pm.passwordmanager.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pm.passwordmanager.dto.request.UpdateCredentialRequest;
import com.pm.passwordmanager.entity.CredentialEntity;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.mapper.CredentialMapper;
import com.pm.passwordmanager.mapper.PasswordHistoryMapper;
import com.pm.passwordmanager.service.PasswordGeneratorService;
import com.pm.passwordmanager.service.SessionService;
import com.pm.passwordmanager.util.EncryptedData;
import com.pm.passwordmanager.util.EncryptionEngine;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property 12: 新旧密码不同验证
 * 验证：新密码与当前密码相同时更新被拒绝。
 *
 * Validates: Requirements 5.5
 */
@Label("Feature: password-manager, Property 12: 新旧密码不同验证")
class SamePasswordRejectionPropertyTest {

    private static final Long USER_ID = 1L;
    private static final Long CREDENTIAL_ID = 100L;
    private static final byte[] FAKE_DEK = new byte[32];

    private final CredentialMapper credentialMapper = mock(CredentialMapper.class);
    private final PasswordHistoryMapper passwordHistoryMapper = mock(PasswordHistoryMapper.class);
    private final EncryptionEngine encryptionEngine = mock(EncryptionEngine.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final PasswordGeneratorService passwordGeneratorService = mock(PasswordGeneratorService.class);

    private final CredentialServiceImpl service = new CredentialServiceImpl(
            credentialMapper, passwordHistoryMapper, encryptionEngine, sessionService, passwordGeneratorService);

    SamePasswordRejectionPropertyTest() {
        when(sessionService.getDek(USER_ID)).thenReturn(FAKE_DEK);
        when(sessionService.isSessionActive(USER_ID)).thenReturn(true);
    }

    /**
     * 当新密码与当前密码相同时，更新应被拒绝并抛出 SAME_PASSWORD 异常。
     *
     * Validates: Requirements 5.5
     */
    @Property(tries = 100)
    @Label("should_rejectUpdate_when_newPasswordSameAsCurrent")
    void should_rejectUpdate_when_newPasswordSameAsCurrent(
            @ForAll("nonBlankPasswords") String currentPassword
    ) {
        // Arrange: existing credential with currentPassword encrypted
        byte[] encryptedBytes = ("enc_" + currentPassword).getBytes(StandardCharsets.UTF_8);
        byte[] iv = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};

        CredentialEntity entity = CredentialEntity.builder()
                .id(CREDENTIAL_ID)
                .userId(USER_ID)
                .accountName("TestAccount")
                .username("testuser")
                .passwordEncrypted(encryptedBytes)
                .iv(iv)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(credentialMapper.selectById(CREDENTIAL_ID)).thenReturn(entity);
        // Decrypt returns the current password
        when(encryptionEngine.decrypt(any(EncryptedData.class), eq(FAKE_DEK)))
                .thenReturn(currentPassword.getBytes(StandardCharsets.UTF_8));

        // Act & Assert: update with same password should be rejected
        UpdateCredentialRequest request = UpdateCredentialRequest.builder()
                .password(currentPassword)
                .build();

        assertThatThrownBy(() -> service.updateCredential(USER_ID, CREDENTIAL_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SAME_PASSWORD));
    }

    /**
     * 当新密码与当前密码不同时，更新应成功。
     *
     * Validates: Requirements 5.5
     */
    @Property(tries = 100)
    @Label("should_acceptUpdate_when_newPasswordDifferentFromCurrent")
    void should_acceptUpdate_when_newPasswordDifferentFromCurrent(
            @ForAll("distinctPasswordPairs") String[] passwords
    ) {
        String currentPassword = passwords[0];
        String newPassword = passwords[1];

        byte[] encryptedBytes = ("enc_" + currentPassword).getBytes(StandardCharsets.UTF_8);
        byte[] iv = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};

        CredentialEntity entity = CredentialEntity.builder()
                .id(CREDENTIAL_ID)
                .userId(USER_ID)
                .accountName("TestAccount")
                .username("testuser")
                .passwordEncrypted(encryptedBytes)
                .iv(iv)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(credentialMapper.selectById(CREDENTIAL_ID)).thenReturn(entity);
        when(encryptionEngine.decrypt(any(EncryptedData.class), eq(FAKE_DEK)))
                .thenReturn(currentPassword.getBytes(StandardCharsets.UTF_8));
        when(encryptionEngine.encrypt(any(byte[].class), eq(FAKE_DEK)))
                .thenReturn(new EncryptedData(new byte[]{10, 20, 30}, new byte[]{4, 5, 6}));
        when(passwordHistoryMapper.insert(any())).thenReturn(1);
        when(passwordHistoryMapper.selectCount(any())).thenReturn(1L);
        when(credentialMapper.updateById(any())).thenReturn(1);

        UpdateCredentialRequest request = UpdateCredentialRequest.builder()
                .password(newPassword)
                .build();

        var response = service.updateCredential(USER_ID, CREDENTIAL_ID, request);
        assertThat(response).isNotNull();
        assertThat(response.getAccountName()).isEqualTo("TestAccount");
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> nonBlankPasswords() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(64)
                .alpha()
                .numeric();
    }

    @Provide
    Arbitrary<String[]> distinctPasswordPairs() {
        return nonBlankPasswords().flatMap(current ->
                nonBlankPasswords()
                        .filter(newPwd -> !newPwd.equals(current))
                        .map(newPwd -> new String[]{current, newPwd})
        );
    }
}
