package com.pm.passwordmanager.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pm.passwordmanager.dto.request.CreateCredentialRequest;
import com.pm.passwordmanager.dto.response.CredentialResponse;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.mapper.CredentialMapper;
import com.pm.passwordmanager.mapper.PasswordHistoryMapper;
import com.pm.passwordmanager.service.PasswordGeneratorService;
import com.pm.passwordmanager.service.SessionService;
import com.pm.passwordmanager.util.EncryptedData;
import com.pm.passwordmanager.util.EncryptionEngine;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property 7: 凭证必填字段验证
 * 验证：账户名称、用户名、密码三个必填字段均非空时创建成功，缺少任一则失败。
 *
 * Validates: Requirements 3.1, 3.6
 */
@Label("Feature: password-manager, Property 7: 凭证必填字段验证")
class CredentialValidationPropertyTest {

    private static final Long USER_ID = 1L;
    private static final byte[] FAKE_DEK = new byte[32];

    private final CredentialMapper credentialMapper = mock(CredentialMapper.class);
    private final PasswordHistoryMapper passwordHistoryMapper = mock(PasswordHistoryMapper.class);
    private final EncryptionEngine encryptionEngine = mock(EncryptionEngine.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final PasswordGeneratorService passwordGeneratorService = mock(PasswordGeneratorService.class);

    private final CredentialServiceImpl service = new CredentialServiceImpl(
            credentialMapper, passwordHistoryMapper, encryptionEngine, sessionService, passwordGeneratorService);

    CredentialValidationPropertyTest() {
        // Session is active and DEK is available
        when(sessionService.getDek(USER_ID)).thenReturn(FAKE_DEK);
        when(sessionService.isSessionActive(USER_ID)).thenReturn(true);
        // Encryption returns a dummy result
        when(encryptionEngine.encrypt(any(byte[].class), eq(FAKE_DEK)))
                .thenReturn(new EncryptedData(new byte[]{1, 2, 3}, new byte[]{4, 5, 6}));
        // Mapper insert always succeeds
        when(credentialMapper.insert(any())).thenReturn(1);
    }

    /**
     * 当账户名称、用户名、密码均非空时，创建凭证应成功。
     *
     * Validates: Requirements 3.1
     */
    @Property(tries = 100)
    @Label("should_createSuccessfully_when_allRequiredFieldsPresent")
    void should_createSuccessfully_when_allRequiredFieldsPresent(
            @ForAll("nonBlankStrings") String accountName,
            @ForAll("nonBlankStrings") String username,
            @ForAll("nonBlankStrings") String password
    ) {
        CreateCredentialRequest request = CreateCredentialRequest.builder()
                .accountName(accountName)
                .username(username)
                .password(password)
                .build();

        CredentialResponse response = service.createCredential(USER_ID, request);

        assertThat(response).isNotNull();
        assertThat(response.getAccountName()).isEqualTo(accountName);
        assertThat(response.getUsername()).isEqualTo(username);
        assertThat(response.getMaskedPassword()).isEqualTo("••••••");
    }

    /**
     * 当任一必填字段为空或空白时，创建凭证应抛出 CREDENTIAL_REQUIRED_FIELDS_MISSING 异常。
     *
     * Validates: Requirements 3.6
     */
    @Property(tries = 100)
    @Label("should_rejectCreation_when_anyRequiredFieldMissing")
    void should_rejectCreation_when_anyRequiredFieldMissing(
            @ForAll("requestsWithMissingField") CreateCredentialRequest request
    ) {
        assertThatThrownBy(() -> service.createCredential(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CREDENTIAL_REQUIRED_FIELDS_MISSING));
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> nonBlankStrings() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(50)
                .alpha()
                .numeric();
    }

    @Provide
    Arbitrary<CreateCredentialRequest> requestsWithMissingField() {
        Arbitrary<String> validStrings = nonBlankStrings();
        Arbitrary<String> blankStrings = Arbitraries.of(null, "", "   ", "\t", "\n");

        // Pick which field (0=accountName, 1=username, 2=password) to make blank
        Arbitrary<Integer> blankFieldIndex = Arbitraries.integers().between(0, 2);

        return Combinators.combine(validStrings, validStrings, validStrings, blankStrings, blankFieldIndex)
                .as((acct, user, pwd, blank, idx) -> {
                    String accountName = idx == 0 ? blank : acct;
                    String username = idx == 1 ? blank : user;
                    String password = idx == 2 ? blank : pwd;
                    return CreateCredentialRequest.builder()
                            .accountName(accountName)
                            .username(username)
                            .password(password)
                            .build();
                });
    }
}
