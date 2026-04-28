package com.pm.passwordmanager.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.Optional;

import org.mockito.Mockito;

import com.pm.passwordmanager.api.dto.response.UnlockResultResponse;
import com.pm.passwordmanager.domain.command.LoginCommand;
import com.pm.passwordmanager.domain.command.RegisterCommand;
import com.pm.passwordmanager.domain.model.User;
import com.pm.passwordmanager.domain.repository.UserRepository;
import com.pm.passwordmanager.domain.service.MfaService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.infrastructure.encryption.Argon2Hasher;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import org.mockito.ArgumentCaptor;

/**
 * Feature: multi-user-platform, Property 7: MFA 登录门控（MFA Login Gate）
 *
 * For any 已启用 MFA 的用户，使用正确凭据登录时应返回 mfaRequired=true，
 * 且返回的 sessionToken 为 MFA 临时令牌（非正式会话令牌），不直接签发正式会话。
 *
 * Validates: Requirements 3.5
 */
@Label("Feature: multi-user-platform, Property 7: MFA 登录门控（MFA Login Gate）")
class MfaLoginGatePropertyTest {

    private final Argon2Hasher argon2Hasher = new Argon2Hasher();
    private final EncryptionEngine encryptionEngine = new EncryptionEngine();

    @Property(tries = 100)
    @Label("should_returnMfaRequired_when_mfaEnabledUser_logsIn")
    void should_returnMfaRequired_when_mfaEnabledUser_logsIn(
            @ForAll("validUsernames") String username,
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String password
    ) {
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        MfaService mfaService = Mockito.mock(MfaService.class);

        AuthServiceImpl authService = new AuthServiceImpl(
                userRepository, argon2Hasher, encryptionEngine, sessionService, mfaService);

        // First register the user
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

        // Setup: MFA is enabled for this user
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(savedUser));
        when(mfaService.isMfaEnabled(1L)).thenReturn(true);

        // Login
        UnlockResultResponse result = authService.login(
                LoginCommand.builder().identifier(username).masterPassword(password).build());

        // Should require MFA
        assertThat(result.isMfaRequired()).isTrue();
        // sessionToken here is the MFA temporary token, not a real session token
        assertThat(result.getSessionToken()).isNotNull().isNotEmpty();

        // SessionService.generateToken should NOT have been called (no real session issued)
        verify(sessionService, never()).generateToken(any());
        // DEK should NOT be stored in session yet
        verify(sessionService, never()).storeDek(any(), any());
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
