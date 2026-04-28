package com.pm.passwordmanager.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pm.passwordmanager.domain.command.LoginCommand;
import com.pm.passwordmanager.domain.command.RegisterCommand;
import com.pm.passwordmanager.api.dto.response.UnlockResultResponse;
import com.pm.passwordmanager.domain.model.User;
import com.pm.passwordmanager.domain.repository.UserRepository;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.domain.service.MfaService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.infrastructure.encryption.Argon2Hasher;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private Argon2Hasher argon2Hasher;
    @Mock
    private EncryptionEngine encryptionEngine;
    @Mock
    private SessionService sessionService;
    @Mock
    private MfaService mfaService;

    @InjectMocks
    private AuthServiceImpl authService;

    private byte[] testSalt;
    private byte[] testDek;
    private byte[] testKek;
    private String testPasswordHash;

    @BeforeEach
    void setUp() {
        testSalt = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        testDek = new byte[32];
        testKek = new byte[32];
        testPasswordHash = "dGVzdEhhc2g=";
    }

    // ========== register tests ==========

    @Test
    void should_registerUser_when_validCredentials() {
        RegisterCommand request = RegisterCommand.builder()
                .username("testuser")
                .email("test@example.com")
                .masterPassword("MyStr0ng!Pass")
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(argon2Hasher.generateSalt()).thenReturn(testSalt);
        when(argon2Hasher.hash(eq("MyStr0ng!Pass"), eq(testSalt))).thenReturn(testPasswordHash);
        when(argon2Hasher.deriveKey(eq("MyStr0ng!Pass"), eq(testSalt), eq(32))).thenReturn(testKek);
        when(encryptionEngine.generateDek()).thenReturn(testDek);
        when(encryptionEngine.encrypt(eq(testDek), eq(testKek)))
                .thenReturn(new EncryptedData(new byte[]{99}, new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}));

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("testuser");
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
        assertThat(saved.getMasterPasswordHash()).isEqualTo(testPasswordHash);
        assertThat(saved.getSalt()).isEqualTo(Base64.getEncoder().encodeToString(testSalt));
        assertThat(saved.getEncryptionKeyEncrypted()).isNotNull();
        assertThat(saved.getFailedAttempts()).isEqualTo(0);
    }

    @Test
    void should_rejectRegister_when_passwordTooShort() {
        RegisterCommand request = RegisterCommand.builder()
                .username("testuser")
                .email("test@example.com")
                .masterPassword("Short1!")
                .build();

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MASTER_PASSWORD_TOO_WEAK);
    }

    @Test
    void should_rejectRegister_when_passwordLacksCharacterTypes() {
        RegisterCommand request = RegisterCommand.builder()
                .username("testuser")
                .email("test@example.com")
                .masterPassword("abcdefghijkl")
                .build();

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MASTER_PASSWORD_TOO_WEAK);
    }

    @Test
    void should_rejectRegister_when_passwordHasOnlyTwoTypes() {
        RegisterCommand request = RegisterCommand.builder()
                .username("testuser")
                .email("test@example.com")
                .masterPassword("AbcdefghijkL")
                .build();

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MASTER_PASSWORD_TOO_WEAK);
    }

    // ========== login tests ==========

    @Test
    void should_loginSuccessfully_when_correctPasswordByUsername() {
        LoginCommand request = LoginCommand.builder()
                .identifier("testuser")
                .masterPassword("MyStr0ng!Pass")
                .build();

        byte[] iv = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        byte[] ciphertext = new byte[]{99, 100};
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

        User user = User.builder()
                .id(1L)
                .username("testuser")
                .masterPasswordHash(testPasswordHash)
                .salt(Base64.getEncoder().encodeToString(testSalt))
                .encryptionKeyEncrypted(combined)
                .failedAttempts(0)
                .autoLockMinutes(5)
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(argon2Hasher.verify(eq("MyStr0ng!Pass"), eq(testSalt), eq(testPasswordHash))).thenReturn(true);
        when(argon2Hasher.deriveKey(eq("MyStr0ng!Pass"), eq(testSalt), eq(32))).thenReturn(testKek);
        when(encryptionEngine.decrypt(any(EncryptedData.class), eq(testKek))).thenReturn(testDek);
        when(mfaService.isMfaEnabled(1L)).thenReturn(false);

        UnlockResultResponse result = authService.login(request);

        assertThat(result.isMfaRequired()).isFalse();
        verify(sessionService).storeDek(eq(1L), eq(testDek));
        verify(userRepository).updateById(any(User.class));
    }

    @Test
    void should_returnCredentialsInvalid_when_wrongPassword() {
        LoginCommand request = LoginCommand.builder()
                .identifier("testuser")
                .masterPassword("WrongPassword1!")
                .build();

        User user = User.builder()
                .id(1L)
                .username("testuser")
                .masterPasswordHash(testPasswordHash)
                .salt(Base64.getEncoder().encodeToString(testSalt))
                .encryptionKeyEncrypted(new byte[14])
                .failedAttempts(0)
                .autoLockMinutes(5)
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(argon2Hasher.verify(eq("WrongPassword1!"), eq(testSalt), eq(testPasswordHash))).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREDENTIALS_INVALID);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).updateById(captor.capture());
        assertThat(captor.getValue().getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void should_lockAccount_when_fiveConsecutiveFailures() {
        LoginCommand request = LoginCommand.builder()
                .identifier("testuser")
                .masterPassword("WrongPassword1!")
                .build();

        User user = User.builder()
                .id(1L)
                .username("testuser")
                .masterPasswordHash(testPasswordHash)
                .salt(Base64.getEncoder().encodeToString(testSalt))
                .encryptionKeyEncrypted(new byte[14])
                .failedAttempts(4)
                .autoLockMinutes(5)
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(argon2Hasher.verify(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREDENTIALS_INVALID);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).updateById(captor.capture());
        User updated = captor.getValue();
        assertThat(updated.getFailedAttempts()).isEqualTo(5);
        assertThat(updated.getLockedUntil()).isNotNull();
        assertThat(updated.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(14));
    }

    @Test
    void should_rejectLogin_when_accountIsLocked() {
        LoginCommand request = LoginCommand.builder()
                .identifier("testuser")
                .masterPassword("MyStr0ng!Pass")
                .build();

        User user = User.builder()
                .id(1L)
                .username("testuser")
                .masterPasswordHash(testPasswordHash)
                .salt(Base64.getEncoder().encodeToString(testSalt))
                .encryptionKeyEncrypted(new byte[14])
                .failedAttempts(5)
                .lockedUntil(LocalDateTime.now().plusMinutes(10))
                .autoLockMinutes(5)
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);

        verify(argon2Hasher, never()).verify(any(), any(), any());
    }

    @Test
    void should_allowLogin_when_lockoutPeriodExpired() {
        LoginCommand request = LoginCommand.builder()
                .identifier("testuser")
                .masterPassword("MyStr0ng!Pass")
                .build();

        byte[] combined = new byte[14];

        User user = User.builder()
                .id(1L)
                .username("testuser")
                .masterPasswordHash(testPasswordHash)
                .salt(Base64.getEncoder().encodeToString(testSalt))
                .encryptionKeyEncrypted(combined)
                .failedAttempts(5)
                .lockedUntil(LocalDateTime.now().minusMinutes(1))
                .autoLockMinutes(5)
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(argon2Hasher.verify(any(), any(), any())).thenReturn(true);
        when(argon2Hasher.deriveKey(any(), any(), anyInt())).thenReturn(testKek);
        when(encryptionEngine.decrypt(any(EncryptedData.class), any())).thenReturn(testDek);
        when(mfaService.isMfaEnabled(1L)).thenReturn(false);

        UnlockResultResponse result = authService.login(request);

        assertThat(result.isMfaRequired()).isFalse();
        verify(sessionService).storeDek(eq(1L), any());
    }

    @Test
    void should_resetFailedAttempts_when_correctPasswordAfterFailures() {
        LoginCommand request = LoginCommand.builder()
                .identifier("testuser")
                .masterPassword("MyStr0ng!Pass")
                .build();

        byte[] combined = new byte[14];

        User user = User.builder()
                .id(1L)
                .username("testuser")
                .masterPasswordHash(testPasswordHash)
                .salt(Base64.getEncoder().encodeToString(testSalt))
                .encryptionKeyEncrypted(combined)
                .failedAttempts(3)
                .autoLockMinutes(5)
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(argon2Hasher.verify(any(), any(), any())).thenReturn(true);
        when(argon2Hasher.deriveKey(any(), any(), anyInt())).thenReturn(testKek);
        when(encryptionEngine.decrypt(any(EncryptedData.class), any())).thenReturn(testDek);
        when(mfaService.isMfaEnabled(1L)).thenReturn(false);

        authService.login(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).updateById(captor.capture());
        assertThat(captor.getValue().getFailedAttempts()).isEqualTo(0);
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    // ========== password complexity validation tests ==========

    @Test
    void should_acceptPassword_when_threeCharacterTypes() {
        User.validatePasswordComplexity("Abcdefghij1k");
        User.validatePasswordComplexity("Abcdefghij!k");
        User.validatePasswordComplexity("abcdefghij1!");
    }

    @Test
    void should_acceptPassword_when_fourCharacterTypes() {
        User.validatePasswordComplexity("Abcdefghi1!k");
    }

    @Test
    void should_countCharacterTypes_correctly() {
        assertThat(User.countCharacterTypes("abc")).isEqualTo(1);
        assertThat(User.countCharacterTypes("abcABC")).isEqualTo(2);
        assertThat(User.countCharacterTypes("abcABC123")).isEqualTo(3);
        assertThat(User.countCharacterTypes("abcABC123!")).isEqualTo(4);
    }
}
