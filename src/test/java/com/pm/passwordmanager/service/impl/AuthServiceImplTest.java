package com.pm.passwordmanager.service.impl;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pm.passwordmanager.dto.request.CreateMasterPasswordRequest;
import com.pm.passwordmanager.dto.request.UnlockVaultRequest;
import com.pm.passwordmanager.dto.response.UnlockResultResponse;
import com.pm.passwordmanager.entity.UserEntity;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.mapper.UserMapper;
import com.pm.passwordmanager.service.SessionService;
import com.pm.passwordmanager.util.Argon2Hasher;
import com.pm.passwordmanager.util.EncryptedData;
import com.pm.passwordmanager.util.EncryptionEngine;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private Argon2Hasher argon2Hasher;
    @Mock
    private EncryptionEngine encryptionEngine;
    @Mock
    private SessionService sessionService;
    @Mock
    private com.pm.passwordmanager.service.MfaService mfaService;

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
        testPasswordHash = "dGVzdEhhc2g="; // base64 of "testHash"
    }

    // ========== setup tests ==========

    @Test
    void should_createMasterPassword_when_passwordMeetsComplexity() {
        CreateMasterPasswordRequest request = CreateMasterPasswordRequest.builder()
                .masterPassword("MyStr0ng!Pass")
                .build();

        when(argon2Hasher.generateSalt()).thenReturn(testSalt);
        when(argon2Hasher.hash(eq("MyStr0ng!Pass"), eq(testSalt))).thenReturn(testPasswordHash);
        when(argon2Hasher.deriveKey(eq("MyStr0ng!Pass"), eq(testSalt), eq(32))).thenReturn(testKek);
        when(encryptionEngine.generateDek()).thenReturn(testDek);
        when(encryptionEngine.encrypt(eq(testDek), eq(testKek)))
                .thenReturn(new EncryptedData(new byte[]{99}, new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}));

        authService.setup(request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(captor.capture());

        UserEntity saved = captor.getValue();
        assertThat(saved.getMasterPasswordHash()).isEqualTo(testPasswordHash);
        assertThat(saved.getSalt()).isEqualTo(Base64.getEncoder().encodeToString(testSalt));
        assertThat(saved.getEncryptionKeyEncrypted()).isNotNull();
        assertThat(saved.getFailedAttempts()).isEqualTo(0);
    }

    @Test
    void should_rejectSetup_when_passwordTooShort() {
        CreateMasterPasswordRequest request = CreateMasterPasswordRequest.builder()
                .masterPassword("Short1!")
                .build();

        assertThatThrownBy(() -> authService.setup(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MASTER_PASSWORD_TOO_WEAK);
    }

    @Test
    void should_rejectSetup_when_passwordLacksCharacterTypes() {
        // 12 chars but only lowercase letters (1 type)
        CreateMasterPasswordRequest request = CreateMasterPasswordRequest.builder()
                .masterPassword("abcdefghijkl")
                .build();

        assertThatThrownBy(() -> authService.setup(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MASTER_PASSWORD_TOO_WEAK);
    }

    @Test
    void should_rejectSetup_when_passwordHasOnlyTwoTypes() {
        // 12 chars with upper + lower (2 types, need 3)
        CreateMasterPasswordRequest request = CreateMasterPasswordRequest.builder()
                .masterPassword("AbcdefghijkL")
                .build();

        assertThatThrownBy(() -> authService.setup(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MASTER_PASSWORD_TOO_WEAK);
    }

    // ========== unlock tests ==========

    @Test
    void should_unlockVault_when_correctPassword() {
        UnlockVaultRequest request = UnlockVaultRequest.builder()
                .masterPassword("MyStr0ng!Pass")
                .build();

        byte[] iv = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        byte[] ciphertext = new byte[]{99, 100};
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

        UserEntity user = UserEntity.builder()
                .id(1L)
                .masterPasswordHash(testPasswordHash)
                .salt(Base64.getEncoder().encodeToString(testSalt))
                .encryptionKeyEncrypted(combined)
                .failedAttempts(0)
                .autoLockMinutes(5)
                .build();

        when(userMapper.selectOne(any())).thenReturn(user);
        when(argon2Hasher.verify(eq("MyStr0ng!Pass"), eq(testSalt), eq(testPasswordHash))).thenReturn(true);
        when(argon2Hasher.deriveKey(eq("MyStr0ng!Pass"), eq(testSalt), eq(32))).thenReturn(testKek);
        when(encryptionEngine.decrypt(any(EncryptedData.class), eq(testKek))).thenReturn(testDek);

        UnlockResultResponse result = authService.unlock(request);

        assertThat(result.isMfaRequired()).isFalse();
        verify(sessionService).storeDek(eq(1L), eq(testDek));
        verify(userMapper).updateById(any(UserEntity.class));
    }

    @Test
    void should_rejectUnlock_when_masterPasswordIsWrong() {
        UnlockVaultRequest request = UnlockVaultRequest.builder()
                .masterPassword("WrongPassword1!")
                .build();

        UserEntity user = UserEntity.builder()
                .id(1L)
                .masterPasswordHash(testPasswordHash)
                .salt(Base64.getEncoder().encodeToString(testSalt))
                .encryptionKeyEncrypted(new byte[14])
                .failedAttempts(0)
                .autoLockMinutes(5)
                .build();

        when(userMapper.selectOne(any())).thenReturn(user);
        when(argon2Hasher.verify(eq("WrongPassword1!"), eq(testSalt), eq(testPasswordHash))).thenReturn(false);

        assertThatThrownBy(() -> authService.unlock(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MASTER_PASSWORD_WRONG);

        // Failed attempts should be incremented
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void should_lockAccount_when_fiveConsecutiveFailures() {
        UnlockVaultRequest request = UnlockVaultRequest.builder()
                .masterPassword("WrongPassword1!")
                .build();

        UserEntity user = UserEntity.builder()
                .id(1L)
                .masterPasswordHash(testPasswordHash)
                .salt(Base64.getEncoder().encodeToString(testSalt))
                .encryptionKeyEncrypted(new byte[14])
                .failedAttempts(4) // already 4 failures
                .autoLockMinutes(5)
                .build();

        when(userMapper.selectOne(any())).thenReturn(user);
        when(argon2Hasher.verify(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.unlock(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MASTER_PASSWORD_WRONG);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).updateById(captor.capture());
        UserEntity updated = captor.getValue();
        assertThat(updated.getFailedAttempts()).isEqualTo(5);
        assertThat(updated.getLockedUntil()).isNotNull();
        assertThat(updated.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(14));
    }

    @Test
    void should_rejectUnlock_when_accountIsLocked() {
        UnlockVaultRequest request = UnlockVaultRequest.builder()
                .masterPassword("MyStr0ng!Pass")
                .build();

        UserEntity user = UserEntity.builder()
                .id(1L)
                .masterPasswordHash(testPasswordHash)
                .salt(Base64.getEncoder().encodeToString(testSalt))
                .encryptionKeyEncrypted(new byte[14])
                .failedAttempts(5)
                .lockedUntil(LocalDateTime.now().plusMinutes(10))
                .autoLockMinutes(5)
                .build();

        when(userMapper.selectOne(any())).thenReturn(user);

        assertThatThrownBy(() -> authService.unlock(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);

        verify(argon2Hasher, never()).verify(any(), any(), any());
    }

    @Test
    void should_allowUnlock_when_lockoutPeriodExpired() {
        UnlockVaultRequest request = UnlockVaultRequest.builder()
                .masterPassword("MyStr0ng!Pass")
                .build();

        byte[] iv = new byte[12];
        byte[] ciphertext = new byte[2];
        byte[] combined = new byte[14];

        UserEntity user = UserEntity.builder()
                .id(1L)
                .masterPasswordHash(testPasswordHash)
                .salt(Base64.getEncoder().encodeToString(testSalt))
                .encryptionKeyEncrypted(combined)
                .failedAttempts(5)
                .lockedUntil(LocalDateTime.now().minusMinutes(1)) // lock expired
                .autoLockMinutes(5)
                .build();

        when(userMapper.selectOne(any())).thenReturn(user);
        when(argon2Hasher.verify(any(), any(), any())).thenReturn(true);
        when(argon2Hasher.deriveKey(any(), any(), anyInt())).thenReturn(testKek);
        when(encryptionEngine.decrypt(any(EncryptedData.class), any())).thenReturn(testDek);

        UnlockResultResponse result = authService.unlock(request);

        assertThat(result.isMfaRequired()).isFalse();
        verify(sessionService).storeDek(eq(1L), any());
    }

    @Test
    void should_resetFailedAttempts_when_correctPasswordAfterFailures() {
        UnlockVaultRequest request = UnlockVaultRequest.builder()
                .masterPassword("MyStr0ng!Pass")
                .build();

        byte[] combined = new byte[14];

        UserEntity user = UserEntity.builder()
                .id(1L)
                .masterPasswordHash(testPasswordHash)
                .salt(Base64.getEncoder().encodeToString(testSalt))
                .encryptionKeyEncrypted(combined)
                .failedAttempts(3)
                .autoLockMinutes(5)
                .build();

        when(userMapper.selectOne(any())).thenReturn(user);
        when(argon2Hasher.verify(any(), any(), any())).thenReturn(true);
        when(argon2Hasher.deriveKey(any(), any(), anyInt())).thenReturn(testKek);
        when(encryptionEngine.decrypt(any(EncryptedData.class), any())).thenReturn(testDek);

        authService.unlock(request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getFailedAttempts()).isEqualTo(0);
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    // ========== password complexity validation tests ==========

    @Test
    void should_acceptPassword_when_threeCharacterTypes() {
        // upper + lower + digit = 3 types
        authService.validatePasswordComplexity("Abcdefghij1k");
        // upper + lower + special = 3 types
        authService.validatePasswordComplexity("Abcdefghij!k");
        // lower + digit + special = 3 types
        authService.validatePasswordComplexity("abcdefghij1!");
    }

    @Test
    void should_acceptPassword_when_fourCharacterTypes() {
        authService.validatePasswordComplexity("Abcdefghi1!k");
    }

    @Test
    void should_countCharacterTypes_correctly() {
        assertThat(authService.countCharacterTypes("abc")).isEqualTo(1);
        assertThat(authService.countCharacterTypes("abcABC")).isEqualTo(2);
        assertThat(authService.countCharacterTypes("abcABC123")).isEqualTo(3);
        assertThat(authService.countCharacterTypes("abcABC123!")).isEqualTo(4);
    }
}
