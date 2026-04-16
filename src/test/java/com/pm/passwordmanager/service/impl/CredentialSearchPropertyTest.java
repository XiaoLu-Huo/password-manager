package com.pm.passwordmanager.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pm.passwordmanager.dto.response.CredentialListResponse;
import com.pm.passwordmanager.entity.CredentialEntity;
import com.pm.passwordmanager.mapper.CredentialMapper;
import com.pm.passwordmanager.mapper.PasswordHistoryMapper;
import com.pm.passwordmanager.service.PasswordGeneratorService;
import com.pm.passwordmanager.service.SessionService;
import com.pm.passwordmanager.util.EncryptionEngine;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeProperty;

/**
 * Property 9: 搜索结果正确性
 * For any 搜索关键词和凭证集合，搜索结果应包含所有账户名称、用户名或关联 URL 中包含该关键词的凭证，
 * 且不包含不匹配的凭证。
 *
 * Validates: Requirements 4.2
 */
@Label("Feature: password-manager, Property 9: 搜索结果正确性")
class CredentialSearchPropertyTest {

    private static final Long USER_ID = 1L;
    private static final byte[] FAKE_DEK = new byte[32];

    private CredentialMapper credentialMapper;
    private CredentialServiceImpl service;
    private List<CredentialEntity> allCredentials;

    @BeforeProperty
    void setUp() {
        credentialMapper = mock(CredentialMapper.class);
        PasswordHistoryMapper passwordHistoryMapper = mock(PasswordHistoryMapper.class);
        EncryptionEngine encryptionEngine = mock(EncryptionEngine.class);
        SessionService sessionService = mock(SessionService.class);
        PasswordGeneratorService passwordGeneratorService = mock(PasswordGeneratorService.class);

        service = new CredentialServiceImpl(
                credentialMapper, passwordHistoryMapper, encryptionEngine, sessionService, passwordGeneratorService);

        when(sessionService.getDek(USER_ID)).thenReturn(FAKE_DEK);
        when(sessionService.isSessionActive(USER_ID)).thenReturn(true);

        allCredentials = new ArrayList<>();
    }

    /**
     * 搜索结果应包含所有匹配凭证，不包含不匹配凭证。
     * 匹配规则：关键词出现在 accountName、username 或 url 中（不区分大小写）。
     *
     * Validates: Requirements 4.2
     */
    @Property(tries = 100)
    @Label("should_returnAllMatchingAndNoNonMatching_when_searchByKeyword")
    void should_returnAllMatchingAndNoNonMatching_when_searchByKeyword(
            @ForAll("searchScenarios") SearchScenario scenario
    ) {
        allCredentials = scenario.credentials;

        // Simulate database LIKE filtering: mock selectList to perform in-memory LIKE matching
        when(credentialMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            String kw = scenario.keyword.toLowerCase();
            return allCredentials.stream()
                    .filter(e -> e.getUserId().equals(USER_ID))
                    .filter(e -> containsIgnoreCase(e.getAccountName(), kw)
                            || containsIgnoreCase(e.getUsername(), kw)
                            || containsIgnoreCase(e.getUrl(), kw))
                    .collect(Collectors.toList());
        });

        List<CredentialListResponse> results = service.searchCredentials(USER_ID, scenario.keyword);

        // Compute expected matching IDs using the same logic
        String kw = scenario.keyword.toLowerCase();
        Set<Long> expectedIds = allCredentials.stream()
                .filter(e -> e.getUserId().equals(USER_ID))
                .filter(e -> containsIgnoreCase(e.getAccountName(), kw)
                        || containsIgnoreCase(e.getUsername(), kw)
                        || containsIgnoreCase(e.getUrl(), kw))
                .map(CredentialEntity::getId)
                .collect(Collectors.toSet());

        Set<Long> actualIds = results.stream()
                .map(CredentialListResponse::getId)
                .collect(Collectors.toSet());

        // All matching credentials are included
        assertThat(actualIds).containsAll(expectedIds);
        // No non-matching credentials are included
        assertThat(actualIds).isSubsetOf(expectedIds);
        // Result count matches
        assertThat(results).hasSize(expectedIds.size());

        // Verify each result has correct summary fields
        for (CredentialListResponse r : results) {
            CredentialEntity source = allCredentials.stream()
                    .filter(e -> e.getId().equals(r.getId()))
                    .findFirst()
                    .orElse(null);
            assertThat(source).isNotNull();
            assertThat(r.getAccountName()).isEqualTo(source.getAccountName());
            assertThat(r.getUsername()).isEqualTo(source.getUsername());
            assertThat(r.getUrl()).isEqualTo(source.getUrl());
        }
    }

    /**
     * 空白关键词搜索应返回用户的所有凭证。
     *
     * Validates: Requirements 4.2 (边界条件)
     */
    @Property(tries = 100)
    @Label("should_returnAllCredentials_when_keywordIsBlank")
    void should_returnAllCredentials_when_keywordIsBlank(
            @ForAll("credentialLists") List<CredentialEntity> credentials,
            @ForAll("blankKeywords") String keyword
    ) {
        allCredentials = credentials;

        // For blank keyword, searchCredentials delegates to listCredentials which queries all
        when(credentialMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation ->
                allCredentials.stream()
                        .filter(e -> e.getUserId().equals(USER_ID))
                        .collect(Collectors.toList()));

        List<CredentialListResponse> results = service.searchCredentials(USER_ID, keyword);

        long expectedCount = allCredentials.stream()
                .filter(e -> e.getUserId().equals(USER_ID))
                .count();

        assertThat(results).hasSize((int) expectedCount);
    }

    // --- Helper methods ---

    private boolean containsIgnoreCase(String text, String keyword) {
        if (text == null || keyword == null) {
            return false;
        }
        return text.toLowerCase().contains(keyword);
    }

    // --- Providers ---

    @Provide
    Arbitrary<SearchScenario> searchScenarios() {
        Arbitrary<List<CredentialEntity>> credentials = credentialLists();
        Arbitrary<String> keywords = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(10);

        return Combinators.combine(credentials, keywords).as((creds, kw) -> {
            // Ensure at least some credentials contain the keyword for meaningful tests
            if (!creds.isEmpty() && creds.stream().noneMatch(c ->
                    containsIgnoreCase(c.getAccountName(), kw.toLowerCase())
                            || containsIgnoreCase(c.getUsername(), kw.toLowerCase())
                            || containsIgnoreCase(c.getUrl(), kw.toLowerCase()))) {
                // Inject the keyword into a random credential's accountName
                CredentialEntity target = creds.get(0);
                target.setAccountName(target.getAccountName() + kw);
            }
            return new SearchScenario(creds, kw);
        });
    }

    @Provide
    Arbitrary<List<CredentialEntity>> credentialLists() {
        return credentialEntities().list().ofMinSize(1).ofMaxSize(20)
                .map(list -> {
                    // Ensure unique IDs to avoid lookup collisions in assertions
                    long id = 1;
                    for (CredentialEntity e : list) {
                        e.setId(id++);
                    }
                    return list;
                });
    }

    @Provide
    Arbitrary<String> blankKeywords() {
        return Arbitraries.of(null, "", "   ", "\t");
    }

    private Arbitrary<CredentialEntity> credentialEntities() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1, 10000);
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(30);
        Arbitrary<String> urls = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30)
                .map(s -> "https://" + s + ".com");

        return Combinators.combine(ids, names, names, names, urls)
                .as((id, accountName, username, tag, url) ->
                        CredentialEntity.builder()
                                .id(id)
                                .userId(USER_ID)
                                .accountName(accountName)
                                .username(username)
                                .url(url)
                                .tags(tag)
                                .passwordEncrypted(new byte[]{1, 2, 3})
                                .iv(new byte[]{4, 5, 6})
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build());
    }

    // --- Helper class ---

    static class SearchScenario {
        final List<CredentialEntity> credentials;
        final String keyword;

        SearchScenario(List<CredentialEntity> credentials, String keyword) {
            this.credentials = credentials;
            this.keyword = keyword;
        }

        @Override
        public String toString() {
            return String.format("SearchScenario{credentialCount=%d, keyword='%s'}",
                    credentials.size(), keyword);
        }
    }
}
