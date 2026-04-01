package com.pm.passwordmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pm.passwordmanager.dto.request.CreateMasterPasswordRequest;
import com.pm.passwordmanager.dto.request.EnableMfaRequest;
import com.pm.passwordmanager.dto.request.UnlockVaultRequest;
import com.pm.passwordmanager.dto.request.VerifyTotpRequest;
import com.pm.passwordmanager.dto.response.ApiResponse;
import com.pm.passwordmanager.dto.response.MfaSetupResponse;
import com.pm.passwordmanager.dto.response.UnlockResultResponse;
import com.pm.passwordmanager.entity.UserEntity;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.mapper.UserMapper;
import com.pm.passwordmanager.service.AuthService;
import com.pm.passwordmanager.service.MfaService;
import com.pm.passwordmanager.service.SessionService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private MfaService mfaService;
    @Mock
    private SessionService sessionService;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthController authController;

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        testUser = UserEntity.builder().id(1L).build();
    }

    // ========== POST /api/auth/setup ==========

    @Test
    void should_returnSuccess_when_setupWithValidPassword() {
        CreateMasterPasswordRequest request = CreateMasterPasswordRequest.builder()
                .masterPassword("MyStr0ng!Pass")
                .build();
        doNothing().when(authService).setup(request);

        ApiResponse<Void> response = authController.setup(request);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getMessage()).isEqualTo("success");
        verify(authService).setup(request);
    }

    @Test
    void should_propagateException_when_setupWithWeakPassword() {
        CreateMasterPasswordRequest request = CreateMasterPasswordRequest.builder()
                .masterPassword("weak")
                .build();
        doThrow(new BusinessException(ErrorCode.MASTER_PASSWORD_TOO_WEAK))
                .when(authService).setup(request);

        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> authController.setup(request));
    }

    // ========== POST /api/auth/unlock ==========

    @Test
    void should_returnUnlockResult_when_unlockWithCorrectPassword() {
        UnlockVaultRequest request = UnlockVaultRequest.builder()
                .masterPassword("MyStr0ng!Pass")
                .build();
        UnlockResultResponse unlockResult = UnlockResultResponse.builder()
                .mfaRequired(false)
                .build();
        when(authService.unlock(request)).thenReturn(unlockResult);

        ApiResponse<UnlockResultResponse> response = authController.unlock(request);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isEqualTo(unlockResult);
        assertThat(response.getData().isMfaRequired()).isFalse();
    }

    @Test
    void should_returnMfaRequired_when_unlockWithMfaEnabled() {
        UnlockVaultRequest request = UnlockVaultRequest.builder()
                .masterPassword("MyStr0ng!Pass")
                .build();
        UnlockResultResponse unlockResult = UnlockResultResponse.builder()
                .mfaRequired(true)
                .build();
        when(authService.unlock(request)).thenReturn(unlockResult);

        ApiResponse<UnlockResultResponse> response = authController.unlock(request);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData().isMfaRequired()).isTrue();
    }

    // ========== POST /api/auth/verify-totp ==========

    @Test
    void should_returnSuccess_when_verifyTotpWithValidCode() {
        VerifyTotpRequest request = VerifyTotpRequest.builder()
                .totpCode("123456")
                .build();
        UnlockResultResponse result = UnlockResultResponse.builder()
                .mfaRequired(false)
                .sessionToken("authenticated")
                .build();
        when(authService.verifyTotpAndUnlock("123456")).thenReturn(result);

        ApiResponse<UnlockResultResponse> response = authController.verifyTotp(request);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData().getSessionToken()).isEqualTo("authenticated");
    }

    @Test
    void should_propagateException_when_verifyTotpWithInvalidCode() {
        VerifyTotpRequest request = VerifyTotpRequest.builder()
                .totpCode("000000")
                .build();
        doThrow(new BusinessException(ErrorCode.TOTP_INVALID))
                .when(authService).verifyTotpAndUnlock("000000");

        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> authController.verifyTotp(request));
    }

    // ========== POST /api/auth/lock ==========

    @Test
    void should_clearSessionAndReturnSuccess_when_lock() {
        when(userMapper.selectOne(any())).thenReturn(testUser);

        ApiResponse<Void> response = authController.lock();

        assertThat(response.getCode()).isEqualTo(0);
        verify(sessionService).clearSession(1L);
    }

    // ========== POST /api/auth/mfa/enable ==========

    @Test
    void should_returnSetupInfo_when_enableMfaWithoutTotpCode() {
        when(userMapper.selectOne(any())).thenReturn(testUser);
        MfaSetupResponse setupResponse = MfaSetupResponse.builder()
                .qrCodeUri("otpauth://totp/test")
                .recoveryCodes(List.of("CODE1", "CODE2"))
                .build();
        when(mfaService.initSetup(1L)).thenReturn(setupResponse);

        EnableMfaRequest request = EnableMfaRequest.builder().build();
        ApiResponse<MfaSetupResponse> response = authController.enableMfa(request);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData().getQrCodeUri()).isEqualTo("otpauth://totp/test");
        assertThat(response.getData().getRecoveryCodes()).hasSize(2);
    }

    @Test
    void should_returnSetupInfo_when_enableMfaWithNullRequest() {
        when(userMapper.selectOne(any())).thenReturn(testUser);
        MfaSetupResponse setupResponse = MfaSetupResponse.builder()
                .qrCodeUri("otpauth://totp/test")
                .recoveryCodes(List.of("CODE1"))
                .build();
        when(mfaService.initSetup(1L)).thenReturn(setupResponse);

        ApiResponse<MfaSetupResponse> response = authController.enableMfa(null);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isNotNull();
    }

    @Test
    void should_confirmEnable_when_enableMfaWithTotpCode() {
        when(userMapper.selectOne(any())).thenReturn(testUser);
        EnableMfaRequest request = EnableMfaRequest.builder()
                .totpCode("123456")
                .build();

        ApiResponse<MfaSetupResponse> response = authController.enableMfa(request);

        assertThat(response.getCode()).isEqualTo(0);
        verify(mfaService).confirmEnable(1L, "123456");
    }

    // ========== POST /api/auth/mfa/disable ==========

    @Test
    void should_disableMfa_when_authenticated() {
        when(userMapper.selectOne(any())).thenReturn(testUser);

        ApiResponse<Void> response = authController.disableMfa();

        assertThat(response.getCode()).isEqualTo(0);
        verify(mfaService).disable(1L);
    }

    @Test
    void should_propagateException_when_disableMfaNotEnabled() {
        when(userMapper.selectOne(any())).thenReturn(testUser);
        doThrow(new BusinessException(ErrorCode.MFA_NOT_ENABLED))
                .when(mfaService).disable(1L);

        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> authController.disableMfa());
    }

    // ========== getCurrentUserId edge case ==========

    @Test
    void should_throwVaultLocked_when_noUserExists() {
        when(userMapper.selectOne(any())).thenReturn(null);

        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> authController.lock());
    }
}
