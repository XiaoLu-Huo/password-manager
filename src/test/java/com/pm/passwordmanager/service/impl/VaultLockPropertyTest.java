package com.pm.passwordmanager.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeTry;

/**
 * Property 19: 密码库锁定后会话清除
 * For any 已解锁的密码库，执行锁定操作后，内存中的 DEK 和所有解密数据应被清除，
 * 且后续凭证访问请求应被拒绝。
 *
 * Validates: Requirements 8.3, 8.5
 */
@Label("Feature: password-manager, Property 19: 密码库锁定后会话清除")
class VaultLockPropertyTest {

    private SessionServiceImpl sessionService;

    @BeforeTry
    void setUp() {
        sessionService = new SessionServiceImpl();
    }

    /**
     * 锁定后 DEK 应被清除，getDek 返回 null。
     *
     * **Validates: Requirements 8.3**
     */
    @Property(tries = 100)
    @Label("should_clearDek_when_vaultIsLocked")
    void should_clearDek_when_vaultIsLocked(
            @ForAll("userIds") Long userId,
            @ForAll("randomDeks") byte[] dek
    ) {
        // Unlock: store DEK in session
        sessionService.storeDek(userId, dek);
        assertThat(sessionService.getDek(userId)).isNotNull();

        // Lock: clear session
        sessionService.clearSession(userId);

        // After lock, DEK should be null
        assertThat(sessionService.getDek(userId)).isNull();
    }

    /**
     * 锁定后会话应不再活跃，isSessionActive 返回 false。
     *
     * **Validates: Requirements 8.5**
     */
    @Property(tries = 100)
    @Label("should_deactivateSession_when_vaultIsLocked")
    void should_deactivateSession_when_vaultIsLocked(
            @ForAll("userIds") Long userId,
            @ForAll("randomDeks") byte[] dek
    ) {
        // Unlock: store DEK in session
        sessionService.storeDek(userId, dek);
        assertThat(sessionService.isSessionActive(userId)).isTrue();

        // Lock: clear session
        sessionService.clearSession(userId);

        // After lock, session should be inactive
        assertThat(sessionService.isSessionActive(userId)).isFalse();
    }

    /**
     * 锁定后原始 DEK 字节应被安全擦除（全部置零）。
     *
     * **Validates: Requirements 8.3**
     */
    @Property(tries = 100)
    @Label("should_secureWipeDekBytes_when_vaultIsLocked")
    void should_secureWipeDekBytes_when_vaultIsLocked(
            @ForAll("userIds") Long userId,
            @ForAll("randomDeks") byte[] dek
    ) {
        // Store DEK and capture the internal copy via getDek
        sessionService.storeDek(userId, dek);
        byte[] internalDek = sessionService.getDek(userId);
        assertThat(internalDek).isNotNull();

        // Lock: clear session
        sessionService.clearSession(userId);

        // The internal DEK bytes should be zeroed out (secure wipe)
        byte[] allZeros = new byte[internalDek.length];
        assertThat(internalDek).isEqualTo(allZeros);
    }

    /**
     * 锁定后再次尝试获取 DEK 应持续返回 null（凭证访问被拒绝）。
     *
     * **Validates: Requirements 8.5**
     */
    @Property(tries = 100)
    @Label("should_rejectSubsequentAccess_when_vaultIsLockedRepeatedly")
    void should_rejectSubsequentAccess_when_vaultIsLockedRepeatedly(
            @ForAll("userIds") Long userId,
            @ForAll("randomDeks") byte[] dek,
            @ForAll("accessAttempts") int attempts
    ) {
        // Unlock then lock
        sessionService.storeDek(userId, dek);
        sessionService.clearSession(userId);

        // Multiple subsequent access attempts should all be rejected
        for (int i = 0; i < attempts; i++) {
            assertThat(sessionService.getDek(userId)).isNull();
            assertThat(sessionService.isSessionActive(userId)).isFalse();
        }
    }

    // --- Providers ---

    @Provide
    Arbitrary<Long> userIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<byte[]> randomDeks() {
        // AES-256 key = 32 bytes
        return Arbitraries.bytes().array(byte[].class).ofSize(32)
                .filter(bytes -> {
                    // Ensure at least one non-zero byte so we can verify wipe
                    for (byte b : bytes) {
                        if (b != 0) return true;
                    }
                    return false;
                });
    }

    @Provide
    Arbitrary<Integer> accessAttempts() {
        return Arbitraries.integers().between(1, 5);
    }
}
