package com.pm.passwordmanager.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pm.passwordmanager.api.assembler.AuthDtoMapper;
import com.pm.passwordmanager.api.dto.request.EnableMfaRequest;
import com.pm.passwordmanager.api.dto.request.VerifyTotpRequest;
import com.pm.passwordmanager.api.dto.response.ApiResponse;
import com.pm.passwordmanager.api.dto.response.MfaSetupResponse;
import com.pm.passwordmanager.api.dto.response.UnlockResultResponse;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.domain.service.AuthService;
import com.pm.passwordmanager.domain.service.MfaService;
import com.pm.passwordmanager.domain.service.SessionService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private AuthDtoMapper authDtoMapper;
    @Mock
    private MfaService mfaService;
    @Mock
    private SessionService sessionService;

    @InjectMocks
    private AuthController authController;

    // TODO: Task 6 will add register/login endpoint tests

    @Test
    void should_returnTrue_when_statusChecked() {
        when(authService.isInitialized()).thenReturn(true);
        ApiResponse<Boolean> response = authController.status();
        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isTrue();
    }

    @Test
    void should_returnSuccess_when_verifyTotpWithValidCode() {
        VerifyTotpRequest request = VerifyTotpRequest.builder()
                .mfaToken("mfa-token-123").totpCode("123456").build();
        when(authService.verifyTotpAndUnlock("mfa-token-123", "123456")).thenReturn(
                UnlockResultResponse.builder().mfaRequired(false).sessionToken("authenticated").build());

        ApiResponse<UnlockResultResponse> response = authController.verifyTotp(request);

        assertThat(response.getData().getSessionToken()).isEqualTo("authenticated");
    }

    @Test
    void should_propagateException_when_verifyTotpWithInvalidCode() {
        VerifyTotpRequest request = VerifyTotpRequest.builder()
                .mfaToken("mfa-token-123").totpCode("000000").build();
        doThrow(new BusinessException(ErrorCode.TOTP_INVALID))
                .when(authService).verifyTotpAndUnlock("mfa-token-123", "000000");

        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> authController.verifyTotp(request));
    }

    @Test
    void should_clearSessionAndReturnSuccess_when_lock() {
        when(authService.getCurrentUserId()).thenReturn(1L);

        ApiResponse<Void> response = authController.lock();

        assertThat(response.getCode()).isEqualTo(0);
        verify(sessionService).clearSession(1L);
    }

    @Test
    void should_returnSetupInfo_when_enableMfaWithoutTotpCode() {
        when(authService.getCurrentUserId()).thenReturn(1L);
        MfaSetupResponse setupResponse = MfaSetupResponse.builder()
                .qrCodeUri("otpauth://totp/test")
                .recoveryCodes(List.of("CODE1", "CODE2")).build();
        when(mfaService.initSetup(1L)).thenReturn(setupResponse);

        ApiResponse<MfaSetupResponse> response = authController.enableMfa(EnableMfaRequest.builder().build());

        assertThat(response.getData().getQrCodeUri()).isEqualTo("otpauth://totp/test");
        assertThat(response.getData().getRecoveryCodes()).hasSize(2);
    }

    @Test
    void should_returnSetupInfo_when_enableMfaWithNullRequest() {
        when(authService.getCurrentUserId()).thenReturn(1L);
        when(mfaService.initSetup(1L)).thenReturn(
                MfaSetupResponse.builder().qrCodeUri("otpauth://totp/test")
                        .recoveryCodes(List.of("CODE1")).build());

        assertThat(authController.enableMfa(null).getData()).isNotNull();
    }

    @Test
    void should_confirmEnable_when_enableMfaWithTotpCode() {
        when(authService.getCurrentUserId()).thenReturn(1L);

        authController.enableMfa(EnableMfaRequest.builder().totpCode("123456").build());

        verify(mfaService).confirmEnable(1L, "123456");
    }

    @Test
    void should_disableMfa_when_authenticated() {
        when(authService.getCurrentUserId()).thenReturn(1L);

        assertThat(authController.disableMfa().getCode()).isEqualTo(0);
        verify(mfaService).disable(1L);
    }

    @Test
    void should_propagateException_when_disableMfaNotEnabled() {
        when(authService.getCurrentUserId()).thenReturn(1L);
        doThrow(new BusinessException(ErrorCode.MFA_NOT_ENABLED)).when(mfaService).disable(1L);

        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> authController.disableMfa());
    }

    @Test
    void should_throwVaultLocked_when_noUserExists() {
        when(authService.getCurrentUserId()).thenThrow(new BusinessException(ErrorCode.VAULT_LOCKED));

        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> authController.lock());
    }
}
