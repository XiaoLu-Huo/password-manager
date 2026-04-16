package com.pm.passwordmanager.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pm.passwordmanager.domain.model.Credential;
import com.pm.passwordmanager.domain.repository.CredentialRepository;
import com.pm.passwordmanager.domain.service.PasswordGeneratorService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.domain.service.impl.CredentialServiceImpl;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;
import com.pm.passwordmanager.infrastructure.persistence.mapper.PasswordHistoryMapper;

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
 * Validates: Requirements 4.2
 */
@Label("Feature: password-manager, Property 9: 搜索结果正确性")
class CredentialSearchPropertyTest {

    private static final Long USER_ID = 1L;

    private CredentialRepository credentialRepository;
    private CredentialServiceImpl service;
    private List<Credential> allCredentials;

    @BeforeProperty
    void setUp() {
        credentialRepository = mock(CredentialRepository.class);
        PasswordHistoryMapper passwordHistoryMapper = mock(PasswordHistoryMapper.class);
        EncryptionEngine encryptionEngine = mock(EncryptionEngine.class);
        SessionService sessionService = mock(SessionService.class);
        PasswordGeneratorService passwordGeneratorService = mock(PasswordGeneratorService.class);

        service = new CredentialServiceImpl(
                credentialRepository, passwordHistoryMapper, encryptionEngine, sessionService, passwordGeneratorService, null);

        when(sessionService.getDek(USER_ID)).thenReturn(new byte[32]);
        when(sessionService.isSessionActive(USER_ID)).thenReturn(true);
        allCredentials = new ArrayList<>();
    }

    @Property(tries = 100)
    @Label("should_returnAllMatchingAndNoNonMatching_when_searchByKeyword")
    void should_returnAllMatchingAndNoNonMatching_when_searchByKeyword(
            @ForAll("searchScenarios") SearchScenario scenario
    ) {
        allCredentials = scenario.credentials;
        String kw = scenario.keyword.toLowerCase();

        // Mock repository to simulate LIKE search
        when(credentialRepository.searchByKeyword(eq(USER_ID), anyString())).thenAnswer(inv ->
                allCredentials.stream()
                        .filter(c -> containsIgnoreCase(c.getAccountName(), kw)
                                || containsIgnoreCase(c.getUsername(), kw)
                                || containsIgnoreCase(c.getUrl(), kw))
                        .collect(Collectors.toList()));

        List<Credential> results = service.searchCredentials(USER_ID, scenario.keyword);

        Set<Long> expectedIds = allCredentials.stream()
                .filter(c -> containsIgnoreCase(c.getAccountName(), kw)
                        || containsIgnoreCase(c.getUsername(), kw)
                        || containsIgnoreCase(c.getUrl(), kw))
                .map(Credential::getId).collect(Collectors.toSet());

        Set<Long> actualIds = results.stream().map(Credential::getId).collect(Collectors.toSet());

        assertThat(actualIds).containsAll(expectedIds);
        assertThat(actualIds).isSubsetOf(expectedIds);
        assertThat(results).hasSize(expectedIds.size());
    }

    @Property(tries = 100)
    @Label("should_returnAllCredentials_when_keywordIsBlank")
    void should_returnAllCredentials_when_keywordIsBlank(
            @ForAll("credentialLists") List<Credential> credentials,
            @ForAll("blankKeywords") String keyword
    ) {
        allCredentials = credentials;
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(allCredentials);

        List<Credential> results = service.searchCredentials(USER_ID, keyword);
        assertThat(results).hasSize(allCredentials.size());
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        return text != null && keyword != null && text.toLowerCase().contains(keyword);
    }

    @Provide
    Arbitrary<SearchScenario> searchScenarios() {
        Arbitrary<String> keywords = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10);
        return keywords.flatMap(kw -> {
            Arbitrary<List<Credential>> creds = credentialLists();
            return creds.map(list -> {
                if (!list.isEmpty() && list.stream().noneMatch(c ->
                        containsIgnoreCase(c.getAccountName(), kw.toLowerCase())
                                || containsIgnoreCase(c.getUsername(), kw.toLowerCase())
                                || containsIgnoreCase(c.getUrl(), kw.toLowerCase()))) {
                    list.get(0).setAccountName(list.get(0).getAccountName() + kw);
                }
                return new SearchScenario(list, kw);
            });
        });
    }

    @Provide
    Arbitrary<List<Credential>> credentialLists() {
        return credentialArbitrary().list().ofMinSize(1).ofMaxSize(20).map(list -> {
            long id = 1;
            for (Credential c : list) { c.setId(id++); }
            return list;
        });
    }

    @Provide
    Arbitrary<String> blankKeywords() {
        return Arbitraries.of(null, "", "   ", "\t");
    }

    private Arbitrary<Credential> credentialArbitrary() {
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(30);
        Arbitrary<String> urls = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30)
                .map(s -> "https://" + s + ".com");
        return Combinators.combine(names, names, names, urls)
                .as((accountName, username, tag, url) -> Credential.builder()
                        .userId(USER_ID).accountName(accountName).username(username)
                        .url(url).tags(tag)
                        .passwordEncrypted(new byte[]{1, 2, 3}).iv(new byte[]{4, 5, 6})
                        .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                        .build());
    }

    static class SearchScenario {
        final List<Credential> credentials;
        final String keyword;
        SearchScenario(List<Credential> credentials, String keyword) {
            this.credentials = credentials;
            this.keyword = keyword;
        }
        @Override public String toString() {
            return String.format("SearchScenario{count=%d, keyword='%s'}", credentials.size(), keyword);
        }
    }
}
