package com.pm.passwordmanager.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import com.pm.passwordmanager.domain.command.UnlockVaultCommand;
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
import net.jqwik.api.lifecycle.BeforeProperty;

/**
 * Property 3: 连续失败锁定
 * For any 用户账户，连续输入 5 次错误主密码后，账户应进入锁定状态且锁定时长为 15 分钟；
 * 在锁定期间，即使输入正确密码也应拒绝解锁。
 *
 * Validates: Requirements 1.5
 */
@Label("Feature: password-manager, Property 3: 连续失败锁定")
class AccountLockoutPropertyTest {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private UserRepository userRepository;
    private Argon2Hasher argon2Hasher;
    private EncryptionEngine encryptionEngine;
    private SessionService sessionService;
    private MfaService mfaService;
    private AuthServiceImpl authService;

    private final byte[] testSalt = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    private final String encodedSalt = Base64.getEncoder().encodeToString(testSalt);
    private final String testPasswordHash = "testHash";
    // 12 bytes IV + 2 bytes ciphertext
    private final byte[] testEncryptedDek = new byte[14];

    @BeforeProperty
    void setUp() {
        userRepository = mock(UserRepository.class);
        argon2Hasher = mock(Argon2Hasher.class);
        encryptionEngine = mock(EncryptionEngine.class);
        sessionService = mock(SessionService.class);
        mfaService = mock(MfaService.class);
        authService = new AuthServiceImpl(userRepository, argon2Hasher, encryptionEngine, sessionService, mfaService);
    }

    /**
     * 连续 5 次错误密码后，第 5 次失败应触发锁定，lockedUntil 应设置为约 15 分钟后。
     *
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 100)
    @Label("should_lockAccount_when_fiveConsecutiveWrongPasswords")
    void should_lockAccount_when_fiveConsecutiveWrongPasswords(
            @ForAll("initialFailedAttempts") int initialFailures
    ) {
        User user = User.builder()
                .id(1L)
                .masterPasswordHash(testPasswordHash)
                .salt(encodedSalt)
                .encryptionKeyEncrypted(testEncryptedDek)
                .failedAttempts(initialFailures)
                .lockedUntil(null)
                .autoLockMinutes(5)
                .build();

        when(userRepository.findFirst()).thenAnswer(inv -> Optional.of(user));
        when(argon2Hasher.verify(any(), any(), any())).thenReturn(false);

        int attemptsNeeded = MAX_FAILED_ATTEMPTS - initialFailures;

        for (int i = 0; i < attemptsNeeded; i++) {
            UnlockVaultCommand request = UnlockVaultCommand.builder()
                    .masterPassword("WrongPassword" + i)
                    .build();

            assertThatThrownBy(() -> authService.unlock(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.MASTER_PASSWORD_WRONG));
        }

        assertThat(user.getFailedAttempts()).isEqualTo(MAX_FAILED_ATTEMPTS);
        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES - 1));
        assertThat(user.getLockedUntil()).isBefore(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES + 1));
    }

    /**
     * 在锁定期间，即使输入正确密码也应被拒绝（返回 ACCOUNT_LOCKED）。
     *
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 100)
    @Label("should_rejectCorrectPassword_when_accountIsLocked")
    void should_rejectCorrectPassword_when_accountIsLocked(
            @ForAll("lockoutRemainingMinutes") int remainingMinutes
    ) {
        User user = User.builder()
                .id(1L)
                .masterPasswordHash(testPasswordHash)
                .salt(encodedSalt)
                .encryptionKeyEncrypted(testEncryptedDek)
                .failedAttempts(MAX_FAILED_ATTEMPTS)
                .lockedUntil(LocalDateTime.now().plusMinutes(remainingMinutes))
                .autoLockMinutes(5)
                .build();

        when(userRepository.findFirst()).thenReturn(Optional.of(user));

        UnlockVaultCommand request = UnlockVaultCommand.builder()
                .masterPassword("CorrectPassword123!")
                .build();

        assertThatThrownBy(() -> authService.unlock(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_LOCKED));
    }

    /**
     * 少于 5 次连续失败不应触发锁定。
     *
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 100)
    @Label("should_notLockAccount_when_fewerThanFiveFailures")
    void should_notLockAccount_when_fewerThanFiveFailures(
            @ForAll("subThresholdFailures") int totalFailures
    ) {
        User user = User.builder()
                .id(1L)
                .masterPasswordHash(testPasswordHash)
                .salt(encodedSalt)
                .encryptionKeyEncrypted(testEncryptedDek)
                .failedAttempts(0)
                .lockedUntil(null)
                .autoLockMinutes(5)
                .build();

        when(userRepository.findFirst()).thenAnswer(inv -> Optional.of(user));
        when(argon2Hasher.verify(any(), any(), any())).thenReturn(false);

        for (int i = 0; i < totalFailures; i++) {
            UnlockVaultCommand request = UnlockVaultCommand.builder()
                    .masterPassword("WrongPassword" + i)
                    .build();

            assertThatThrownBy(() -> authService.unlock(request))
                    .isInstanceOf(BusinessException.class);
        }

        assertThat(user.getFailedAttempts()).isEqualTo(totalFailures);
        assertThat(user.getLockedUntil()).isNull();
    }

    /**
     * 正确密码应重置失败计数，中断连续失败序列。
     *
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 100)
    @Label("should_resetFailedAttempts_when_correctPasswordBeforeLockout")
    void should_resetFailedAttempts_when_correctPasswordBeforeLockout(
            @ForAll("subThresholdFailures") int failuresBefore
    ) {
        byte[] testDek = new byte[32];
        byte[] testKek = new byte[32];

        User user = User.builder()
                .id(1L)
                .masterPasswordHash(testPasswordHash)
                .salt(encodedSalt)
                .encryptionKeyEncrypted(testEncryptedDek)
                .failedAttempts(failuresBefore)
                .lockedUntil(null)
                .autoLockMinutes(5)
                .build();

        when(userRepository.findFirst()).thenReturn(Optional.of(user));
        when(argon2Hasher.verify(any(), any(), any())).thenReturn(true);
        when(argon2Hasher.deriveKey(any(), any(), anyInt())).thenReturn(testKek);
        when(encryptionEngine.decrypt(any(EncryptedData.class), any())).thenReturn(testDek);

        UnlockVaultCommand request = UnlockVaultCommand.builder()
                .masterPassword("CorrectPassword123!")
                .build();

        authService.unlock(request);

        assertThat(user.getFailedAttempts()).isEqualTo(0);
        assertThat(user.getLockedUntil()).isNull();
    }

    // --- Providers ---

    @Provide
    Arbitrary<Integer> initialFailedAttempts() {
        return Arbitraries.integers().between(0, MAX_FAILED_ATTEMPTS - 1);
    }

    @Provide
    Arbitrary<Integer> lockoutRemainingMinutes() {
        return Arbitraries.integers().between(1, LOCKOUT_MINUTES);
    }

    @Provide
    Arbitrary<Integer> subThresholdFailures() {
        return Arbitraries.integers().between(1, MAX_FAILED_ATTEMPTS - 1);
    }
}
