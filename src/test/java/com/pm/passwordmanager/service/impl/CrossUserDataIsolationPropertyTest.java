package com.pm.passwordmanager.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.mockito.Mockito;

import com.pm.passwordmanager.domain.assembler.CredentialModelAssembler;
import com.pm.passwordmanager.domain.model.Credential;
import com.pm.passwordmanager.domain.repository.CredentialRepository;
import com.pm.passwordmanager.domain.service.PasswordGeneratorService;
import com.pm.passwordmanager.domain.service.PasswordHistoryService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Feature: multi-user-platform, Property 8: 数据隔离（Cross-User Data Isolation）
 *
 * For any 两个不同的注册用户 A 和 B，用户 A 创建的凭证在用户 B 的会话中不可见，
 * 且用户 B 尝试通过 ID 直接访问用户 A 的资源时应返回"资源不存在"错误。
 *
 * Validates: Requirements 5.1, 5.2, 5.3, 5.4
 */
@Label("Feature: multi-user-platform, Property 8: 数据隔离（Cross-User Data Isolation）")
class CrossUserDataIsolationPropertyTest {

    private static final Long USER_A_ID = 1L;
    private static final Long USER_B_ID = 2L;

    /**
     * 用户 A 创建的凭证在用户 B 的 listCredentials 中不可见。
     */
    @Property(tries = 100)
    @Label("should_notListUserACredentials_when_queryingAsUserB")
    void should_notListUserACredentials_when_queryingAsUserB(
            @ForAll("credentialData") CredentialData data
    ) {
        // Setup mocks
        CredentialRepository credentialRepository = Mockito.mock(CredentialRepository.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        EncryptionEngine encryptionEngine = new EncryptionEngine();
        PasswordHistoryService passwordHistoryService = Mockito.mock(PasswordHistoryService.class);
        PasswordGeneratorService passwordGeneratorService = Mockito.mock(PasswordGeneratorService.class);
        CredentialModelAssembler credentialModelAssembler = Mockito.mock(CredentialModelAssembler.class);

        CredentialServiceImpl credentialService = new CredentialServiceImpl(
                credentialRepository, passwordHistoryService, encryptionEngine,
                sessionService, passwordGeneratorService, credentialModelAssembler);

        // Both users have active sessions with their own DEKs
        byte[] dekA = encryptionEngine.generateDek();
        byte[] dekB = encryptionEngine.generateDek();
        when(sessionService.getDek(USER_A_ID)).thenReturn(dekA);
        when(sessionService.getDek(USER_B_ID)).thenReturn(dekB);
        when(sessionService.isSessionActive(USER_A_ID)).thenReturn(true);
        when(sessionService.isSessionActive(USER_B_ID)).thenReturn(true);

        // User A creates a credential — simulate the saved credential
        Credential userACredential = Credential.builder()
                .id(100L)
                .userId(USER_A_ID)
                .accountName(data.accountName)
                .username(data.username)
                .passwordEncrypted(new byte[]{1, 2, 3})
                .iv(new byte[]{4, 5, 6})
                .url(data.url)
                .tags(data.tag)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Repository returns User A's credential only for User A's userId
        when(credentialRepository.findByUserId(USER_A_ID)).thenReturn(List.of(userACredential));
        // Repository returns empty list for User B's userId
        when(credentialRepository.findByUserId(USER_B_ID)).thenReturn(Collections.emptyList());

        // Verify: User B's list does not contain User A's credential
        List<Credential> userBCredentials = credentialService.listCredentials(USER_B_ID);
        assertThat(userBCredentials).isEmpty();

        // Verify: User A can see their own credential
        List<Credential> userACredentials = credentialService.listCredentials(USER_A_ID);
        assertThat(userACredentials).hasSize(1);
        assertThat(userACredentials.get(0).getId()).isEqualTo(100L);
    }

    /**
     * 用户 B 通过凭证 ID 直接访问用户 A 的凭证时，应返回 CREDENTIAL_NOT_FOUND。
     */
    @Property(tries = 100)
    @Label("should_throwCredentialNotFound_when_userBAccessesUserACredentialById")
    void should_throwCredentialNotFound_when_userBAccessesUserACredentialById(
            @ForAll("credentialData") CredentialData data
    ) {
        CredentialRepository credentialRepository = Mockito.mock(CredentialRepository.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        EncryptionEngine encryptionEngine = new EncryptionEngine();
        PasswordHistoryService passwordHistoryService = Mockito.mock(PasswordHistoryService.class);
        PasswordGeneratorService passwordGeneratorService = Mockito.mock(PasswordGeneratorService.class);
        CredentialModelAssembler credentialModelAssembler = Mockito.mock(CredentialModelAssembler.class);

        CredentialServiceImpl credentialService = new CredentialServiceImpl(
                credentialRepository, passwordHistoryService, encryptionEngine,
                sessionService, passwordGeneratorService, credentialModelAssembler);

        byte[] dekB = encryptionEngine.generateDek();
        when(sessionService.getDek(USER_B_ID)).thenReturn(dekB);
        when(sessionService.isSessionActive(USER_B_ID)).thenReturn(true);

        // User A's credential exists in the repository
        Credential userACredential = Credential.builder()
                .id(100L)
                .userId(USER_A_ID)
                .accountName(data.accountName)
                .username(data.username)
                .passwordEncrypted(new byte[]{1, 2, 3})
                .iv(new byte[]{4, 5, 6})
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(credentialRepository.findById(100L)).thenReturn(Optional.of(userACredential));

        // User B tries to access User A's credential by ID → CREDENTIAL_NOT_FOUND
        assertThatThrownBy(() -> credentialService.getCredential(USER_B_ID, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREDENTIAL_NOT_FOUND);
    }

    /**
     * 用户 B 尝试删除用户 A 的凭证时，应返回 CREDENTIAL_NOT_FOUND。
     */
    @Property(tries = 100)
    @Label("should_throwCredentialNotFound_when_userBDeletesUserACredential")
    void should_throwCredentialNotFound_when_userBDeletesUserACredential(
            @ForAll("credentialData") CredentialData data
    ) {
        CredentialRepository credentialRepository = Mockito.mock(CredentialRepository.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        EncryptionEngine encryptionEngine = new EncryptionEngine();
        PasswordHistoryService passwordHistoryService = Mockito.mock(PasswordHistoryService.class);
        PasswordGeneratorService passwordGeneratorService = Mockito.mock(PasswordGeneratorService.class);
        CredentialModelAssembler credentialModelAssembler = Mockito.mock(CredentialModelAssembler.class);

        CredentialServiceImpl credentialService = new CredentialServiceImpl(
                credentialRepository, passwordHistoryService, encryptionEngine,
                sessionService, passwordGeneratorService, credentialModelAssembler);

        when(sessionService.isSessionActive(USER_B_ID)).thenReturn(true);

        Credential userACredential = Credential.builder()
                .id(100L)
                .userId(USER_A_ID)
                .accountName(data.accountName)
                .username(data.username)
                .passwordEncrypted(new byte[]{1, 2, 3})
                .iv(new byte[]{4, 5, 6})
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(credentialRepository.findById(100L)).thenReturn(Optional.of(userACredential));

        // User B tries to delete User A's credential → CREDENTIAL_NOT_FOUND
        assertThatThrownBy(() -> credentialService.deleteCredential(USER_B_ID, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREDENTIAL_NOT_FOUND);
    }

    /**
     * 用户 B 尝试查看用户 A 凭证的密码明文时，应返回 CREDENTIAL_NOT_FOUND。
     */
    @Property(tries = 100)
    @Label("should_throwCredentialNotFound_when_userBRevealsUserAPassword")
    void should_throwCredentialNotFound_when_userBRevealsUserAPassword(
            @ForAll("credentialData") CredentialData data
    ) {
        CredentialRepository credentialRepository = Mockito.mock(CredentialRepository.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        EncryptionEngine encryptionEngine = new EncryptionEngine();
        PasswordHistoryService passwordHistoryService = Mockito.mock(PasswordHistoryService.class);
        PasswordGeneratorService passwordGeneratorService = Mockito.mock(PasswordGeneratorService.class);
        CredentialModelAssembler credentialModelAssembler = Mockito.mock(CredentialModelAssembler.class);

        CredentialServiceImpl credentialService = new CredentialServiceImpl(
                credentialRepository, passwordHistoryService, encryptionEngine,
                sessionService, passwordGeneratorService, credentialModelAssembler);

        byte[] dekB = encryptionEngine.generateDek();
        when(sessionService.getDek(USER_B_ID)).thenReturn(dekB);

        Credential userACredential = Credential.builder()
                .id(100L)
                .userId(USER_A_ID)
                .accountName(data.accountName)
                .username(data.username)
                .passwordEncrypted(new byte[]{1, 2, 3})
                .iv(new byte[]{4, 5, 6})
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(credentialRepository.findById(100L)).thenReturn(Optional.of(userACredential));

        // User B tries to reveal User A's credential password → CREDENTIAL_NOT_FOUND
        assertThatThrownBy(() -> credentialService.revealPassword(USER_B_ID, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREDENTIAL_NOT_FOUND);
    }

    /**
     * 用户 A 的搜索结果不包含用户 B 的凭证。
     */
    @Property(tries = 100)
    @Label("should_notReturnCrossUserResults_when_searchingCredentials")
    void should_notReturnCrossUserResults_when_searchingCredentials(
            @ForAll("credentialData") CredentialData data
    ) {
        CredentialRepository credentialRepository = Mockito.mock(CredentialRepository.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        EncryptionEngine encryptionEngine = new EncryptionEngine();
        PasswordHistoryService passwordHistoryService = Mockito.mock(PasswordHistoryService.class);
        PasswordGeneratorService passwordGeneratorService = Mockito.mock(PasswordGeneratorService.class);
        CredentialModelAssembler credentialModelAssembler = Mockito.mock(CredentialModelAssembler.class);

        CredentialServiceImpl credentialService = new CredentialServiceImpl(
                credentialRepository, passwordHistoryService, encryptionEngine,
                sessionService, passwordGeneratorService, credentialModelAssembler);

        when(sessionService.isSessionActive(USER_A_ID)).thenReturn(true);
        when(sessionService.isSessionActive(USER_B_ID)).thenReturn(true);

        // Repository enforces userId filtering — User B's search returns empty
        when(credentialRepository.searchByKeyword(USER_B_ID, data.accountName))
                .thenReturn(Collections.emptyList());
        // User A's search returns their own credential
        Credential userACredential = Credential.builder()
                .id(100L).userId(USER_A_ID).accountName(data.accountName)
                .username(data.username).build();
        when(credentialRepository.searchByKeyword(USER_A_ID, data.accountName))
                .thenReturn(List.of(userACredential));

        List<Credential> userBResults = credentialService.searchCredentials(USER_B_ID, data.accountName);
        assertThat(userBResults).isEmpty();

        List<Credential> userAResults = credentialService.searchCredentials(USER_A_ID, data.accountName);
        assertThat(userAResults).hasSize(1);
    }

    /**
     * 用户 A 的标签筛选结果不包含用户 B 的凭证。
     */
    @Property(tries = 100)
    @Label("should_notReturnCrossUserResults_when_filteringByTag")
    void should_notReturnCrossUserResults_when_filteringByTag(
            @ForAll("credentialData") CredentialData data
    ) {
        CredentialRepository credentialRepository = Mockito.mock(CredentialRepository.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        EncryptionEngine encryptionEngine = new EncryptionEngine();
        PasswordHistoryService passwordHistoryService = Mockito.mock(PasswordHistoryService.class);
        PasswordGeneratorService passwordGeneratorService = Mockito.mock(PasswordGeneratorService.class);
        CredentialModelAssembler credentialModelAssembler = Mockito.mock(CredentialModelAssembler.class);

        CredentialServiceImpl credentialService = new CredentialServiceImpl(
                credentialRepository, passwordHistoryService, encryptionEngine,
                sessionService, passwordGeneratorService, credentialModelAssembler);

        when(sessionService.isSessionActive(USER_A_ID)).thenReturn(true);
        when(sessionService.isSessionActive(USER_B_ID)).thenReturn(true);

        // Repository enforces userId filtering
        when(credentialRepository.filterByTag(USER_B_ID, data.tag)).thenReturn(Collections.emptyList());
        Credential userACredential = Credential.builder()
                .id(100L).userId(USER_A_ID).accountName(data.accountName)
                .username(data.username).tags(data.tag).build();
        when(credentialRepository.filterByTag(USER_A_ID, data.tag))
                .thenReturn(List.of(userACredential));

        List<Credential> userBResults = credentialService.filterByTag(USER_B_ID, data.tag);
        assertThat(userBResults).isEmpty();

        List<Credential> userAResults = credentialService.filterByTag(USER_A_ID, data.tag);
        assertThat(userAResults).hasSize(1);
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<CredentialData> credentialData() {
        Arbitrary<String> accountNames = Arbitraries.strings()
                .withCharRange('a', 'z').ofMinLength(3).ofMaxLength(16);
        Arbitrary<String> usernames = Arbitraries.strings()
                .withCharRange('a', 'z').ofMinLength(3).ofMaxLength(12);
        Arbitrary<String> urls = Arbitraries.strings()
                .withCharRange('a', 'z').ofMinLength(5).ofMaxLength(12)
                .map(s -> "https://" + s + ".com");
        Arbitrary<String> tags = Arbitraries.of("work", "personal", "finance", "social", "dev");

        return Combinators.combine(accountNames, usernames, urls, tags)
                .as(CredentialData::new);
    }

    /**
     * Helper record to bundle generated credential data.
     */
    static class CredentialData {
        final String accountName;
        final String username;
        final String url;
        final String tag;

        CredentialData(String accountName, String username, String url, String tag) {
            this.accountName = accountName;
            this.username = username;
            this.url = url;
            this.tag = tag;
        }
    }
}
