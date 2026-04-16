package com.pm.passwordmanager.domain.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pm.passwordmanager.api.dto.request.GeneratePasswordRequest;
import com.pm.passwordmanager.domain.assembler.CredentialModelAssembler;
import com.pm.passwordmanager.domain.command.CreateCredentialCommand;
import com.pm.passwordmanager.domain.command.UpdateCredentialCommand;
import com.pm.passwordmanager.domain.model.Credential;
import com.pm.passwordmanager.domain.repository.CredentialRepository;
import com.pm.passwordmanager.domain.service.CredentialService;
import com.pm.passwordmanager.domain.service.PasswordGeneratorService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;
import com.pm.passwordmanager.infrastructure.persistence.entity.PasswordHistoryEntity;
import com.pm.passwordmanager.infrastructure.persistence.mapper.PasswordHistoryMapper;

import lombok.RequiredArgsConstructor;

/**
 * 凭证领域服务实现。
 * 通过领域模型和仓储接口编排业务逻辑，不直接操作 Entity/Mapper。
 */
@Service
@RequiredArgsConstructor
public class CredentialServiceImpl implements CredentialService {

    private static final int MAX_HISTORY_COUNT = 10;

    private final CredentialRepository credentialRepository;
    private final PasswordHistoryMapper passwordHistoryMapper;
    private final EncryptionEngine encryptionEngine;
    private final SessionService sessionService;
    private final PasswordGeneratorService passwordGeneratorService;
    private final CredentialModelAssembler credentialModelAssembler;

    @Override
    @Transactional
    public Credential createCredential(Long userId, CreateCredentialCommand command) {
        // 解析密码：自动生成或使用提供的
        String password = resolvePassword(command);

        // Command → Domain Model（MapStruct）
        Credential credential = credentialModelAssembler.toModel(command, userId);
        LocalDateTime now = LocalDateTime.now();
        credential.setCreatedAt(now);
        credential.setUpdatedAt(now);

        // 领域模型验证必填字段
        credential.validateRequiredFields(password);

        // 加密密码
        byte[] dek = getActiveDek(userId);
        EncryptedData encrypted = encryptionEngine.encrypt(
                password.getBytes(StandardCharsets.UTF_8), dek);
        credential.updateEncryptedPassword(encrypted.getCiphertext(), encrypted.getIv());

        // 通过仓储持久化
        return credentialRepository.save(credential);
    }

    @Override
    public List<Credential> listCredentials(Long userId) {
        ensureSessionActive(userId);
        return credentialRepository.findByUserId(userId);
    }

    @Override
    public List<Credential> searchCredentials(Long userId, String keyword) {
        ensureSessionActive(userId);
        if (keyword == null || keyword.isBlank()) {
            return credentialRepository.findByUserId(userId);
        }
        return credentialRepository.searchByKeyword(userId, keyword);
    }

    @Override
    public List<Credential> filterByTag(Long userId, String tag) {
        ensureSessionActive(userId);
        if (tag == null || tag.isBlank()) {
            return credentialRepository.findByUserId(userId);
        }
        return credentialRepository.filterByTag(userId, tag);
    }

    @Override
    public Credential getCredential(Long userId, Long credentialId) {
        ensureSessionActive(userId);
        return findCredentialByUser(userId, credentialId);
    }

    @Override
    public String revealPassword(Long userId, Long credentialId) {
        byte[] dek = getActiveDek(userId);
        Credential credential = findCredentialByUser(userId, credentialId);
        return decryptPassword(credential, dek);
    }

    @Override
    @Transactional
    public Credential updateCredential(Long userId, Long credentialId, UpdateCredentialCommand command) {
        byte[] dek = getActiveDek(userId);
        Credential credential = findCredentialByUser(userId, credentialId);

        // 应用非密码字段更新
        credential.applyUpdate(
                command.getAccountName(), command.getUsername(),
                command.getUrl(), command.getNotes(), command.getTags());

        // 处理密码更新
        if (command.getPassword() != null && !command.getPassword().isBlank()) {
            String currentPassword = decryptPassword(credential, dek);
            credential.validatePasswordChange(command.getPassword(), currentPassword);

            // 记录旧密码到历史
            recordPasswordHistory(credential);

            // 加密新密码
            EncryptedData encrypted = encryptionEngine.encrypt(
                    command.getPassword().getBytes(StandardCharsets.UTF_8), dek);
            credential.updateEncryptedPassword(encrypted.getCiphertext(), encrypted.getIv());
        }

        credentialRepository.updateById(credential);
        return credential;
    }

    @Override
    @Transactional
    public void deleteCredential(Long userId, Long credentialId) {
        ensureSessionActive(userId);
        Credential credential = findCredentialByUser(userId, credentialId);
        credentialRepository.deleteById(credential.getId());
    }

    // ==================== 私有方法 ====================

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

    private String resolvePassword(CreateCredentialCommand command) {
        if (Boolean.TRUE.equals(command.getAutoGenerate())) {
            GeneratePasswordRequest genReq = GeneratePasswordRequest.builder()
                    .useDefault(true)
                    .build();
            return passwordGeneratorService.generatePassword(genReq).getPassword();
        }
        return command.getPassword();
    }

    private Credential findCredentialByUser(Long userId, Long credentialId) {
        Credential credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREDENTIAL_NOT_FOUND));
        if (!credential.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CREDENTIAL_NOT_FOUND);
        }
        return credential;
    }

    private String decryptPassword(Credential credential, byte[] dek) {
        EncryptedData encrypted = new EncryptedData(credential.getPasswordEncrypted(), credential.getIv());
        byte[] plaintext = encryptionEngine.decrypt(encrypted, dek);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private void recordPasswordHistory(Credential credential) {
        PasswordHistoryEntity history = PasswordHistoryEntity.builder()
                .credentialId(credential.getId())
                .passwordEncrypted(credential.getPasswordEncrypted())
                .iv(credential.getIv())
                .createdAt(LocalDateTime.now())
                .build();
        passwordHistoryMapper.insert(history);

        Long count = passwordHistoryMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PasswordHistoryEntity>()
                        .eq(PasswordHistoryEntity::getCredentialId, credential.getId()));
        if (count > MAX_HISTORY_COUNT) {
            List<PasswordHistoryEntity> oldest = passwordHistoryMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PasswordHistoryEntity>()
                            .eq(PasswordHistoryEntity::getCredentialId, credential.getId())
                            .orderByAsc(PasswordHistoryEntity::getCreatedAt)
                            .last("LIMIT " + (count - MAX_HISTORY_COUNT)));
            for (PasswordHistoryEntity old : oldest) {
                passwordHistoryMapper.deleteById(old.getId());
            }
        }
    }
}
