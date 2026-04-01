package com.pm.passwordmanager.util;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.NotEmpty;
import net.jqwik.api.constraints.StringLength;

/**
 * Property 2: 认证正确性（往返属性）
 * 验证：对任意密码进行哈希后，使用相同密码验证应成功，使用不同密码验证应失败。
 *
 * Validates: Requirements 1.3, 1.4
 */
@Label("Feature: password-manager, Property 2: 认证正确性（往返属性）")
class Argon2HasherPropertyTest {

    private final Argon2Hasher argon2Hasher = new Argon2Hasher();

    /**
     * 对任意密码进行哈希后，使用相同密码和盐值验证应返回 true。
     *
     * **Validates: Requirements 1.3**
     */
    @Property(tries = 100)
    @Label("should_verifySuccessfully_when_samePasswordUsed")
    void should_verifySuccessfully_when_samePasswordUsed(
            @ForAll @NotEmpty @StringLength(min = 1, max = 32) String password
    ) {
        byte[] salt = argon2Hasher.generateSalt();
        String hash = argon2Hasher.hash(password, salt);

        boolean result = argon2Hasher.verify(password, salt, hash);

        assertThat(result).isTrue();
    }

    /**
     * 对任意两个不同的密码，使用 password1 哈希后用 password2 验证应返回 false。
     *
     * **Validates: Requirements 1.4**
     */
    @Property(tries = 100)
    @Label("should_rejectVerification_when_differentPasswordUsed")
    void should_rejectVerification_when_differentPasswordUsed(
            @ForAll @NotEmpty @StringLength(min = 1, max = 32) String password1,
            @ForAll @NotEmpty @StringLength(min = 1, max = 32) String password2
    ) {
        Assume.that(!password1.equals(password2));

        byte[] salt = argon2Hasher.generateSalt();
        String hash = argon2Hasher.hash(password1, salt);

        boolean result = argon2Hasher.verify(password2, salt, hash);

        assertThat(result).isFalse();
    }
}
