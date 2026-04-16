package com.pm.passwordmanager.service.impl;

import java.io.ByteArrayOutputStream;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.excel.EasyExcel;
import com.pm.passwordmanager.api.dto.response.ImportResultResponse;
import com.pm.passwordmanager.api.enums.ConflictStrategy;
import com.pm.passwordmanager.domain.model.Credential;
import com.pm.passwordmanager.domain.repository.CredentialRepository;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.domain.service.impl.CredentialExcelRow;
import com.pm.passwordmanager.domain.service.impl.ImportExportServiceImpl;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;
import com.pm.passwordmanager.infrastructure.encryption.ExcelEncryptionUtil;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property 15: 导入需要正确密码
 * 验证：错误密码导入失败，正确密码导入成功
 * Validates: Requirements 7.4
 */
@Label("Feature: password-manager, Property 15: 导入需要正确密码")
class ImportPasswordPropertyTest {

    private static final Long USER_ID = 1L;
    private static final byte[] FAKE_DEK = new byte[32];

    private final CredentialRepository credentialRepository = mock(CredentialRepository.class);
    private final EncryptionEngine encryptionEngine = mock(EncryptionEngine.class);
    private final SessionService sessionService = mock(SessionService.class);

    private final ImportExportServiceImpl service = new ImportExportServiceImpl(
            credentialRepository, encryptionEngine, sessionService);

    ImportPasswordPropertyTest() {
        when(sessionService.getDek(USER_ID)).thenReturn(FAKE_DEK);
        when(encryptionEngine.encrypt(any(byte[].class), eq(FAKE_DEK)))
                .thenReturn(new EncryptedData(new byte[]{1, 2, 3}, new byte[]{4, 5, 6}));
        when(credentialRepository.findByUserIdAndAccountName(eq(USER_ID), any()))
                .thenReturn(Collections.emptyList());
        when(credentialRepository.save(any(Credential.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Property(tries = 100)
    @Label("should_failImport_when_wrongPassword")
    void should_failImport_when_wrongPassword(
            @ForAll("distinctPasswordPairs") String[] passwords
    ) {
        String correctPassword = passwords[0];
        String wrongPassword = passwords[1];

        byte[] encryptedExcel = createEncryptedExcel(correctPassword);

        assertThatThrownBy(() -> service.importFromExcel(USER_ID, encryptedExcel, wrongPassword, ConflictStrategy.SKIP))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.IMPORT_DECRYPTION_FAILED));
    }

    @Property(tries = 100)
    @Label("should_succeedImport_when_correctPassword")
    void should_succeedImport_when_correctPassword(
            @ForAll("nonBlankPasswords") String password
    ) {
        byte[] encryptedExcel = createEncryptedExcel(password);

        ImportResultResponse result = service.importFromExcel(
                USER_ID, encryptedExcel, password, ConflictStrategy.SKIP);

        assertThat(result).isNotNull();
        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getImportedCount()).isEqualTo(1);
    }

    // ==================== Helpers ====================

    /**
     * Creates an encrypted Excel file with one sample credential row.
     */
    private byte[] createEncryptedExcel(String password) {
        CredentialExcelRow row = CredentialExcelRow.builder()
                .accountName("TestAccount")
                .username("testuser")
                .password("TestPass123")
                .url("https://example.com")
                .notes("note")
                .tags("tag1")
                .build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, CredentialExcelRow.class).sheet("Credentials").doWrite(java.util.List.of(row));

        return ExcelEncryptionUtil.encrypt(out.toByteArray(), password);
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<String> nonBlankPasswords() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(32).alpha().numeric();
    }

    @Provide
    Arbitrary<String[]> distinctPasswordPairs() {
        return nonBlankPasswords().flatMap(correct ->
                nonBlankPasswords()
                        .filter(wrong -> !wrong.equals(correct))
                        .map(wrong -> new String[]{correct, wrong}));
    }
}
