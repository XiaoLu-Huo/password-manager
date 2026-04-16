package com.pm.passwordmanager.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pm.passwordmanager.domain.model.Credential;
import com.pm.passwordmanager.domain.repository.CredentialRepository;
import com.pm.passwordmanager.infrastructure.persistence.assembler.CredentialAssembler;
import com.pm.passwordmanager.infrastructure.persistence.entity.CredentialEntity;
import com.pm.passwordmanager.infrastructure.persistence.mapper.CredentialMapper;

import lombok.RequiredArgsConstructor;

/**
 * 凭证仓储实现。通过 CredentialAssembler（MapStruct）完成 Entity ↔ Domain Model 转换。
 */
@Repository
@RequiredArgsConstructor
public class CredentialRepositoryImpl implements CredentialRepository {

    private final CredentialMapper credentialMapper;
    private final CredentialAssembler assembler;

    @Override
    public Credential save(Credential credential) {
        CredentialEntity entity = assembler.toEntity(credential);
        credentialMapper.insert(entity);
        credential.setId(entity.getId());
        return credential;
    }

    @Override
    public Optional<Credential> findById(Long id) {
        return Optional.ofNullable(credentialMapper.selectById(id))
                .map(assembler::toDomain);
    }

    @Override
    public List<Credential> findByUserId(Long userId) {
        return credentialMapper.selectList(
                new LambdaQueryWrapper<CredentialEntity>()
                        .eq(CredentialEntity::getUserId, userId)
                        .orderByDesc(CredentialEntity::getUpdatedAt))
                .stream().map(assembler::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Credential> searchByKeyword(Long userId, String keyword) {
        return credentialMapper.selectList(
                new LambdaQueryWrapper<CredentialEntity>()
                        .eq(CredentialEntity::getUserId, userId)
                        .and(w -> w
                                .like(CredentialEntity::getAccountName, keyword)
                                .or().like(CredentialEntity::getUsername, keyword)
                                .or().like(CredentialEntity::getUrl, keyword))
                        .orderByDesc(CredentialEntity::getUpdatedAt))
                .stream().map(assembler::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Credential> filterByTag(Long userId, String tag) {
        return credentialMapper.selectList(
                new LambdaQueryWrapper<CredentialEntity>()
                        .eq(CredentialEntity::getUserId, userId)
                        .like(CredentialEntity::getTags, tag)
                        .orderByDesc(CredentialEntity::getUpdatedAt))
                .stream().map(assembler::toDomain).collect(Collectors.toList());
    }

    @Override
    public void updateById(Credential credential) {
        CredentialEntity entity = assembler.toEntity(credential);
        credentialMapper.updateById(entity);
    }

    @Override
    public void deleteById(Long id) {
        credentialMapper.deleteById(id);
    }
}
