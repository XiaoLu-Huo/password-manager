package com.pm.passwordmanager.service.impl;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.excel.EasyExcel;
import com.pm.passwordmanager.api.dto.response.ImportResultResponse;
import com.pm.passwordmanager.api.enums.ConflictStrategy;
import com.pm.passwordmanager.domain.model.Credential;
import com.pm.passwordmanager.domain.repository.CredentialRepository;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.domain.service.impl.CredentialExcelRow;
import com.pm.passwordmanager.domain.service.impl.ImportExportServiceImpl;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;
import com.pm.passwordmanager.infrastructure.encryption.ExcelEncryptionUtil;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.ArgumentCaptor;

/**
 * Property 16: 导入冲突策略
 * 验证：覆盖替换旧数据、跳过保留旧数据、保留两者同时存在
 * Validates: Requirements 7.6
 */
@Label("Feature: password-manager, Property 16: 导入冲突策略")
class ImportConflictPropertyTest {

    private static final Long USER_ID = 1L;
    private static final byte[] FAKE_DEK = new byte[32];
    private static final String EXCEL_PASSWORD = "testpwd";

    /**
     * Helper: build a fresh set of mocks and service for each property invocation
     * to avoid shared mutable state across tries.
     */
    private record TestFixture(
            CredentialRepository credentialRepository,
            EncryptionEngine encryptionEngine,
            SessionService sessionService,
            ImportExportServiceImpl service
    ) {
        static TestFixture create() {
            CredentialRepository repo = mock(CredentialRepository.class);
            EncryptionEngine enc = mock(EncryptionEngine.class);
            SessionService sess = mock(SessionService.class);

            when(sess.getDek(USER_ID)).thenReturn(FAKE_DEK);
            when(enc.encrypt(any(byte[].class), eq(FAKE_DEK)))
                    .thenAnswer(inv -> {
                        byte[] plain = inv.getArgument(0);
                        // Use a deterministic "encryption" so we can verify content later
                        return new EncryptedData(plain, new byte[]{1, 2, 3});
                    });

            ImportExportServiceImpl svc = new ImportExportServiceImpl(repo, enc, sess);
            return new TestFixture(repo, enc, sess, svc);
        }
    }

    // ==================== OVERWRITE ====================

    @Property(tries = 100)
    @Label("should_replaceOldData_when_overwriteStrategy")
    void should_replaceOldData_when_overwriteStrategy(
            @ForAll("credentialData") CredentialData data
    ) {
        TestFixture f = TestFixture.create();

        // Existing credential in vault with same accountName
        Credential existing = Credential.builder()
                .id(42L)
                .userId(USER_ID)
                .accountName(data.accountName)
                .username("old_user")
                .passwordEncrypted(new byte[]{9, 9, 9})
                .iv(new byte[]{8, 8, 8})
                .url("https://old.example.com")
                .notes("old notes")
                .tags("old-tag")
                .createdAt(LocalDateTime.now().minusDays(30))
                .updatedAt(LocalDateTime.now().minusDays(30))
                .build();

        when(f.credentialRepository.findByUserIdAndAccountName(eq(USER_ID), eq(data.accountName)))
                .thenReturn(List.of(existing));
        // Non-conflicting names return empty
        when(f.credentialRepository.findByUserIdAndAccountName(eq(USER_ID),
                org.mockito.AdditionalMatchers.not(eq(data.accountName))))
                .thenReturn(Collections.emptyList());

        byte[] excelFile = createEncryptedExcel(data);

        ImportResultResponse result = f.service.importFromExcel(
                USER_ID, excelFile, EXCEL_PASSWORD, ConflictStrategy.OVERWRITE);

        // Overwrite should update the existing credential
        assertThat(result.getOverwrittenCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isEqualTo(0);

        // Verify updateById was called (overwrite path)
        ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
        verify(f.credentialRepository).updateById(captor.capture());
        Credential updated = captor.getValue();

        // The updated credential should carry the new data
        assertThat(updated.getUsername()).isEqualTo(data.username);
        assertThat(updated.getUrl()).isEqualTo(data.url);
        // No new credential should have been saved
        verify(f.credentialRepository, never()).save(any());
    }

    // ==================== SKIP ====================

    @Property(tries = 100)
    @Label("should_keepOldData_when_skipStrategy")
    void should_keepOldData_when_skipStrategy(
            @ForAll("credentialData") CredentialData data
    ) {
        TestFixture f = TestFixture.create();

        Credential existing = Credential.builder()
                .id(42L)
                .userId(USER_ID)
                .accountName(data.accountName)
                .username("old_user")
                .passwordEncrypted(new byte[]{9, 9, 9})
                .iv(new byte[]{8, 8, 8})
                .build();

        when(f.credentialRepository.findByUserIdAndAccountName(eq(USER_ID), eq(data.accountName)))
                .thenReturn(List.of(existing));

        byte[] excelFile = createEncryptedExcel(data);

        ImportResultResponse result = f.service.importFromExcel(
                USER_ID, excelFile, EXCEL_PASSWORD, ConflictStrategy.SKIP);

        // Skip should not save or update anything
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getOverwrittenCount()).isEqualTo(0);
        assertThat(result.getImportedCount()).isEqualTo(0);

        verify(f.credentialRepository, never()).save(any());
        verify(f.credentialRepository, never()).updateById(any());
    }

    // ==================== KEEP_BOTH ====================

    @Property(tries = 100)
    @Label("should_keepBothRecords_when_keepBothStrategy")
    void should_keepBothRecords_when_keepBothStrategy(
            @ForAll("credentialData") CredentialData data
    ) {
        TestFixture f = TestFixture.create();

        Credential existing = Credential.builder()
                .id(42L)
                .userId(USER_ID)
                .accountName(data.accountName)
                .username("old_user")
                .passwordEncrypted(new byte[]{9, 9, 9})
                .iv(new byte[]{8, 8, 8})
                .build();

        when(f.credentialRepository.findByUserIdAndAccountName(eq(USER_ID), eq(data.accountName)))
                .thenReturn(List.of(existing));
        when(f.credentialRepository.save(any(Credential.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        byte[] excelFile = createEncryptedExcel(data);

        ImportResultResponse result = f.service.importFromExcel(
                USER_ID, excelFile, EXCEL_PASSWORD, ConflictStrategy.KEEP_BOTH);

        // KEEP_BOTH should save a new credential (imported) and leave the old one untouched
        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getOverwrittenCount()).isEqualTo(0);
        assertThat(result.getSkippedCount()).isEqualTo(0);

        // A new credential was saved
        ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
        verify(f.credentialRepository).save(captor.capture());
        Credential saved = captor.getValue();
        assertThat(saved.getAccountName()).isEqualTo(data.accountName);
        assertThat(saved.getUsername()).isEqualTo(data.username);

        // The existing credential was NOT updated
        verify(f.credentialRepository, never()).updateById(any());
    }

    // ==================== Helpers ====================

    /** Simple data carrier for generated credential fields. */
    record CredentialData(String accountName, String username, String password, String url) {}

    private byte[] createEncryptedExcel(CredentialData data) {
        CredentialExcelRow row = CredentialExcelRow.builder()
                .accountName(data.accountName)
                .username(data.username)
                .password(data.password)
                .url(data.url)
                .notes("note")
                .tags("tag")
                .build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, CredentialExcelRow.class)
                .sheet("Credentials")
                .doWrite(List.of(row));

        return ExcelEncryptionUtil.encrypt(out.toByteArray(), EXCEL_PASSWORD);
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<CredentialData> credentialData() {
        Arbitrary<String> names = Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(20).alpha();
        Arbitrary<String> passwords = Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(30).alpha().numeric();
        Arbitrary<String> urls = Arbitraries.of(
                "https://a.com", "https://b.org", "https://c.net",
                "https://d.io", "https://e.dev");

        return Combinators.combine(names, names, passwords, urls)
                .as(CredentialData::new);
    }
}
