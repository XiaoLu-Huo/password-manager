package com.pm.passwordmanager.infrastructure.persistence.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pm.passwordmanager.domain.model.MfaConfig;
import com.pm.passwordmanager.domain.repository.MfaConfigRepository;
import com.pm.passwordmanager.infrastructure.persistence.assembler.MfaConfigAssembler;
import com.pm.passwordmanager.infrastructure.persistence.entity.MfaConfigEntity;
import com.pm.passwordmanager.infrastructure.persistence.mapper.MfaConfigMapper;

import lombok.RequiredArgsConstructor;

/**
 * MFA 配置仓储实现。通过 MfaConfigAssembler（MapStruct）完成 Entity ↔ Domain Model 转换。
 */
@Repository
@RequiredArgsConstructor
public class MfaConfigRepositoryImpl implements MfaConfigRepository {

    private final MfaConfigMapper mfaConfigMapper;
    private final MfaConfigAssembler assembler;

    @Override
    public Optional<MfaConfig> findByUserId(Long userId) {
        return Optional.ofNullable(mfaConfigMapper.selectOne(
                new LambdaQueryWrapper<MfaConfigEntity>()
                        .eq(MfaConfigEntity::getUserId, userId)))
                .map(assembler::toDomain);
    }

    @Override
    public MfaConfig save(MfaConfig mfaConfig) {
        MfaConfigEntity entity = assembler.toEntity(mfaConfig);
        mfaConfigMapper.insert(entity);
        mfaConfig.setId(entity.getId());
        return mfaConfig;
    }

    @Override
    public void updateById(MfaConfig mfaConfig) {
        MfaConfigEntity entity = assembler.toEntity(mfaConfig);
        mfaConfigMapper.updateById(entity);
    }
}
