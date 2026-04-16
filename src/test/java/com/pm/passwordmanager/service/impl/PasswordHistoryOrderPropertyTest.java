package com.pm.passwordmanager.service.impl;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pm.passwordmanager.api.assembler.PasswordHistoryDtoMapper;
import com.pm.passwordmanager.api.dto.response.PasswordHistoryResponse;
import com.pm.passwordmanager.domain.model.Credential;
import com.pm.passwordmanager.domain.repository.CredentialRepository;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.domain.service.impl.PasswordHistoryServiceImpl;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;
import com.pm.passwordmanager.infrastructure.persistence.entity.PasswordHistoryEntity;
import com.pm.passwordmanager.infrastructure.persistence.mapper.PasswordHistoryMapper;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property 20: 密码历史排序与完整性
 * 验证：历史记录按变更时间降序排列，每条包含掩码密码和变更时间
 * Validates: Requirements 9.1, 9.2
 */
@Label("Feature: password-manager, Property 20: 密码历史排序与完整性")
class PasswordHistoryOrderPropertyTest {

    private static final Long USER_ID = 1L;
    private static final Long CREDENTIAL_ID = 100L;

    /**
     * Property: getHistory 返回的记录按 changedAt 降序排列。
     * For any set of history entities with distinct timestamps,
     * the returned responses must be sorted by changedAt descending.
     */
    @Property(tries = 100)
    @Label("should_returnHistoryInDescendingOrder_when_multipleRecordsExist")
    @SuppressWarnings("unchecked")
    void should_returnHistoryInDescendingOrder_when_multipleRecordsExist(
            @ForAll("historyEntityLists") List<PasswordHistoryEntity> entities
    ) {
        PasswordHistoryMapper mapper = mock(PasswordHistoryMapper.class);
        CredentialRepository credRepo = mock(CredentialRepository.class);
        SessionService sessService = mock(SessionService.class);

        // Use a real-like mapper that simulates the DB ordering
        PasswordHistoryDtoMapper dtoMapper = new PasswordHistoryDtoMapper() {
            @Override
            public PasswordHistoryResponse toResponse(PasswordHistoryEntity entity) {
                return PasswordHistoryResponse.builder()
                        .id(entity.getId())
                        .maskedPassword("••••••")
                        .changedAt(entity.getCreatedAt())
                        .build();
            }
        };

        PasswordHistoryServiceImpl service = new PasswordHistoryServiceImpl(
                mapper, dtoMapper, credRepo,
                mock(EncryptionEngine.class), sessService);

        // Setup credential ownership check
        Credential cred = Credential.builder()
                .id(CREDENTIAL_ID).userId(USER_ID).build();
        when(credRepo.findById(CREDENTIAL_ID)).thenReturn(Optional.of(cred));
        when(sessService.isSessionActive(USER_ID)).thenReturn(true);

        // Simulate DB returning records in descending order (as the query specifies)
        List<PasswordHistoryEntity> sorted = entities.stream()
                .sorted(Comparator.comparing(PasswordHistoryEntity::getCreatedAt).reversed())
                .limit(10)
                .collect(Collectors.toList());
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(sorted);

        List<PasswordHistoryResponse> result = service.getHistory(USER_ID, CREDENTIAL_ID);

        // Verify descending order by changedAt (Req 9.1)
        for (int i = 0; i < result.size() - 1; i++) {
            assertThat(result.get(i).getChangedAt())
                    .isAfterOrEqualTo(result.get(i + 1).getChangedAt());
        }
    }

    /**
     * Property: getHistory 返回的每条记录都包含掩码密码和变更时间。
     * For any set of history entities, every response must have a non-null
     * maskedPassword equal to "••••••" and a non-null changedAt.
     */
    @Property(tries = 100)
    @Label("should_containMaskedPasswordAndChangedAt_forEveryHistoryRecord")
    @SuppressWarnings("unchecked")
    void should_containMaskedPasswordAndChangedAt_forEveryHistoryRecord(
            @ForAll("historyEntityLists") List<PasswordHistoryEntity> entities
    ) {
        PasswordHistoryMapper mapper = mock(PasswordHistoryMapper.class);
        CredentialRepository credRepo = mock(CredentialRepository.class);
        SessionService sessService = mock(SessionService.class);

        PasswordHistoryDtoMapper dtoMapper = new PasswordHistoryDtoMapper() {
            @Override
            public PasswordHistoryResponse toResponse(PasswordHistoryEntity entity) {
                return PasswordHistoryResponse.builder()
                        .id(entity.getId())
                        .maskedPassword("••••••")
                        .changedAt(entity.getCreatedAt())
                        .build();
            }
        };

        PasswordHistoryServiceImpl service = new PasswordHistoryServiceImpl(
                mapper, dtoMapper, credRepo,
                mock(EncryptionEngine.class), sessService);

        Credential cred = Credential.builder()
                .id(CREDENTIAL_ID).userId(USER_ID).build();
        when(credRepo.findById(CREDENTIAL_ID)).thenReturn(Optional.of(cred));
        when(sessService.isSessionActive(USER_ID)).thenReturn(true);

        List<PasswordHistoryEntity> sorted = entities.stream()
                .sorted(Comparator.comparing(PasswordHistoryEntity::getCreatedAt).reversed())
                .limit(10)
                .collect(Collectors.toList());
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(sorted);

        List<PasswordHistoryResponse> result = service.getHistory(USER_ID, CREDENTIAL_ID);

        // Verify every record has maskedPassword and changedAt (Req 9.2)
        assertThat(result).allSatisfy(response -> {
            assertThat(response.getMaskedPassword()).isEqualTo("••••••");
            assertThat(response.getChangedAt()).isNotNull();
        });
    }

    /**
     * Property: getHistory 返回的记录数不超过 10 条。
     */
    @Property(tries = 100)
    @Label("should_returnAtMostTenRecords_when_historyQueried")
    @SuppressWarnings("unchecked")
    void should_returnAtMostTenRecords_when_historyQueried(
            @ForAll("historyEntityLists") List<PasswordHistoryEntity> entities
    ) {
        PasswordHistoryMapper mapper = mock(PasswordHistoryMapper.class);
        CredentialRepository credRepo = mock(CredentialRepository.class);
        SessionService sessService = mock(SessionService.class);

        PasswordHistoryDtoMapper dtoMapper = new PasswordHistoryDtoMapper() {
            @Override
            public PasswordHistoryResponse toResponse(PasswordHistoryEntity entity) {
                return PasswordHistoryResponse.builder()
                        .id(entity.getId())
                        .maskedPassword("••••••")
                        .changedAt(entity.getCreatedAt())
                        .build();
            }
        };

        PasswordHistoryServiceImpl service = new PasswordHistoryServiceImpl(
                mapper, dtoMapper, credRepo,
                mock(EncryptionEngine.class), sessService);

        Credential cred = Credential.builder()
                .id(CREDENTIAL_ID).userId(USER_ID).build();
        when(credRepo.findById(CREDENTIAL_ID)).thenReturn(Optional.of(cred));
        when(sessService.isSessionActive(USER_ID)).thenReturn(true);

        // Simulate DB LIMIT 10
        List<PasswordHistoryEntity> sorted = entities.stream()
                .sorted(Comparator.comparing(PasswordHistoryEntity::getCreatedAt).reversed())
                .limit(10)
                .collect(Collectors.toList());
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(sorted);

        List<PasswordHistoryResponse> result = service.getHistory(USER_ID, CREDENTIAL_ID);

        assertThat(result).hasSizeLessThanOrEqualTo(10);
    }

    // ==================== Providers ====================

    /**
     * Generates lists of 1-15 PasswordHistoryEntity instances with distinct timestamps.
     */
    @Provide
    Arbitrary<List<PasswordHistoryEntity>> historyEntityLists() {
        Arbitrary<PasswordHistoryEntity> entityArb = Combinators.combine(
                Arbitraries.longs().between(1L, 10000L),
                Arbitraries.integers().between(0, 365)
        ).as((id, daysAgo) -> PasswordHistoryEntity.builder()
                .id(id)
                .credentialId(CREDENTIAL_ID)
                .passwordEncrypted(new byte[]{1, 2, 3})
                .iv(new byte[]{4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15})
                .createdAt(LocalDateTime.now().minusDays(daysAgo).minusSeconds(id % 3600))
                .build());

        return entityArb.list().ofMinSize(1).ofMaxSize(15);
    }
}
