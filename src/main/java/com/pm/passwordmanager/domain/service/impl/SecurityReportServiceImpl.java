package com.pm.passwordmanager.domain.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.pm.passwordmanager.api.dto.response.SecurityReportResponse;
import com.pm.passwordmanager.api.enums.PasswordStrengthLevel;
import com.pm.passwordmanager.domain.model.Credential;
import com.pm.passwordmanager.domain.repository.CredentialRepository;
import com.pm.passwordmanager.domain.service.SecurityReportService;
import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;
import com.pm.passwordmanager.infrastructure.encryption.EncryptedData;
import com.pm.passwordmanager.infrastructure.encryption.EncryptionEngine;
import com.pm.passwordmanager.infrastructure.encryption.PasswordStrengthEvaluator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SecurityReportServiceImpl implements SecurityReportService {

    private static final int EXPIRED_DAYS = 90;

    private final CredentialRepository credentialRepository;
    private final EncryptionEngine encryptionEngine;
    private final SessionService sessionService;
    private final PasswordStrengthEvaluator passwordStrengthEvaluator;

    @Override
    public SecurityReportResponse getReport(Long userId) {
        byte[] dek = getActiveDek(userId);
        List<Credential> credentials = credentialRepository.findByUserId(userId);

        Map<PasswordStrengthLevel, Integer> strengthCounts = new EnumMap<>(PasswordStrengthLevel.class);
        for (PasswordStrengthLevel level : PasswordStrengthLevel.values()) {
            strengthCounts.put(level, 0);
        }

        int duplicateCount = 0;
        int expiredCount = 0;
        LocalDateTime expiryThreshold = LocalDateTime.now().minusDays(EXPIRED_DAYS);
        Map<String, List<Credential>> passwordGroups = new HashMap<>();

        for (Credential c : credentials) {
            String plainPassword = decryptPassword(c, dek);
            PasswordStrengthLevel level = passwordStrengthEvaluator.evaluate(plainPassword);
            strengthCounts.merge(level, 1, Integer::sum);

            if (c.getUpdatedAt() != null && c.getUpdatedAt().isBefore(expiryThreshold)) {
                expiredCount++;
            }
            passwordGroups.computeIfAbsent(plainPassword, k -> new ArrayList<>()).add(c);
        }

        for (List<Credential> group : passwordGroups.values()) {
            if (group.size() > 1) {
                duplicateCount += group.size();
            }
        }

        return SecurityReportResponse.builder()
                .totalCredentials(credentials.size())
                .weakPasswordCount(strengthCounts.get(PasswordStrengthLevel.WEAK))
                .mediumPasswordCount(strengthCounts.get(PasswordStrengthLevel.MEDIUM))
                .duplicatePasswordCount(duplicateCount)
                .expiredPasswordCount(expiredCount)
                .build();
    }

    @Override
    public List<Credential> getCredentialsByStrengthLevel(Long userId, PasswordStrengthLevel level) {
        byte[] dek = getActiveDek(userId);
        List<Credential> credentials = credentialRepository.findByUserId(userId);
        List<Credential> result = new ArrayList<>();

        for (Credential c : credentials) {
            String plainPassword = decryptPassword(c, dek);
            if (passwordStrengthEvaluator.evaluate(plainPassword) == level) {
                result.add(c);
            }
        }
        return result;
    }

    @Override
    public List<Credential> getDuplicatePasswordCredentials(Long userId) {
        byte[] dek = getActiveDek(userId);
        List<Credential> credentials = credentialRepository.findByUserId(userId);

        Map<String, List<Credential>> passwordGroups = new HashMap<>();
        for (Credential c : credentials) {
            String plainPassword = decryptPassword(c, dek);
            passwordGroups.computeIfAbsent(plainPassword, k -> new ArrayList<>()).add(c);
        }

        List<Credential> result = new ArrayList<>();
        for (List<Credential> group : passwordGroups.values()) {
            if (group.size() > 1) {
                result.addAll(group);
            }
        }
        return result;
    }

    @Override
    public List<Credential> getExpiredPasswordCredentials(Long userId) {
        ensureSessionActive(userId);
        List<Credential> credentials = credentialRepository.findByUserId(userId);
        LocalDateTime expiryThreshold = LocalDateTime.now().minusDays(EXPIRED_DAYS);
        List<Credential> result = new ArrayList<>();

        for (Credential c : credentials) {
            if (c.getUpdatedAt() != null && c.getUpdatedAt().isBefore(expiryThreshold)) {
                result.add(c);
            }
        }
        return result;
    }

    // ==================== Private helpers ====================

    private void ensureSessionActive(Long userId) {
        if (!sessionService.isSessionActive(userId)) {
            throw new BusinessException(ErrorCode.VAULT_LOCKED);
        }
    }

    private byte[] getActiveDek(Long userId) {
        byte[] dek = sessionService.getDek(userId);
        if (dek == null) {
            throw new BusinessException(ErrorCode.VAULT_LOCKED);
        }
        return dek;
    }

    private String decryptPassword(Credential credential, byte[] dek) {
        EncryptedData encrypted = new EncryptedData(
                credential.getPasswordEncrypted(), credential.getIv());
        byte[] plaintext = encryptionEngine.decrypt(encrypted, dek);
        return new String(plaintext, StandardCharsets.UTF_8);
    }
}
