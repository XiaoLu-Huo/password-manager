package com.pm.passwordmanager.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pm.passwordmanager.api.dto.response.ImportResultResponse;
import com.pm.passwordmanager.api.enums.ConflictStrategy;
import com.pm.passwordmanager.domain.model.Credential;
import com.pm.passwordmanager.domain.repository.CredentialRepository;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.domain.service.impl.ImportExportServiceImpl;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;

/**
 * Property 17: Excel 导出/导入往返一致性
 * 验证：导出后再导入应产生与原始数据等价的凭证记录
 * Validates: Requirements 7.7
 */
@Label("Feature: password-manager, Property 17: Excel 导出/导入往返一致性")
class ExcelRoundTripPropertyTest {

    private static final Long USER_ID = 1L;
    private static final byte[] FAKE_DEK = new byte[32];
    private static final String EXCEL_PASSWORD = "roundtrip";

    /** Simple data carrier for generated credential fields. */
    record CredentialData(String accountName, String username, String password, String url, String notes, String tags) {}

    /**
     * Fresh mocks per invocation to avoid shared mutable state.
     * encrypt/decrypt are identity-based so the round-trip is transparent.
     */
    private record TestFixture(
            CredentialRepository credentialRepository,
            EncryptionEngine encryptionEngine,
            SessionService sessionService,
            ImportExportServiceImpl service,
            List<Credential> savedCredentials
    ) {
        static TestFixture create() {
            CredentialRepository repo = mock(CredentialRepository.class);
            EncryptionEngine enc = mock(EncryptionEngine.class);
            SessionService sess = mock(SessionService.class);

            when(sess.getDek(USER_ID)).thenReturn(FAKE_DEK);

            // Identity-based encrypt: ciphertext = plaintext bytes, iv = fixed marker
            when(enc.encrypt(any(byte[].class), eq(FAKE_DEK)))
                    .thenAnswer(inv -> {
                        byte[] plain = inv.getArgument(0);
                        return new EncryptedData(plain.clone(), new byte[]{1, 2, 3});
                    });

            // Identity-based decrypt: return ciphertext as plaintext
            when(enc.decrypt(any(EncryptedData.class), eq(FAKE_DEK)))
                    .thenAnswer(inv -> {
                        EncryptedData ed = inv.getArgument(0);
                        return ed.getCiphertext().clone();
                    });

            List<Credential> saved = new ArrayList<>();

            // No conflicts during import (empty vault for import phase)
            when(repo.findByUserIdAndAccountName(eq(USER_ID), any()))
                    .thenReturn(Collections.emptyList());

            // Capture saved credentials
            when(repo.save(any(Credential.class))).thenAnswer(inv -> {
                Credential c = inv.getArgument(0);
                saved.add(c);
                return c;
            });

            ImportExportServiceImpl svc = new ImportExportServiceImpl(repo, enc, sess);
            return new TestFixture(repo, enc, sess, svc, saved);
        }
    }

    @Property(tries = 100)
    @Label("should_returnEquivalentData_when_excelExportThenImport")
    void should_returnEquivalentData_when_excelExportThenImport(
            @ForAll("credentialDataList") @Size(min = 1, max = 5) List<CredentialData> dataList
    ) {
        TestFixture f = TestFixture.create();

        // Build original credentials stored in the vault
        List<Credential> originals = new ArrayList<>();
        for (CredentialData d : dataList) {
            byte[] pwBytes = d.password.getBytes(StandardCharsets.UTF_8);
            originals.add(Credential.builder()
                    .userId(USER_ID)
                    .accountName(d.accountName)
                    .username(d.username)
                    .passwordEncrypted(pwBytes)
                    .iv(new byte[]{1, 2, 3})
                    .url(d.url)
                    .notes(d.notes)
                    .tags(d.tags)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build());
        }

        // Export phase: repository returns the originals
        when(f.credentialRepository.findByUserId(USER_ID)).thenReturn(originals);

        byte[] exportedExcel = f.service.exportAsEncryptedExcel(USER_ID, EXCEL_PASSWORD);
        assertThat(exportedExcel).isNotNull().isNotEmpty();

        // Import phase: import into an empty vault
        ImportResultResponse result = f.service.importFromExcel(
                USER_ID, exportedExcel, EXCEL_PASSWORD, ConflictStrategy.SKIP);

        assertThat(result.getTotalCount()).isEqualTo(dataList.size());
        assertThat(result.getImportedCount()).isEqualTo(dataList.size());
        assertThat(result.getSkippedCount()).isEqualTo(0);

        // Verify each imported credential matches the original data
        assertThat(f.savedCredentials).hasSize(dataList.size());

        for (int i = 0; i < dataList.size(); i++) {
            CredentialData original = dataList.get(i);
            Credential imported = f.savedCredentials.get(i);

            assertThat(imported.getAccountName()).isEqualTo(original.accountName);
            assertThat(imported.getUsername()).isEqualTo(original.username);
            // Excel round-trip may convert empty strings to null; treat them as equivalent
            assertThat(normalize(imported.getUrl())).isEqualTo(normalize(original.url));
            assertThat(normalize(imported.getNotes())).isEqualTo(normalize(original.notes));
            assertThat(normalize(imported.getTags())).isEqualTo(normalize(original.tags));

            // Verify password round-trip: the saved encrypted bytes should decode
            // back to the original password (identity encryption)
            String importedPassword = new String(imported.getPasswordEncrypted(), StandardCharsets.UTF_8);
            assertThat(importedPassword).isEqualTo(original.password);
        }
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<List<CredentialData>> credentialDataList() {
        return credentialData().list().ofMinSize(1).ofMaxSize(5)
                .filter(list -> list.stream().map(CredentialData::accountName).distinct().count() == list.size());
    }

    @Provide
    Arbitrary<CredentialData> credentialData() {
        Arbitrary<String> names = Arbitraries.strings().ofMinLength(1).ofMaxLength(20).alpha();
        Arbitrary<String> passwords = Arbitraries.strings().ofMinLength(1).ofMaxLength(30).alpha().numeric();
        Arbitrary<String> urls = Arbitraries.of(
                "https://a.com", "https://b.org", "https://c.net",
                "https://d.io", "https://e.dev");
        Arbitrary<String> notes = Arbitraries.of("note1", "note2", "note3");
        Arbitrary<String> tags = Arbitraries.of("work", "personal", "finance", "social");

        return Combinators.combine(names, names, passwords, urls, notes, tags)
                .as(CredentialData::new);
    }

    /** Normalize null and empty string to the same value for comparison. */
    private static String normalize(String s) {
        return (s == null || s.isEmpty()) ? "" : s;
    }
}
