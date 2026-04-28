package com.pm.passwordmanager.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.Optional;

import com.pm.passwordmanager.domain.command.LoginCommand;
import com.pm.passwordmanager.api.dto.response.UnlockResultResponse;
import com.pm.passwordmanager.domain.model.User;
import com.pm.passwordmanager.domain.repository.UserRepository;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.domain.service.MfaService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.infrastructure.encryption.Argon2Hasher;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeTry;

/**
 * Property 4: MFA 双因素强制
 * For any 已启用 MFA 的用户，仅提供正确的主密码而不提供有效的 TOTP 验证码时，
 * 密码库不应被解锁；同时提供正确主密码和有效 TOTP 验证码时方可解锁。
 *
 * Validates: Requirements 1.10, 1.11
 */
@Label("Feature: password-manager, Property 4: MFA 双因素强制")
class MfaEnforcementPropertyTest {

    private static final String TEST_USERNAME = "testuser";

    private UserRepository userRepository;
    private Argon2Hasher argon2Hasher;
    private EncryptionEngine encryptionEngine;
    private SessionService sessionService;
    private MfaService mfaService;
    private AuthServiceImpl authService;

    private final byte[] testSalt = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    private final String encodedSalt = Base64.getEncoder().encodeToString(testSalt);
    private final String testPasswordHash = "testHash";
    private final byte[] testDek = new byte[32];
    private final byte[] testKek = new byte[32];
    private final byte[] testEncryptedDek = new byte[14];

    @BeforeTry
    void setUp() {
        userRepository = mock(UserRepository.class);
        argon2Hasher = mock(Argon2Hasher.class);
        encryptionEngine = mock(EncryptionEngine.class);
        sessionService = mock(SessionService.class);
        mfaService = mock(MfaService.class);
        authService = new AuthServiceImpl(userRepository, argon2Hasher, encryptionEngine, sessionService, mfaService);
    }

    private User buildUser() {
        return User.builder()
                .id(1L)
                .username(TEST_USERNAME)
                .masterPasswordHash(testPasswordHash)
                .salt(encodedSalt)
                .encryptionKeyEncrypted(testEncryptedDek)
                .failedAttempts(0)
                .lockedUntil(null)
                .autoLockMinutes(5)
                .build();
    }

    private void stubCorrectPassword() {
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(buildUser()));
        when(argon2Hasher.verify(any(), any(), any())).thenReturn(true);
        when(argon2Hasher.deriveKey(any(), any(), anyInt())).thenReturn(testKek);
        when(encryptionEngine.decrypt(any(EncryptedData.class), any())).thenReturn(testDek);
    }

    @Property(tries = 100)
    @Label("should_requireTotp_when_mfaEnabledAndOnlyMasterPasswordProvided")
    void should_requireTotp_when_mfaEnabledAndOnlyMasterPasswordProvided(
            @ForAll("validPasswords") String masterPassword
    ) {
        stubCorrectPassword();
        when(mfaService.isMfaEnabled(anyLong())).thenReturn(true);

        LoginCommand request = LoginCommand.builder()
                .identifier(TEST_USERNAME)
                .masterPassword(masterPassword)
                .build();

        UnlockResultResponse result = authService.login(request);

        assertThat(result.isMfaRequired()).isTrue();
        verify(sessionService, never()).storeDek(anyLong(), any());
    }

    @Property(tries = 100)
    @Label("should_rejectUnlock_when_mfaEnabledAndTotpCodeInvalid")
    void should_rejectUnlock_when_mfaEnabledAndTotpCodeInvalid(
            @ForAll("validPasswords") String masterPassword,
            @ForAll("invalidTotpCodes") String invalidTotpCode
    ) {
        stubCorrectPassword();
        when(mfaService.isMfaEnabled(anyLong())).thenReturn(true);
        when(mfaService.verifyTotp(anyLong(), anyString())).thenReturn(false);

        LoginCommand request = LoginCommand.builder()
                .identifier(TEST_USERNAME)
                .masterPassword(masterPassword)
                .build();
        UnlockResultResponse loginResult = authService.login(request);
        assertThat(loginResult.isMfaRequired()).isTrue();

        // Use the mfaToken returned from login for TOTP verification
        String mfaToken = loginResult.getSessionToken();
        assertThatThrownBy(() -> authService.verifyTotpAndUnlock(mfaToken, invalidTotpCode))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TOTP_INVALID));
    }

    @Property(tries = 100)
    @Label("should_unlockVault_when_mfaEnabledAndBothPasswordAndTotpValid")
    void should_unlockVault_when_mfaEnabledAndBothPasswordAndTotpValid(
            @ForAll("validPasswords") String masterPassword,
            @ForAll("validTotpCodes") String validTotpCode
    ) {
        stubCorrectPassword();
        when(mfaService.isMfaEnabled(anyLong())).thenReturn(true);
        when(mfaService.verifyTotp(anyLong(), eq(validTotpCode))).thenReturn(true);

        LoginCommand request = LoginCommand.builder()
                .identifier(TEST_USERNAME)
                .masterPassword(masterPassword)
                .build();
        UnlockResultResponse loginResult = authService.login(request);
        assertThat(loginResult.isMfaRequired()).isTrue();

        String mfaToken = loginResult.getSessionToken();
        UnlockResultResponse totpResult = authService.verifyTotpAndUnlock(mfaToken, validTotpCode);
        assertThat(totpResult.isMfaRequired()).isFalse();

        verify(sessionService).storeDek(eq(1L), any());
    }

    @Property(tries = 100)
    @Label("should_unlockDirectly_when_mfaNotEnabledAndPasswordCorrect")
    void should_unlockDirectly_when_mfaNotEnabledAndPasswordCorrect(
            @ForAll("validPasswords") String masterPassword
    ) {
        stubCorrectPassword();
        when(mfaService.isMfaEnabled(anyLong())).thenReturn(false);

        LoginCommand request = LoginCommand.builder()
                .identifier(TEST_USERNAME)
                .masterPassword(masterPassword)
                .build();

        UnlockResultResponse result = authService.login(request);

        assertThat(result.isMfaRequired()).isFalse();
        verify(sessionService).storeDek(eq(1L), any());
    }

    @Provide
    Arbitrary<String> validPasswords() {
        return Arbitraries.strings().ascii().ofMinLength(12).ofMaxLength(64)
                .filter(s -> countCharacterTypes(s) >= 3);
    }

    @Provide
    Arbitrary<String> validTotpCodes() {
        return Arbitraries.integers().between(0, 999999)
                .map(i -> String.format("%06d", i));
    }

    @Provide
    Arbitrary<String> invalidTotpCodes() {
        return Arbitraries.oneOf(
                Arbitraries.strings().numeric().ofMinLength(1).ofMaxLength(5),
                Arbitraries.strings().numeric().ofMinLength(7).ofMaxLength(10),
                Arbitraries.strings().alpha().ofLength(6)
        );
    }

    private int countCharacterTypes(String password) {
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        return (hasUpper ? 1 : 0) + (hasLower ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
    }
}
