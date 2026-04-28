package com.pm.passwordmanager.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.pm.passwordmanager.api.dto.response.UnlockResultResponse;
import com.pm.passwordmanager.domain.command.LoginCommand;
import com.pm.passwordmanager.domain.command.RegisterCommand;
import com.pm.passwordmanager.domain.model.User;
import com.pm.passwordmanager.domain.repository.UserRepository;
import com.pm.passwordmanager.domain.service.MfaService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.infrastructure.encryption.Argon2Hasher;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Feature: multi-user-platform, Property 1: 注册-登录往返（Registration-Login Round Trip）
 *
 * For any 合法的用户名、邮箱和满足复杂度要求的主密码，注册成功后使用相同凭据登录应返回有效的 sessionToken。
 *
 * Validates: Requirements 2.1, 3.1
 */
@Label("Feature: multi-user-platform, Property 1: 注册-登录往返（Registration-Login Round Trip）")
class RegistrationLoginRoundTripPropertyTest {

    /**
     * 注册后使用用户名登录应成功返回 sessionToken。
     */
    @Property(tries = 100)
    @Label("should_loginSuccessfully_when_registeredWithValidCredentials_byUsername")
    void should_loginSuccessfully_when_registeredWithValidCredentials_byUsername(
            @ForAll("validUsernames") String username,
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String password
    ) {
        // Use real crypto components
        Argon2Hasher argon2Hasher = new Argon2Hasher();
        EncryptionEngine encryptionEngine = new EncryptionEngine();
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        MfaService mfaService = Mockito.mock(MfaService.class);

        AuthServiceImpl authService = new AuthServiceImpl(
                userRepository, argon2Hasher, encryptionEngine, sessionService, mfaService);

        // Setup: no existing user with this username/email
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // Capture the saved user during registration
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(userCaptor.capture())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        // Register
        authService.register(RegisterCommand.builder()
                .username(username).email(email).masterPassword(password).build());

        User savedUser = userCaptor.getValue();

        // Setup: login finds the saved user by username
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(savedUser));
        when(mfaService.isMfaEnabled(1L)).thenReturn(false);
        when(sessionService.generateToken(1L)).thenReturn("test-token");

        // Login by username
        UnlockResultResponse result = authService.login(
                LoginCommand.builder().identifier(username).masterPassword(password).build());

        assertThat(result.isMfaRequired()).isFalse();
        assertThat(result.getSessionToken()).isEqualTo("test-token");
    }

    /**
     * 注册后使用邮箱登录应成功返回 sessionToken。
     */
    @Property(tries = 100)
    @Label("should_loginSuccessfully_when_registeredWithValidCredentials_byEmail")
    void should_loginSuccessfully_when_registeredWithValidCredentials_byEmail(
            @ForAll("validUsernames") String username,
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String password
    ) {
        Argon2Hasher argon2Hasher = new Argon2Hasher();
        EncryptionEngine encryptionEngine = new EncryptionEngine();
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        MfaService mfaService = Mockito.mock(MfaService.class);

        AuthServiceImpl authService = new AuthServiceImpl(
                userRepository, argon2Hasher, encryptionEngine, sessionService, mfaService);

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(userCaptor.capture())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        authService.register(RegisterCommand.builder()
                .username(username).email(email).masterPassword(password).build());

        User savedUser = userCaptor.getValue();

        // Login by email (contains @)
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(savedUser));
        when(mfaService.isMfaEnabled(1L)).thenReturn(false);
        when(sessionService.generateToken(1L)).thenReturn("test-token");

        UnlockResultResponse result = authService.login(
                LoginCommand.builder().identifier(email).masterPassword(password).build());

        assertThat(result.isMfaRequired()).isFalse();
        assertThat(result.getSessionToken()).isEqualTo("test-token");
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
        // Generate passwords that meet complexity: ≥12 chars, ≥3 character types
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('!', '@', '#', '$')
                .ofMinLength(12).ofMaxLength(20)
                .filter(p -> User.countCharacterTypes(p) >= 3);
    }
}
