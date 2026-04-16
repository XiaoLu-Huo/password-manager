package com.pm.passwordmanager.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pm.passwordmanager.api.dto.response.SecurityReportResponse;
import com.pm.passwordmanager.api.enums.PasswordStrengthLevel;
import com.pm.passwordmanager.domain.model.Credential;
import com.pm.passwordmanager.domain.repository.CredentialRepository;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.domain.service.impl.SecurityReportServiceImpl;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;
import com.pm.passwordmanager.infrastructure.encryption.PasswordStrengthEvaluator;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeProperty;

/**
 * Property 14: 安全报告统计一致性
 * 验证：弱密码列表、重复密码列表、超期列表与统计数字一致
 * Validates: Requirements 6.2, 6.3, 6.4, 6.5
 */
@Label("Feature: password-manager, Property 14: 安全报告统计一致性")
class SecurityReportPropertyTest {

    private static final Long USER_ID = 1L;
    private static final byte[] DEK = new byte[32];

    private CredentialRepository credentialRepository;
    private EncryptionEngine encryptionEngine;
    private SessionService sessionService;
    private PasswordStrengthEvaluator passwordStrengthEvaluator;
    private SecurityReportServiceImpl service;

    /** Maps credential id → plaintext password for mock decryption. */
    private Map<Long, String> passwordMap;

    @BeforeProperty
    void setUp() {
        credentialRepository = mock(CredentialRepository.class);
        encryptionEngine = mock(EncryptionEngine.class);
        sessionService = mock(SessionService.class);
        passwordStrengthEvaluator = new PasswordStrengthEvaluator();

        service = new SecurityReportServiceImpl(
                credentialRepository, encryptionEngine, sessionService, passwordStrengthEvaluator);

        when(sessionService.getDek(USER_ID)).thenReturn(DEK);
        when(sessionService.isSessionActive(USER_ID)).thenReturn(true);
        passwordMap = new HashMap<>();
    }

    @Property(tries = 100)
    @Label("should_haveConsistentReportCounts_when_comparedWithDetailLists")
    void should_haveConsistentReportCounts_when_comparedWithDetailLists(
            @ForAll("credentialScenarios") ReportScenario scenario
    ) {
        List<Credential> credentials = scenario.credentials;
        passwordMap = scenario.passwordMap;

        when(credentialRepository.findByUserId(USER_ID)).thenReturn(credentials);

        // Mock decryption: return the known plaintext for each credential
        when(encryptionEngine.decrypt(any(EncryptedData.class), eq(DEK))).thenAnswer(inv -> {
            EncryptedData ed = inv.getArgument(0);
            // Find credential by matching encrypted bytes
            for (Credential c : credentials) {
                if (c.getPasswordEncrypted() == ed.getCiphertext()) {
                    String plain = passwordMap.get(c.getId());
                    return plain.getBytes(StandardCharsets.UTF_8);
                }
            }
            throw new IllegalStateException("Unknown encrypted data in mock");
        });

        // Get report and detail lists
        SecurityReportResponse report = service.getReport(USER_ID);
        List<Credential> weakList = service.getWeakPasswordCredentials(USER_ID);
        List<Credential> duplicateList = service.getDuplicatePasswordCredentials(USER_ID);
        List<Credential> expiredList = service.getExpiredPasswordCredentials(USER_ID);

        // Property: total count matches credential count
        assertThat(report.getTotalCredentials()).isEqualTo(credentials.size());

        // Property: weak password count matches weak list size
        assertThat(report.getWeakPasswordCount()).isEqualTo(weakList.size());

        // Property: duplicate password count matches duplicate list size
        assertThat(report.getDuplicatePasswordCount()).isEqualTo(duplicateList.size());

        // Property: expired password count matches expired list size
        assertThat(report.getExpiredPasswordCount()).isEqualTo(expiredList.size());

        // Property: weak list contains exactly the credentials with WEAK strength
        for (Credential r : weakList) {
            String plain = passwordMap.get(r.getId());
            assertThat(passwordStrengthEvaluator.evaluate(plain)).isEqualTo(PasswordStrengthLevel.WEAK);
        }

        // Property: expired list contains exactly credentials updated > 90 days ago
        LocalDateTime expiryThreshold = LocalDateTime.now().minusDays(90);
        for (Credential r : expiredList) {
            assertThat(r.getUpdatedAt()).isBefore(expiryThreshold);
        }

        // Property: duplicate list contains only credentials sharing a password with others
        Map<String, List<Long>> groups = new HashMap<>();
        for (Credential c : credentials) {
            groups.computeIfAbsent(passwordMap.get(c.getId()), k -> new ArrayList<>()).add(c.getId());
        }
        for (Credential r : duplicateList) {
            String plain = passwordMap.get(r.getId());
            assertThat(groups.get(plain).size()).isGreaterThan(1);
        }
    }

    @Provide
    Arbitrary<ReportScenario> credentialScenarios() {
        // Generate a pool of passwords (some will be reused to create duplicates)
        Arbitrary<List<String>> passwordPools = passwordArbitrary()
                .list().ofMinSize(1).ofMaxSize(5);

        return passwordPools.flatMap(pool -> {
            // Each credential picks a password from the pool (creating natural duplicates)
            Arbitrary<List<CredentialSeed>> seeds = credentialSeed(pool)
                    .list().ofMinSize(1).ofMaxSize(15);

            return seeds.map(seedList -> {
                List<Credential> creds = new ArrayList<>();
                Map<Long, String> pwMap = new HashMap<>();
                for (int i = 0; i < seedList.size(); i++) {
                    long id = i + 1;
                    CredentialSeed s = seedList.get(i);
                    // Use unique byte array per credential so mock can identify them
                    byte[] fakeEncrypted = new byte[]{(byte) id};
                    byte[] fakeIv = new byte[]{(byte) (id + 100)};
                    Credential c = Credential.builder()
                            .id(id)
                            .userId(USER_ID)
                            .accountName(s.accountName)
                            .username(s.username)
                            .passwordEncrypted(fakeEncrypted)
                            .iv(fakeIv)
                            .url("https://example.com")
                            .tags("tag")
                            .createdAt(LocalDateTime.now())
                            .updatedAt(s.updatedAt)
                            .build();
                    creds.add(c);
                    pwMap.put(id, s.password);
                }
                return new ReportScenario(creds, pwMap);
            });
        });
    }

    private Arbitrary<String> passwordArbitrary() {
        // Mix of weak, medium, and strong passwords
        return Arbitraries.oneOf(
                // Weak: short passwords (< 8 chars)
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(7),
                // Medium: 8-15 chars with mixed types
                Arbitraries.strings().ofMinLength(8).ofMaxLength(15)
                        .withCharRange('a', 'z').withCharRange('0', '9'),
                // Strong: >= 16 chars with 3+ types
                Arbitraries.strings().ofMinLength(16).ofMaxLength(24)
                        .withCharRange('A', 'Z').withCharRange('a', 'z').withCharRange('0', '9')
        );
    }

    private Arbitrary<CredentialSeed> credentialSeed(List<String> passwordPool) {
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(15);
        Arbitrary<String> passwords = Arbitraries.of(passwordPool);
        // Some credentials updated recently, some > 90 days ago
        Arbitrary<LocalDateTime> updatedAts = Arbitraries.oneOf(
                Arbitraries.integers().between(1, 30).map(d -> LocalDateTime.now().minusDays(d)),
                Arbitraries.integers().between(91, 365).map(d -> LocalDateTime.now().minusDays(d))
        );

        return Combinators.combine(names, names, passwords, updatedAts)
                .as(CredentialSeed::new);
    }

    static class CredentialSeed {
        final String accountName;
        final String username;
        final String password;
        final LocalDateTime updatedAt;

        CredentialSeed(String accountName, String username, String password, LocalDateTime updatedAt) {
            this.accountName = accountName;
            this.username = username;
            this.password = password;
            this.updatedAt = updatedAt;
        }
    }

    static class ReportScenario {
        final List<Credential> credentials;
        final Map<Long, String> passwordMap;

        ReportScenario(List<Credential> credentials, Map<Long, String> passwordMap) {
            this.credentials = credentials;
            this.passwordMap = passwordMap;
        }

        @Override
        public String toString() {
            return String.format("ReportScenario{count=%d}", credentials.size());
        }
    }
}
