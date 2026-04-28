package com.pm.passwordmanager.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.Optional;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.pm.passwordmanager.domain.command.LoginCommand;
import com.pm.passwordmanager.domain.model.User;
import com.pm.passwordmanager.domain.repository.UserRepository;
import com.pm.passwordmanager.domain.service.MfaService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.encryption.Argon2Hasher;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Feature: multi-user-platform, Property 6: 登录错误信息一致性（Generic Login Error）
 *
 * For any 不存在的用户名/邮箱或错误的主密码，登录时应返回相同的通用错误码 CREDENTIALS_INVALID。
 *
 * Validates: Requirements 3.2, 3.3
 */
@Label("Feature: multi-user-platform, Property 6: 登录错误信息一致性（Generic Login Error）")
class GenericLoginErrorPropertyTest {

    private final Argon2Hasher argon2Hasher = new Argon2Hasher();

    /**
     * 不存在的用户名登录应返回 CREDENTIALS_INVALID。
     */
    @Property(tries = 100)
    @Label("should_returnGenericError_when_usernameDoesNotExist")
    void should_returnGenericError_when_usernameDoesNotExist(
            @ForAll("validUsernames") String username,
            @ForAll("validPasswords") String password
    ) {
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        MfaService mfaService = Mockito.mock(MfaService.class);
        EncryptionEngine encryptionEngine = new EncryptionEngine();

        AuthServiceImpl authService = new AuthServiceImpl(
                userRepository, argon2Hasher, encryptionEngine, sessionService, mfaService);

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        BusinessException ex = catchThrowableOfType(
                () -> authService.login(LoginCommand.builder()
                        .identifier(username).masterPassword(password).build()),
                BusinessException.class);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CREDENTIALS_INVALID);
    }

    /**
     * 不存在的邮箱登录应返回 CREDENTIALS_INVALID。
     */
    @Property(tries = 100)
    @Label("should_returnGenericError_when_emailDoesNotExist")
    void should_returnGenericError_when_emailDoesNotExist(
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String password
    ) {
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        MfaService mfaService = Mockito.mock(MfaService.class);
        EncryptionEngine encryptionEngine = new EncryptionEngine();

        AuthServiceImpl authService = new AuthServiceImpl(
                userRepository, argon2Hasher, encryptionEngine, sessionService, mfaService);

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        BusinessException ex = catchThrowableOfType(
                () -> authService.login(LoginCommand.builder()
                        .identifier(email).masterPassword(password).build()),
                BusinessException.class);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CREDENTIALS_INVALID);
    }

    /**
     * 错误密码登录应返回 CREDENTIALS_INVALID 且递增失败计数。
     */
    @Property(tries = 100)
    @Label("should_returnGenericError_and_incrementFailedAttempts_when_wrongPassword")
    void should_returnGenericError_and_incrementFailedAttempts_when_wrongPassword(
            @ForAll("validUsernames") String username,
            @ForAll("validPasswords") String correctPassword,
            @ForAll("validPasswords") String wrongPassword
    ) {
        net.jqwik.api.Assume.that(!correctPassword.equals(wrongPassword));

        UserRepository userRepository = Mockito.mock(UserRepository.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        MfaService mfaService = Mockito.mock(MfaService.class);
        EncryptionEngine encryptionEngine = new EncryptionEngine();

        AuthServiceImpl authService = new AuthServiceImpl(
                userRepository, argon2Hasher, encryptionEngine, sessionService, mfaService);

        // Create a user with the correct password hash
        byte[] salt = argon2Hasher.generateSalt();
        String hash = argon2Hasher.hash(correctPassword, salt);

        User user = User.builder()
                .id(1L)
                .username(username)
                .masterPasswordHash(hash)
                .salt(Base64.getEncoder().encodeToString(salt))
                .encryptionKeyEncrypted(new byte[44])
                .failedAttempts(0)
                .autoLockMinutes(15)
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        BusinessException ex = catchThrowableOfType(
                () -> authService.login(LoginCommand.builder()
                        .identifier(username).masterPassword(wrongPassword).build()),
                BusinessException.class);

        // Same error code as non-existent user
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CREDENTIALS_INVALID);

        // Failed attempts should be incremented
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).updateById(captor.capture());
        assertThat(captor.getValue().getFailedAttempts()).isEqualTo(1);
    }

    @Provide
    Arbitrary<String> validUsernames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_', '-')
                .ofMinLength(3).ofMaxLength(16);
    }

    @Provide
    Arbitrary<String> validEmails() {
        Arbitrary<String> localPart = Arbitraries.strings()
                .withCharRange('a', 'z').ofMinLength(3).ofMaxLength(8);
        Arbitrary<String> domain = Arbitraries.strings()
                .withCharRange('a', 'z').ofMinLength(3).ofMaxLength(8);
        return Combinators.combine(localPart, domain)
                .as((local, dom) -> local + "@" + dom + ".com");
    }

    @Provide
    Arbitrary<String> validPasswords() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('!', '@', '#', '$')
                .ofMinLength(12).ofMaxLength(20)
                .filter(p -> User.countCharacterTypes(p) >= 3);
    }
}
