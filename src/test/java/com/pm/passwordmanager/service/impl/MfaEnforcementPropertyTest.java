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

import com.pm.passwordmanager.api.dto.request.UnlockVaultRequest;
import com.pm.passwordmanager.api.dto.response.UnlockResultResponse;
import com.pm.passwordmanager.infrastructure.persistence.entity.UserEntity;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.persistence.mapper.UserMapper;
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

    private UserMapper userMapper;
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
    // 12 bytes IV + 2 bytes ciphertext
    private final byte[] testEncryptedDek = new byte[14];

    @BeforeTry
    void setUp() {
        userMapper = mock(UserMapper.class);
        argon2Hasher = mock(Argon2Hasher.class);
        encryptionEngine = mock(EncryptionEngine.class);
        sessionService = mock(SessionService.class);
        mfaService = mock(MfaService.class);
        authService = new AuthServiceImpl(userMapper, argon2Hasher, encryptionEngine, sessionService, mfaService);
    }

    private UserEntity buildUser() {
        return UserEntity.builder()
                .id(1L)
                .masterPasswordHash(testPasswordHash)
                .salt(encodedSalt)
                .encryptionKeyEncrypted(testEncryptedDek)
                .failedAttempts(0)
                .lockedUntil(null)
                .autoLockMinutes(5)
                .build();
    }

    private void stubCorrectPassword() {
        when(userMapper.selectOne(any())).thenReturn(buildUser());
        when(argon2Hasher.verify(any(), any(), any())).thenReturn(true);
        when(argon2Hasher.deriveKey(any(), any(), anyInt())).thenReturn(testKek);
        when(encryptionEngine.decrypt(any(EncryptedData.class), any())).thenReturn(testDek);
        when(userMapper.updateById(any(UserEntity.class))).thenReturn(1);
    }

    /**
     * 启用 MFA 后，仅提供正确主密码应返回 mfaRequired=true，
     * DEK 不应存入会话（密码库未解锁）。
     *
     * **Validates: Requirements 1.10**
     */
    @Property(tries = 100)
    @Label("should_requireTotp_when_mfaEnabledAndOnlyMasterPasswordProvided")
    void should_requireTotp_when_mfaEnabledAndOnlyMasterPasswordProvided(
            @ForAll("validPasswords") String masterPassword
    ) {
        stubCorrectPassword();
        when(mfaService.isMfaEnabled(anyLong())).thenReturn(true);

        UnlockVaultRequest request = UnlockVaultRequest.builder()
                .masterPassword(masterPassword)
                .build();

        UnlockResultResponse result = authService.unlock(request);

        // Should indicate MFA is required
        assertThat(result.isMfaRequired()).isTrue();
        // DEK should NOT be stored in session yet
        verify(sessionService, never()).storeDek(anyLong(), any());
    }

    /**
     * 启用 MFA 后，提供正确主密码 + 无效 TOTP 码应拒绝解锁。
     *
     * **Validates: Requirements 1.11**
     */
    @Property(tries = 100)
    @Label("should_rejectUnlock_when_mfaEnabledAndTotpCodeInvalid")
    void should_rejectUnlock_when_mfaEnabledAndTotpCodeInvalid(
            @ForAll("validPasswords") String masterPassword,
            @ForAll("invalidTotpCodes") String invalidTotpCode
    ) {
        stubCorrectPassword();
        when(mfaService.isMfaEnabled(anyLong())).thenReturn(true);
        when(mfaService.verifyTotp(anyLong(), anyString())).thenReturn(false);

        // Step 1: unlock with correct password → mfaRequired
        UnlockVaultRequest request = UnlockVaultRequest.builder()
                .masterPassword(masterPassword)
                .build();
        UnlockResultResponse unlockResult = authService.unlock(request);
        assertThat(unlockResult.isMfaRequired()).isTrue();

        // Step 2: verify with invalid TOTP → should fail
        assertThatThrownBy(() -> authService.verifyTotpAndUnlock(invalidTotpCode))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TOTP_INVALID));
    }

    /**
     * 启用 MFA 后，提供正确主密码 + 有效 TOTP 码应成功解锁。
     *
     * **Validates: Requirements 1.10, 1.11**
     */
    @Property(tries = 100)
    @Label("should_unlockVault_when_mfaEnabledAndBothPasswordAndTotpValid")
    void should_unlockVault_when_mfaEnabledAndBothPasswordAndTotpValid(
            @ForAll("validPasswords") String masterPassword,
            @ForAll("validTotpCodes") String validTotpCode
    ) {
        stubCorrectPassword();
        when(mfaService.isMfaEnabled(anyLong())).thenReturn(true);
        when(mfaService.verifyTotp(anyLong(), eq(validTotpCode))).thenReturn(true);

        // Step 1: unlock with correct password → mfaRequired
        UnlockVaultRequest request = UnlockVaultRequest.builder()
                .masterPassword(masterPassword)
                .build();
        UnlockResultResponse unlockResult = authService.unlock(request);
        assertThat(unlockResult.isMfaRequired()).isTrue();

        // Step 2: verify with valid TOTP → should succeed
        UnlockResultResponse totpResult = authService.verifyTotpAndUnlock(validTotpCode);
        assertThat(totpResult.isMfaRequired()).isFalse();

        // DEK should now be stored in session
        verify(sessionService).storeDek(eq(1L), any());
    }

    /**
     * MFA 未启用时，仅提供正确主密码即可解锁，mfaRequired 应为 false。
     *
     * **Validates: Requirements 1.10 (反向验证)**
     */
    @Property(tries = 100)
    @Label("should_unlockDirectly_when_mfaNotEnabledAndPasswordCorrect")
    void should_unlockDirectly_when_mfaNotEnabledAndPasswordCorrect(
            @ForAll("validPasswords") String masterPassword
    ) {
        stubCorrectPassword();
        when(mfaService.isMfaEnabled(anyLong())).thenReturn(false);

        UnlockVaultRequest request = UnlockVaultRequest.builder()
                .masterPassword(masterPassword)
                .build();

        UnlockResultResponse result = authService.unlock(request);

        // Should unlock directly without MFA
        assertThat(result.isMfaRequired()).isFalse();
        // DEK should be stored in session immediately
        verify(sessionService).storeDek(eq(1L), any());
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> validPasswords() {
        return Arbitraries.strings().ascii().ofMinLength(12).ofMaxLength(64)
                .filter(s -> countCharacterTypes(s) >= 3);
    }

    @Provide
    Arbitrary<String> validTotpCodes() {
        // 6-digit numeric codes
        return Arbitraries.integers().between(0, 999999)
                .map(i -> String.format("%06d", i));
    }

    @Provide
    Arbitrary<String> invalidTotpCodes() {
        return Arbitraries.oneOf(
                // Wrong length codes
                Arbitraries.strings().numeric().ofMinLength(1).ofMaxLength(5),
                Arbitraries.strings().numeric().ofMinLength(7).ofMaxLength(10),
                // Non-numeric codes
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
