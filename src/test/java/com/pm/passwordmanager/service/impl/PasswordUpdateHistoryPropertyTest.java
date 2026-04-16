package com.pm.passwordmanager.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pm.passwordmanager.api.assembler.PasswordHistoryDtoMapper;
import com.pm.passwordmanager.domain.command.UpdateCredentialCommand;
import com.pm.passwordmanager.domain.model.Credential;
import com.pm.passwordmanager.domain.repository.CredentialRepository;
import com.pm.passwordmanager.domain.service.PasswordGeneratorService;
import com.pm.passwordmanager.domain.service.PasswordHistoryService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.domain.service.impl.CredentialServiceImpl;
import com.pm.passwordmanager.domain.service.impl.PasswordHistoryServiceImpl;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;
import com.pm.passwordmanager.infrastructure.persistence.entity.PasswordHistoryEntity;
import com.pm.passwordmanager.infrastructure.persistence.mapper.PasswordHistoryMapper;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.ArgumentCaptor;

/**
 * Property 11: 密码更新记录历史并保持上限
 * 验证：旧密码被记录，历史不超过 10 条，修改时间被更新
 * Validates: Requirements 5.2, 5.3, 5.4, 9.5
 */
@Label("Feature: password-manager, Property 11: 密码更新记录历史并保持上限")
class PasswordUpdateHistoryPropertyTest {

    private static final Long USER_ID = 1L;
    private static final Long CREDENTIAL_ID = 100L;
    private static final byte[] FAKE_DEK = new byte[32];

    /**
     * Property: 密码更新时旧密码被记录到历史，且凭证修改时间被更新。
     * For any distinct password pair, updating a credential's password should:
     * 1. Call recordPasswordChange with the old encrypted password and IV
     * 2. Update the credential's updatedAt timestamp
     */
    @Property(tries = 100)
    @Label("should_recordOldPasswordAndUpdateTimestamp_when_passwordUpdated")
    void should_recordOldPasswordAndUpdateTimestamp_when_passwordUpdated(
            @ForAll("distinctPasswordPairs") String[] passwords
    ) {
        String currentPassword = passwords[0];
        String newPassword = passwords[1];

        // Fresh mocks per invocation to avoid accumulated verify counts
        CredentialRepository credRepo = mock(CredentialRepository.class);
        PasswordHistoryService histService = mock(PasswordHistoryService.class);
        EncryptionEngine encEngine = mock(EncryptionEngine.class);
        SessionService sessService = mock(SessionService.class);

        CredentialServiceImpl service = new CredentialServiceImpl(
                credRepo, histService, encEngine, sessService,
                mock(PasswordGeneratorService.class), null);

        when(sessService.getDek(USER_ID)).thenReturn(FAKE_DEK);

        byte[] oldEncrypted = ("enc_" + currentPassword).getBytes(StandardCharsets.UTF_8);
        byte[] oldIv = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        LocalDateTime beforeUpdate = LocalDateTime.now().minusSeconds(10);

        Credential credential = Credential.builder()
                .id(CREDENTIAL_ID).userId(USER_ID)
                .accountName("TestAccount").username("testuser")
                .passwordEncrypted(oldEncrypted).iv(oldIv)
                .createdAt(beforeUpdate).updatedAt(beforeUpdate)
                .build();

        when(credRepo.findById(CREDENTIAL_ID)).thenReturn(Optional.of(credential));
        when(encEngine.decrypt(any(EncryptedData.class), eq(FAKE_DEK)))
                .thenReturn(currentPassword.getBytes(StandardCharsets.UTF_8));
        when(encEngine.encrypt(any(byte[].class), eq(FAKE_DEK)))
                .thenReturn(new EncryptedData(new byte[]{10, 20, 30}, new byte[]{4, 5, 6}));

        UpdateCredentialCommand command = UpdateCredentialCommand.builder()
                .password(newPassword).build();

        Credential result = service.updateCredential(USER_ID, CREDENTIAL_ID, command);

        // Verify old password was recorded in history (Req 5.2)
        verify(histService).recordPasswordChange(eq(CREDENTIAL_ID), eq(oldEncrypted), eq(oldIv));

        // Verify updatedAt was refreshed (Req 5.4)
        assertThat(result.getUpdatedAt()).isAfter(beforeUpdate);
    }

    /**
     * Property: 密码历史记录不超过 10 条上限。
     * For any number of existing history records above the limit,
     * after recording a new password change, the oldest records should be deleted.
     */
    @Property(tries = 100)
    @Label("should_maintainHistoryLimit_when_recordingPasswordChange")
    @SuppressWarnings("unchecked")
    void should_maintainHistoryLimit_when_recordingPasswordChange(
            @ForAll("historyCountAboveLimit") int existingCount
    ) {
        // Fresh mocks per invocation
        PasswordHistoryMapper mapper = mock(PasswordHistoryMapper.class);
        PasswordHistoryServiceImpl svc = new PasswordHistoryServiceImpl(
                mapper, mock(PasswordHistoryDtoMapper.class), mock(CredentialRepository.class),
                mock(EncryptionEngine.class), mock(SessionService.class));

        Long credentialId = 200L;
        byte[] oldEncrypted = new byte[]{1, 2, 3};
        byte[] iv = new byte[]{4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};

        // After insert, the count will be existingCount + 1
        long countAfterInsert = existingCount + 1L;
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(countAfterInsert);

        // Build oldest records to be deleted
        long excess = countAfterInsert - 10;
        List<PasswordHistoryEntity> oldestRecords = new ArrayList<>();
        for (long i = 0; i < excess; i++) {
            oldestRecords.add(PasswordHistoryEntity.builder()
                    .id(1000L + i).credentialId(credentialId)
                    .passwordEncrypted(new byte[]{1}).iv(new byte[]{2})
                    .createdAt(LocalDateTime.now().minusDays(excess - i))
                    .build());
        }
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(oldestRecords);

        svc.recordPasswordChange(credentialId, oldEncrypted, iv);

        // Verify insert was called with correct data
        ArgumentCaptor<PasswordHistoryEntity> captor = ArgumentCaptor.forClass(PasswordHistoryEntity.class);
        verify(mapper).insert(captor.capture());
        PasswordHistoryEntity inserted = captor.getValue();
        assertThat(inserted.getCredentialId()).isEqualTo(credentialId);
        assertThat(inserted.getPasswordEncrypted()).isEqualTo(oldEncrypted);
        assertThat(inserted.getIv()).isEqualTo(iv);

        // Verify oldest records were deleted to maintain the 10-record cap (Req 5.3, 9.5)
        for (PasswordHistoryEntity old : oldestRecords) {
            verify(mapper).deleteById(old.getId());
        }
    }

    /**
     * Property: 当历史记录未超过上限时，不删除任何记录。
     */
    @Property(tries = 100)
    @Label("should_notDeleteHistory_when_countWithinLimit")
    @SuppressWarnings("unchecked")
    void should_notDeleteHistory_when_countWithinLimit(
            @ForAll("historyCountWithinLimit") int existingCount
    ) {
        // Fresh mocks per invocation
        PasswordHistoryMapper mapper = mock(PasswordHistoryMapper.class);
        PasswordHistoryServiceImpl svc = new PasswordHistoryServiceImpl(
                mapper, mock(PasswordHistoryDtoMapper.class), mock(CredentialRepository.class),
                mock(EncryptionEngine.class), mock(SessionService.class));

        Long credentialId = 300L;
        byte[] oldEncrypted = new byte[]{5, 6, 7};
        byte[] iv = new byte[]{8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};

        // After insert, count is still <= 10
        long countAfterInsert = existingCount + 1L;
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(countAfterInsert);

        svc.recordPasswordChange(credentialId, oldEncrypted, iv);

        // Verify insert was called
        verify(mapper).insert(any(PasswordHistoryEntity.class));

        // selectList should NOT be called when within limit (no deletion needed)
        verify(mapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<String> nonBlankPasswords() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(64).alpha().numeric();
    }

    @Provide
    Arbitrary<String[]> distinctPasswordPairs() {
        return nonBlankPasswords().flatMap(current ->
                nonBlankPasswords()
                        .filter(newPwd -> !newPwd.equals(current))
                        .map(newPwd -> new String[]{current, newPwd}));
    }

    /** Generates existing history counts that will exceed the 10-record limit after insert. */
    @Provide
    Arbitrary<Integer> historyCountAboveLimit() {
        return Arbitraries.integers().between(10, 25);
    }

    /** Generates existing history counts that stay within the 10-record limit after insert. */
    @Provide
    Arbitrary<Integer> historyCountWithinLimit() {
        return Arbitraries.integers().between(0, 9);
    }
}
