package com.pm.passwordmanager.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Base64;

import com.pm.passwordmanager.dto.request.UnlockVaultRequest;
import com.pm.passwordmanager.entity.UserEntity;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.mapper.UserMapper;
import com.pm.passwordmanager.service.MfaService;
import com.pm.passwordmanager.service.SessionService;
import com.pm.passwordmanager.util.Argon2Hasher;
import com.pm.passwordmanager.util.EncryptedData;
import com.pm.passwordmanager.util.EncryptionEngine;

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

    private UserMapper userMapper;
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
        userMapper = mock(UserMapper.class);
        argon2Hasher = mock(Argon2Hasher.class);
        encryptionEngine = mock(EncryptionEngine.class);
        sessionService = mock(SessionService.class);
        mfaService = mock(MfaService.class);
        authService = new AuthServiceImpl(userMapper, argon2Hasher, encryptionEngine, sessionService, mfaService);
    }

    /**
     * 连续 5 次错误密码后，第 5 次失败应触发锁定，lockedUntil 应设置为约 15 分钟后。
     * 使用随机初始失败次数（0-4）模拟不同起始状态，累计到 5 次后验证锁定。
     *
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 100)
    @Label("should_lockAccount_when_fiveConsecutiveWrongPasswords")
    void should_lockAccount_when_fiveConsecutiveWrongPasswords(
            @ForAll("initialFailedAttempts") int initialFailures
    ) {
        // Track the user entity state across multiple unlock attempts
        UserEntity user = UserEntity.builder()
                .id(1L)
                .masterPasswordHash(testPasswordHash)
                .salt(encodedSalt)
                .encryptionKeyEncrypted(testEncryptedDek)
                .failedAttempts(initialFailures)
                .lockedUntil(null)
                .autoLockMinutes(5)
                .build();

        // Mock: always return the current user state, always fail password verification
        when(userMapper.selectOne(any())).thenAnswer(inv -> user);
        when(argon2Hasher.verify(any(), any(), any())).thenReturn(false);
        // Capture updates to the user entity
        when(userMapper.updateById(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity updated = inv.getArgument(0);
            user.setFailedAttempts(updated.getFailedAttempts());
            user.setLockedUntil(updated.getLockedUntil());
            return 1;
        });

        int attemptsNeeded = MAX_FAILED_ATTEMPTS - initialFailures;

        for (int i = 0; i < attemptsNeeded; i++) {
            UnlockVaultRequest request = UnlockVaultRequest.builder()
                    .masterPassword("WrongPassword" + i)
                    .build();

            assertThatThrownBy(() -> authService.unlock(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.MASTER_PASSWORD_WRONG));
        }

        // After exactly MAX_FAILED_ATTEMPTS total failures, account should be locked
        assertThat(user.getFailedAttempts()).isEqualTo(MAX_FAILED_ATTEMPTS);
        assertThat(user.getLockedUntil()).isNotNull();
        // Lockout should be approximately 15 minutes from now
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
        UserEntity user = UserEntity.builder()
                .id(1L)
                .masterPasswordHash(testPasswordHash)
                .salt(encodedSalt)
                .encryptionKeyEncrypted(testEncryptedDek)
                .failedAttempts(MAX_FAILED_ATTEMPTS)
                .lockedUntil(LocalDateTime.now().plusMinutes(remainingMinutes))
                .autoLockMinutes(5)
                .build();

        when(userMapper.selectOne(any())).thenReturn(user);

        UnlockVaultRequest request = UnlockVaultRequest.builder()
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
        UserEntity user = UserEntity.builder()
                .id(1L)
                .masterPasswordHash(testPasswordHash)
                .salt(encodedSalt)
                .encryptionKeyEncrypted(testEncryptedDek)
                .failedAttempts(0)
                .lockedUntil(null)
                .autoLockMinutes(5)
                .build();

        when(userMapper.selectOne(any())).thenAnswer(inv -> user);
        when(argon2Hasher.verify(any(), any(), any())).thenReturn(false);
        when(userMapper.updateById(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity updated = inv.getArgument(0);
            user.setFailedAttempts(updated.getFailedAttempts());
            user.setLockedUntil(updated.getLockedUntil());
            return 1;
        });

        for (int i = 0; i < totalFailures; i++) {
            UnlockVaultRequest request = UnlockVaultRequest.builder()
                    .masterPassword("WrongPassword" + i)
                    .build();

            assertThatThrownBy(() -> authService.unlock(request))
                    .isInstanceOf(BusinessException.class);
        }

        // Should NOT be locked
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

        UserEntity user = UserEntity.builder()
                .id(1L)
                .masterPasswordHash(testPasswordHash)
                .salt(encodedSalt)
                .encryptionKeyEncrypted(testEncryptedDek)
                .failedAttempts(failuresBefore)
                .lockedUntil(null)
                .autoLockMinutes(5)
                .build();

        when(userMapper.selectOne(any())).thenReturn(user);
        when(argon2Hasher.verify(any(), any(), any())).thenReturn(true);
        when(argon2Hasher.deriveKey(any(), any(), anyInt())).thenReturn(testKek);
        when(encryptionEngine.decrypt(any(EncryptedData.class), any())).thenReturn(testDek);
        when(userMapper.updateById(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity updated = inv.getArgument(0);
            user.setFailedAttempts(updated.getFailedAttempts());
            user.setLockedUntil(updated.getLockedUntil());
            return 1;
        });

        UnlockVaultRequest request = UnlockVaultRequest.builder()
                .masterPassword("CorrectPassword123!")
                .build();

        authService.unlock(request);

        // Failed attempts should be reset to 0
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
