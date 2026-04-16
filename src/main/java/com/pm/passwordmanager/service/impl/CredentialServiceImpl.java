package com.pm.passwordmanager.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pm.passwordmanager.dto.request.CreateCredentialRequest;
import com.pm.passwordmanager.dto.request.GeneratePasswordRequest;
import com.pm.passwordmanager.dto.request.UpdateCredentialRequest;
import com.pm.passwordmanager.dto.response.CredentialListResponse;
import com.pm.passwordmanager.dto.response.CredentialResponse;
import com.pm.passwordmanager.entity.CredentialEntity;
import com.pm.passwordmanager.entity.PasswordHistoryEntity;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.mapper.CredentialMapper;
import com.pm.passwordmanager.mapper.PasswordHistoryMapper;
import com.pm.passwordmanager.service.CredentialService;
import com.pm.passwordmanager.service.PasswordGeneratorService;
import com.pm.passwordmanager.service.SessionService;
import com.pm.passwordmanager.util.EncryptedData;
import com.pm.passwordmanager.util.EncryptionEngine;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CredentialServiceImpl implements CredentialService {

    private static final String MASKED_PASSWORD = "••••••";
    private static final int MAX_HISTORY_COUNT = 10;

    private final CredentialMapper credentialMapper;
    private final PasswordHistoryMapper passwordHistoryMapper;
    private final EncryptionEngine encryptionEngine;
    private final SessionService sessionService;
    private final PasswordGeneratorService passwordGeneratorService;

    @Override
    @Transactional
    public CredentialResponse createCredential(Long userId, CreateCredentialRequest request) {
        // Resolve password: auto-generate or use provided
        String password = resolvePassword(request);

        // Validate required fields
        validateRequiredFields(request.getAccountName(), request.getUsername(), password);

        // Encrypt password
        byte[] dek = getActiveDek(userId);
        EncryptedData encrypted = encryptionEngine.encrypt(
                password.getBytes(StandardCharsets.UTF_8), dek);

        LocalDateTime now = LocalDateTime.now();
        CredentialEntity entity = CredentialEntity.builder()
                .userId(userId)
                .accountName(request.getAccountName())
                .username(request.getUsername())
                .passwordEncrypted(encrypted.getCiphertext())
                .iv(encrypted.getIv())
                .url(request.getUrl())
                .notes(request.getNotes())
                .tags(request.getTags())
                .createdAt(now)
                .updatedAt(now)
                .build();

        credentialMapper.insert(entity);
        return toResponse(entity);
    }

    @Override
    public List<CredentialListResponse> listCredentials(Long userId) {
        ensureSessionActive(userId);
        List<CredentialEntity> entities = credentialMapper.selectList(
                new LambdaQueryWrapper<CredentialEntity>()
                        .eq(CredentialEntity::getUserId, userId)
                        .orderByDesc(CredentialEntity::getUpdatedAt));
        return entities.stream().map(this::toListResponse).collect(Collectors.toList());
    }

    @Override
    public List<CredentialListResponse> searchCredentials(Long userId, String keyword) {
        ensureSessionActive(userId);
        if (keyword == null || keyword.isBlank()) {
            return listCredentials(userId);
        }
        List<CredentialEntity> entities = credentialMapper.selectList(
                new LambdaQueryWrapper<CredentialEntity>()
                        .eq(CredentialEntity::getUserId, userId)
                        .and(w -> w
                                .like(CredentialEntity::getAccountName, keyword)
                                .or().like(CredentialEntity::getUsername, keyword)
                                .or().like(CredentialEntity::getUrl, keyword))
                        .orderByDesc(CredentialEntity::getUpdatedAt));
        return entities.stream().map(this::toListResponse).collect(Collectors.toList());
    }

    @Override
    public List<CredentialListResponse> filterByTag(Long userId, String tag) {
        ensureSessionActive(userId);
        if (tag == null || tag.isBlank()) {
            return listCredentials(userId);
        }
        // Tags are stored as comma-separated values; use LIKE to match
        List<CredentialEntity> entities = credentialMapper.selectList(
                new LambdaQueryWrapper<CredentialEntity>()
                        .eq(CredentialEntity::getUserId, userId)
                        .like(CredentialEntity::getTags, tag)
                        .orderByDesc(CredentialEntity::getUpdatedAt));
        return entities.stream().map(this::toListResponse).collect(Collectors.toList());
    }

    @Override
    public CredentialResponse getCredential(Long userId, Long credentialId) {
        ensureSessionActive(userId);
        CredentialEntity entity = findCredentialByIdAndUser(userId, credentialId);
        return toResponse(entity);
    }

    @Override
    public String revealPassword(Long userId, Long credentialId) {
        byte[] dek = getActiveDek(userId);
        CredentialEntity entity = findCredentialByIdAndUser(userId, credentialId);
        return decryptPassword(entity, dek);
    }

    @Override
    @Transactional
    public CredentialResponse updateCredential(Long userId, Long credentialId,
                                                UpdateCredentialRequest request) {
        byte[] dek = getActiveDek(userId);
        CredentialEntity entity = findCredentialByIdAndUser(userId, credentialId);

        // Update non-password fields if provided
        if (request.getAccountName() != null) {
            entity.setAccountName(request.getAccountName());
        }
        if (request.getUsername() != null) {
            entity.setUsername(request.getUsername());
        }
        if (request.getUrl() != null) {
            entity.setUrl(request.getUrl());
        }
        if (request.getNotes() != null) {
            entity.setNotes(request.getNotes());
        }
        if (request.getTags() != null) {
            entity.setTags(request.getTags());
        }

        // Handle password update
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            String newPassword = request.getPassword();
            String currentPassword = decryptPassword(entity, dek);

            // Reject if new password is same as current
            if (newPassword.equals(currentPassword)) {
                throw new BusinessException(ErrorCode.SAME_PASSWORD);
            }

            // Record old password in history
            recordPasswordHistory(entity);

            // Encrypt and set new password
            EncryptedData encrypted = encryptionEngine.encrypt(
                    newPassword.getBytes(StandardCharsets.UTF_8), dek);
            entity.setPasswordEncrypted(encrypted.getCiphertext());
            entity.setIv(encrypted.getIv());
        }

        entity.setUpdatedAt(LocalDateTime.now());
        credentialMapper.updateById(entity);
        return toResponse(entity);
    }

    @Override
    @Transactional
    public void deleteCredential(Long userId, Long credentialId) {
        ensureSessionActive(userId);
        CredentialEntity entity = findCredentialByIdAndUser(userId, credentialId);
        credentialMapper.deleteById(entity.getId());
    }

    // ==================== Private helpers ====================

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

    private void validateRequiredFields(String accountName, String username, String password) {
        if (isBlank(accountName) || isBlank(username) || isBlank(password)) {
            throw new BusinessException(ErrorCode.CREDENTIAL_REQUIRED_FIELDS_MISSING);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String resolvePassword(CreateCredentialRequest request) {
        if (Boolean.TRUE.equals(request.getAutoGenerate())) {
            GeneratePasswordRequest genReq = GeneratePasswordRequest.builder()
                    .useDefault(true)
                    .build();
            return passwordGeneratorService.generatePassword(genReq).getPassword();
        }
        return request.getPassword();
    }

    private CredentialEntity findCredentialByIdAndUser(Long userId, Long credentialId) {
        CredentialEntity entity = credentialMapper.selectById(credentialId);
        if (entity == null || !entity.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CREDENTIAL_NOT_FOUND);
        }
        return entity;
    }

    private String decryptPassword(CredentialEntity entity, byte[] dek) {
        EncryptedData encrypted = new EncryptedData(entity.getPasswordEncrypted(), entity.getIv());
        byte[] plaintext = encryptionEngine.decrypt(encrypted, dek);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private void recordPasswordHistory(CredentialEntity entity) {
        // Save old encrypted password to history
        PasswordHistoryEntity history = PasswordHistoryEntity.builder()
                .credentialId(entity.getId())
                .passwordEncrypted(entity.getPasswordEncrypted())
                .iv(entity.getIv())
                .createdAt(LocalDateTime.now())
                .build();
        passwordHistoryMapper.insert(history);

        // Enforce max 10 history records: delete oldest if exceeded
        Long count = passwordHistoryMapper.selectCount(
                new LambdaQueryWrapper<PasswordHistoryEntity>()
                        .eq(PasswordHistoryEntity::getCredentialId, entity.getId()));
        if (count > MAX_HISTORY_COUNT) {
            // Find the oldest records to delete
            List<PasswordHistoryEntity> oldest = passwordHistoryMapper.selectList(
                    new LambdaQueryWrapper<PasswordHistoryEntity>()
                            .eq(PasswordHistoryEntity::getCredentialId, entity.getId())
                            .orderByAsc(PasswordHistoryEntity::getCreatedAt)
                            .last("LIMIT " + (count - MAX_HISTORY_COUNT)));
            for (PasswordHistoryEntity old : oldest) {
                passwordHistoryMapper.deleteById(old.getId());
            }
        }
    }

    private CredentialResponse toResponse(CredentialEntity entity) {
        return CredentialResponse.builder()
                .id(entity.getId())
                .accountName(entity.getAccountName())
                .username(entity.getUsername())
                .maskedPassword(MASKED_PASSWORD)
                .url(entity.getUrl())
                .notes(entity.getNotes())
                .tags(entity.getTags())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private CredentialListResponse toListResponse(CredentialEntity entity) {
        return CredentialListResponse.builder()
                .id(entity.getId())
                .accountName(entity.getAccountName())
                .username(entity.getUsername())
                .url(entity.getUrl())
                .tags(entity.getTags())
                .build();
    }
}
