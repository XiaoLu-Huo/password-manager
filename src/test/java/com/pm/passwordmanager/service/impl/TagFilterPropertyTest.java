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
import com.pm.passwordmanager.domain.service.PasswordHistoryService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.domain.service.impl.CredentialServiceImpl;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeProperty;

/**
 * Property 10: 标签筛选正确性
 * Validates: Requirements 4.6
 */
@Label("Feature: password-manager, Property 10: 标签筛选正确性")
class TagFilterPropertyTest {

    private static final Long USER_ID = 1L;

    private CredentialRepository credentialRepository;
    private CredentialServiceImpl service;
    private List<Credential> allCredentials;

    @BeforeProperty
    void setUp() {
        credentialRepository = mock(CredentialRepository.class);
        PasswordHistoryService passwordHistoryService = mock(PasswordHistoryService.class);
        EncryptionEngine encryptionEngine = mock(EncryptionEngine.class);
        SessionService sessionService = mock(SessionService.class);
        PasswordGeneratorService passwordGeneratorService = mock(PasswordGeneratorService.class);

        service = new CredentialServiceImpl(
                credentialRepository, passwordHistoryService, encryptionEngine, sessionService, passwordGeneratorService, null);

        when(sessionService.getDek(USER_ID)).thenReturn(new byte[32]);
        when(sessionService.isSessionActive(USER_ID)).thenReturn(true);
        allCredentials = new ArrayList<>();
    }

    @Property(tries = 100)
    @Label("should_returnOnlyCredentialsWithTag_when_filterByTag")
    void should_returnOnlyCredentialsWithTag_when_filterByTag(
            @ForAll("tagFilterScenarios") TagFilterScenario scenario
    ) {
        allCredentials = scenario.credentials;

        when(credentialRepository.filterByTag(eq(USER_ID), anyString())).thenAnswer(inv ->
                allCredentials.stream()
                        .filter(c -> c.getTags() != null && c.getTags().contains(scenario.tag))
                        .collect(Collectors.toList()));

        List<Credential> results = service.filterByTag(USER_ID, scenario.tag);

        Set<Long> expectedIds = allCredentials.stream()
                .filter(c -> c.getTags() != null && c.getTags().contains(scenario.tag))
                .map(Credential::getId).collect(Collectors.toSet());

        Set<Long> actualIds = results.stream().map(Credential::getId).collect(Collectors.toSet());

        assertThat(actualIds).containsAll(expectedIds);
        assertThat(actualIds).isSubsetOf(expectedIds);
        assertThat(results).hasSize(expectedIds.size());

        for (Credential r : results) {
            assertThat(r.getTags()).isNotNull();
            assertThat(r.getTags()).contains(scenario.tag);
        }
    }

    @Property(tries = 100)
    @Label("should_returnAllCredentials_when_tagIsBlank")
    void should_returnAllCredentials_when_tagIsBlank(
            @ForAll("credentialLists") List<Credential> credentials,
            @ForAll("blankTags") String tag
    ) {
        allCredentials = credentials;
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(allCredentials);

        List<Credential> results = service.filterByTag(USER_ID, tag);
        assertThat(results).hasSize(allCredentials.size());
    }

    @Provide
    Arbitrary<TagFilterScenario> tagFilterScenarios() {
        Arbitrary<String> tags = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(10);
        return tags.flatMap(filterTag -> {
            Arbitrary<List<Credential>> creds = credentialWithTag(filterTag)
                    .list().ofMinSize(1).ofMaxSize(20)
                    .map(TagFilterPropertyTest::assignUniqueIds);
            return creds.map(list -> new TagFilterScenario(list, filterTag));
        });
    }

    private Arbitrary<Credential> credentialWithTag(String targetTag) {
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(20);
        Arbitrary<String> urls = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15)
                .map(s -> "https://" + s + ".com");
        Arbitrary<String> otherTags = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(10);
        Arbitrary<Boolean> includeTargetTag = Arbitraries.of(true, false);

        return Combinators.combine(names, names, urls, otherTags, includeTargetTag)
                .as((accountName, username, url, other, include) -> {
                    String tagValue = include ? other + "," + targetTag : other;
                    return Credential.builder()
                            .userId(USER_ID).accountName(accountName).username(username)
                            .url(url).tags(tagValue)
                            .passwordEncrypted(new byte[]{1, 2, 3}).iv(new byte[]{4, 5, 6})
                            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                            .build();
                });
    }

    @Provide
    Arbitrary<List<Credential>> credentialLists() {
        return credentialArbitrary().list().ofMinSize(1).ofMaxSize(20)
                .map(TagFilterPropertyTest::assignUniqueIds);
    }

    private static List<Credential> assignUniqueIds(List<Credential> list) {
        for (int i = 0; i < list.size(); i++) { list.get(i).setId((long) (i + 1)); }
        return list;
    }

    private Arbitrary<Credential> credentialArbitrary() {
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(20);
        Arbitrary<String> urls = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15)
                .map(s -> "https://" + s + ".com");
        Arbitrary<String> tags = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(10);
        return Combinators.combine(names, names, urls, tags)
                .as((accountName, username, url, tag) -> Credential.builder()
                        .userId(USER_ID).accountName(accountName).username(username)
                        .url(url).tags(tag)
                        .passwordEncrypted(new byte[]{1, 2, 3}).iv(new byte[]{4, 5, 6})
                        .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                        .build());
    }

    @Provide
    Arbitrary<String> blankTags() {
        return Arbitraries.of(null, "", "   ", "\t");
    }

    static class TagFilterScenario {
        final List<Credential> credentials;
        final String tag;
        TagFilterScenario(List<Credential> credentials, String tag) {
            this.credentials = credentials;
            this.tag = tag;
        }
        @Override public String toString() {
            return String.format("TagFilterScenario{count=%d, tag='%s'}", credentials.size(), tag);
        }
    }
}
