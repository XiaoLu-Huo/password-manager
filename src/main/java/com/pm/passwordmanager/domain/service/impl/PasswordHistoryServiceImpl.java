package com.pm.passwordmanager.domain.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pm.passwordmanager.api.assembler.PasswordHistoryDtoMapper;
import com.pm.passwordmanager.api.dto.response.PasswordHistoryResponse;
import com.pm.passwordmanager.domain.model.Credential;
import com.pm.passwordmanager.domain.repository.CredentialRepository;
import com.pm.passwordmanager.domain.service.PasswordHistoryService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;
import com.pm.passwordmanager.infrastructure.persistence.entity.PasswordHistoryEntity;
import com.pm.passwordmanager.infrastructure.persistence.mapper.PasswordHistoryMapper;

import lombok.RequiredArgsConstructor;

/**
 * 密码历史领域服务实现。
 */
@Service
@RequiredArgsConstructor
public class PasswordHistoryServiceImpl implements PasswordHistoryService {

    private static final int MAX_HISTORY_COUNT = 10;

    private final PasswordHistoryMapper passwordHistoryMapper;
    private final PasswordHistoryDtoMapper passwordHistoryDtoMapper;
    private final CredentialRepository credentialRepository;
    private final EncryptionEngine encryptionEngine;
    private final SessionService sessionService;

    @Override
    public List<PasswordHistoryResponse> getHistory(Long userId, Long credentialId) {
        ensureCredentialOwnership(userId, credentialId);
        ensureSessionActive(userId);

        List<PasswordHistoryEntity> entities = passwordHistoryMapper.selectList(
                new LambdaQueryWrapper<PasswordHistoryEntity>()
                        .eq(PasswordHistoryEntity::getCredentialId, credentialId)
                        .orderByDesc(PasswordHistoryEntity::getCreatedAt)
                        .last("LIMIT " + MAX_HISTORY_COUNT));

        return entities.stream()
                .map(passwordHistoryDtoMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public String revealHistoryPassword(Long userId, Long credentialId, Long historyId) {
        ensureCredentialOwnership(userId, credentialId);
        byte[] dek = getActiveDek(userId);

        PasswordHistoryEntity entity = passwordHistoryMapper.selectById(historyId);
        if (entity == null || !entity.getCredentialId().equals(credentialId)) {
            throw new BusinessException(ErrorCode.PASSWORD_HISTORY_NOT_FOUND);
        }

        EncryptedData encrypted = new EncryptedData(entity.getPasswordEncrypted(), entity.getIv());
        byte[] plaintext = encryptionEngine.decrypt(encrypted, dek);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    @Override
    @Transactional
    public void recordPasswordChange(Long credentialId, byte[] oldPasswordEncrypted, byte[] iv) {
        PasswordHistoryEntity history = PasswordHistoryEntity.builder()
                .credentialId(credentialId)
                .passwordEncrypted(oldPasswordEncrypted)
                .iv(iv)
                .createdAt(LocalDateTime.now())
                .build();
        passwordHistoryMapper.insert(history);

        // 超过上限时删除最早的记录
        Long count = passwordHistoryMapper.selectCount(
                new LambdaQueryWrapper<PasswordHistoryEntity>()
                        .eq(PasswordHistoryEntity::getCredentialId, credentialId));
        if (count > MAX_HISTORY_COUNT) {
            List<PasswordHistoryEntity> oldest = passwordHistoryMapper.selectList(
                    new LambdaQueryWrapper<PasswordHistoryEntity>()
                            .eq(PasswordHistoryEntity::getCredentialId, credentialId)
                            .orderByAsc(PasswordHistoryEntity::getCreatedAt)
                            .last("LIMIT " + (count - MAX_HISTORY_COUNT)));
            for (PasswordHistoryEntity old : oldest) {
                passwordHistoryMapper.deleteById(old.getId());
            }
        }
    }

    // ==================== 私有方法 ====================

    private void ensureCredentialOwnership(Long userId, Long credentialId) {
        Credential credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREDENTIAL_NOT_FOUND));
        if (!credential.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CREDENTIAL_NOT_FOUND);
        }
    }

    private byte[] getActiveDek(Long userId) {
        byte[] dek = sessionService.getDek(userId);
        if (dek == null) {
            throw new BusinessException(ErrorCode.VAULT_LOCKED);
        }
        return dek;
    }

    private void ensureSessionActive(Long userId) {
        if (!sessionService.isSessionActive(userId)) {
            throw new BusinessException(ErrorCode.VAULT_LOCKED);
        }
    }
}
