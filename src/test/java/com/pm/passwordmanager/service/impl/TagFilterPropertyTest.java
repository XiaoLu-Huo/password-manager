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
 * Property 10: 标签筛选正确性
 * For any 标签和凭证集合，按标签筛选的结果应仅包含带有该标签的凭证。
 *
 * Validates: Requirements 4.6
 */
@Label("Feature: password-manager, Property 10: 标签筛选正确性")
class TagFilterPropertyTest {

    private static final Long USER_ID = 1L;

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

        when(sessionService.getDek(USER_ID)).thenReturn(new byte[32]);
        when(sessionService.isSessionActive(USER_ID)).thenReturn(true);

        allCredentials = new ArrayList<>();
    }

    /**
     * 按标签筛选结果仅包含带有该标签的凭证，不包含不带该标签的凭证。
     * 标签以逗号分隔存储，LIKE 匹配。
     *
     * Validates: Requirements 4.6
     */
    @Property(tries = 100)
    @Label("should_returnOnlyCredentialsWithTag_when_filterByTag")
    void should_returnOnlyCredentialsWithTag_when_filterByTag(
            @ForAll("tagFilterScenarios") TagFilterScenario scenario
    ) {
        allCredentials = scenario.credentials;

        // Simulate database LIKE filtering in memory
        when(credentialMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation ->
                allCredentials.stream()
                        .filter(e -> e.getUserId().equals(USER_ID))
                        .filter(e -> e.getTags() != null && e.getTags().contains(scenario.tag))
                        .collect(Collectors.toList()));

        List<CredentialListResponse> results = service.filterByTag(USER_ID, scenario.tag);

        // Compute expected: credentials whose tags field contains the filter tag
        Set<Long> expectedIds = allCredentials.stream()
                .filter(e -> e.getUserId().equals(USER_ID))
                .filter(e -> e.getTags() != null && e.getTags().contains(scenario.tag))
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

        // Every returned credential must have the tag
        for (CredentialListResponse r : results) {
            assertThat(r.getTags()).isNotNull();
            assertThat(r.getTags()).contains(scenario.tag);
        }
    }

    /**
     * 空白标签筛选应返回用户的所有凭证。
     *
     * Validates: Requirements 4.6 (边界条件)
     */
    @Property(tries = 100)
    @Label("should_returnAllCredentials_when_tagIsBlank")
    void should_returnAllCredentials_when_tagIsBlank(
            @ForAll("credentialLists") List<CredentialEntity> credentials,
            @ForAll("blankTags") String tag
    ) {
        allCredentials = credentials;

        when(credentialMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation ->
                allCredentials.stream()
                        .filter(e -> e.getUserId().equals(USER_ID))
                        .collect(Collectors.toList()));

        List<CredentialListResponse> results = service.filterByTag(USER_ID, tag);

        long expectedCount = allCredentials.stream()
                .filter(e -> e.getUserId().equals(USER_ID))
                .count();

        assertThat(results).hasSize((int) expectedCount);
    }

    // --- Providers ---

    @Provide
    Arbitrary<TagFilterScenario> tagFilterScenarios() {
        Arbitrary<String> tags = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(10);

        return tags.flatMap(filterTag -> {
            Arbitrary<List<CredentialEntity>> creds = credentialEntitiesWithTag(filterTag)
                    .list().ofMinSize(1).ofMaxSize(20)
                    .map(TagFilterPropertyTest::assignUniqueIds);
            return creds.map(list -> new TagFilterScenario(list, filterTag));
        });
    }

    /**
     * Generates credential entities where some have the target tag and some don't.
     */
    private Arbitrary<CredentialEntity> credentialEntitiesWithTag(String targetTag) {
        Arbitrary<Long> ids = Arbitraries.longs().between(1, 10000);
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(20);
        Arbitrary<String> urls = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15)
                .map(s -> "https://" + s + ".com");
        // Some credentials will have the target tag, some won't
        Arbitrary<String> otherTags = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(10);
        Arbitrary<Boolean> includeTargetTag = Arbitraries.of(true, false);

        return Combinators.combine(ids, names, names, urls, otherTags, includeTargetTag)
                .as((id, accountName, username, url, other, include) -> {
                    String tagValue = include ? other + "," + targetTag : other;
                    return CredentialEntity.builder()
                            .id(id)
                            .userId(USER_ID)
                            .accountName(accountName)
                            .username(username)
                            .url(url)
                            .tags(tagValue)
                            .passwordEncrypted(new byte[]{1, 2, 3})
                            .iv(new byte[]{4, 5, 6})
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                });
    }

    @Provide
    Arbitrary<List<CredentialEntity>> credentialLists() {
        return credentialEntities().list().ofMinSize(1).ofMaxSize(20)
                .map(TagFilterPropertyTest::assignUniqueIds);
    }

    private static List<CredentialEntity> assignUniqueIds(List<CredentialEntity> list) {
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setId((long) (i + 1));
        }
        return list;
    }

    private Arbitrary<CredentialEntity> credentialEntities() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1, 10000);
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(20);
        Arbitrary<String> urls = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15)
                .map(s -> "https://" + s + ".com");
        Arbitrary<String> tags = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(10);

        return Combinators.combine(ids, names, names, urls, tags)
                .as((id, accountName, username, url, tag) ->
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

    @Provide
    Arbitrary<String> blankTags() {
        return Arbitraries.of(null, "", "   ", "\t");
    }

    // --- Helper class ---

    static class TagFilterScenario {
        final List<CredentialEntity> credentials;
        final String tag;

        TagFilterScenario(List<CredentialEntity> credentials, String tag) {
            this.credentials = credentials;
            this.tag = tag;
        }

        @Override
        public String toString() {
            return String.format("TagFilterScenario{credentialCount=%d, tag='%s'}", credentials.size(), tag);
        }
    }
}
