package com.pm.passwordmanager.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeProperty;

/**
 * Property 18: 自动锁定超时范围验证
 * For any 自动锁定超时设置值，在 1-60 分钟范围内应被接受，超出范围应被拒绝。
 *
 * Validates: Requirements 8.2
 */
@Label("Feature: password-manager, Property 18: 自动锁定超时范围验证")
class AutoLockTimeoutPropertyTest {

    private static final int MIN_TIMEOUT = 1;
    private static final int MAX_TIMEOUT = 60;
    private static final Long TEST_USER_ID = 1L;

    private SessionServiceImpl sessionService;

    @BeforeProperty
    void setUp() {
        sessionService = new SessionServiceImpl();
        // Store a DEK so the session exists for the user
        sessionService.storeDek(TEST_USER_ID, new byte[32]);
    }

    /**
     * 在 1-60 分钟范围内的超时值应被接受并正确存储。
     *
     * **Validates: Requirements 8.2**
     */
    @Property(tries = 100)
    @Label("should_acceptTimeout_when_withinValidRange")
    void should_acceptTimeout_when_withinValidRange(
            @ForAll("validTimeouts") int minutes
    ) {
        sessionService.setAutoLockTimeout(TEST_USER_ID, minutes);

        assertThat(sessionService.getAutoLockTimeout(TEST_USER_ID)).isEqualTo(minutes);
    }

    /**
     * 小于 1 分钟的超时值应被拒绝。
     *
     * **Validates: Requirements 8.2**
     */
    @Property(tries = 100)
    @Label("should_rejectTimeout_when_belowMinimum")
    void should_rejectTimeout_when_belowMinimum(
            @ForAll("belowMinTimeouts") int minutes
    ) {
        assertThatThrownBy(() -> sessionService.setAutoLockTimeout(TEST_USER_ID, minutes))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 大于 60 分钟的超时值应被拒绝。
     *
     * **Validates: Requirements 8.2**
     */
    @Property(tries = 100)
    @Label("should_rejectTimeout_when_aboveMaximum")
    void should_rejectTimeout_when_aboveMaximum(
            @ForAll("aboveMaxTimeouts") int minutes
    ) {
        assertThatThrownBy(() -> sessionService.setAutoLockTimeout(TEST_USER_ID, minutes))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 拒绝无效超时值后，原有超时设置不应被改变。
     *
     * **Validates: Requirements 8.2**
     */
    @Property(tries = 100)
    @Label("should_preserveExistingTimeout_when_invalidValueRejected")
    void should_preserveExistingTimeout_when_invalidValueRejected(
            @ForAll("validTimeouts") int validMinutes,
            @ForAll("invalidTimeouts") int invalidMinutes
    ) {
        sessionService.setAutoLockTimeout(TEST_USER_ID, validMinutes);

        try {
            sessionService.setAutoLockTimeout(TEST_USER_ID, invalidMinutes);
        } catch (IllegalArgumentException ignored) {
            // expected
        }

        assertThat(sessionService.getAutoLockTimeout(TEST_USER_ID)).isEqualTo(validMinutes);
    }

    // --- Providers ---

    @Provide
    Arbitrary<Integer> validTimeouts() {
        return Arbitraries.integers().between(MIN_TIMEOUT, MAX_TIMEOUT);
    }

    @Provide
    Arbitrary<Integer> belowMinTimeouts() {
        return Arbitraries.integers().between(Integer.MIN_VALUE, MIN_TIMEOUT - 1);
    }

    @Provide
    Arbitrary<Integer> aboveMaxTimeouts() {
        return Arbitraries.integers().between(MAX_TIMEOUT + 1, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Integer> invalidTimeouts() {
        return Arbitraries.oneOf(
                Arbitraries.integers().between(Integer.MIN_VALUE, MIN_TIMEOUT - 1),
                Arbitraries.integers().between(MAX_TIMEOUT + 1, Integer.MAX_VALUE)
        );
    }
}
