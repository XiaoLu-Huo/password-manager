package com.pm.passwordmanager.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.mockito.Mockito;

import com.pm.passwordmanager.domain.command.RegisterCommand;
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
 * Feature: multi-user-platform, Property 2: 重复注册拒绝（Duplicate Registration Rejection）
 *
 * For any 已注册的用户名或邮箱，使用相同的用户名或邮箱再次注册应失败并返回对应的重复错误码。
 *
 * Validates: Requirements 2.2, 2.3
 */
@Label("Feature: multi-user-platform, Property 2: 重复注册拒绝（Duplicate Registration Rejection）")
class DuplicateRegistrationRejectionPropertyTest {

    @Property(tries = 100)
    @Label("should_rejectRegistration_when_usernameAlreadyExists")
    void should_rejectRegistration_when_usernameAlreadyExists(
            @ForAll("validUsernames") String username,
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String password
    ) {
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        MfaService mfaService = Mockito.mock(MfaService.class);
        Argon2Hasher argon2Hasher = new Argon2Hasher();
        EncryptionEngine encryptionEngine = new EncryptionEngine();

        AuthServiceImpl authService = new AuthServiceImpl(
                userRepository, argon2Hasher, encryptionEngine, sessionService, mfaService);

        // Simulate existing user with same username
        User existingUser = User.builder().id(1L).username(username).build();
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(existingUser));

        RegisterCommand command = RegisterCommand.builder()
                .username(username).email(email).masterPassword(password).build();

        assertThatThrownBy(() -> authService.register(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USERNAME_DUPLICATE);

        // No user should be saved
        verify(userRepository, never()).save(any());
    }

    @Property(tries = 100)
    @Label("should_rejectRegistration_when_emailAlreadyExists")
    void should_rejectRegistration_when_emailAlreadyExists(
            @ForAll("validUsernames") String username,
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String password
    ) {
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        MfaService mfaService = Mockito.mock(MfaService.class);
        Argon2Hasher argon2Hasher = new Argon2Hasher();
        EncryptionEngine encryptionEngine = new EncryptionEngine();

        AuthServiceImpl authService = new AuthServiceImpl(
                userRepository, argon2Hasher, encryptionEngine, sessionService, mfaService);

        // Username is available, but email is taken
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        User existingUser = User.builder().id(1L).email(email).build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));

        RegisterCommand command = RegisterCommand.builder()
                .username(username).email(email).masterPassword(password).build();

        assertThatThrownBy(() -> authService.register(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_DUPLICATE);

        verify(userRepository, never()).save(any());
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
