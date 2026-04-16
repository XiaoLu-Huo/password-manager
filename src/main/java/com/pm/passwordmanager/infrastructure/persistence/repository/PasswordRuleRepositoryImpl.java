package com.pm.passwordmanager.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pm.passwordmanager.domain.model.PasswordRule;
import com.pm.passwordmanager.domain.repository.PasswordRuleRepository;
import com.pm.passwordmanager.infrastructure.persistence.assembler.PasswordRuleAssembler;
import com.pm.passwordmanager.infrastructure.persistence.entity.PasswordRuleEntity;
import com.pm.passwordmanager.infrastructure.persistence.mapper.PasswordRuleMapper;

import lombok.RequiredArgsConstructor;

/**
 * 密码规则仓储实现。通过 PasswordRuleAssembler（MapStruct）完成 Entity ↔ Domain Model 转换。
 */
@Repository
@RequiredArgsConstructor
public class PasswordRuleRepositoryImpl implements PasswordRuleRepository {

    private final PasswordRuleMapper passwordRuleMapper;
    private final PasswordRuleAssembler assembler;

    @Override
    public PasswordRule save(PasswordRule rule) {
        PasswordRuleEntity entity = assembler.toEntity(rule);
        passwordRuleMapper.insert(entity);
        rule.setId(entity.getId());
        return rule;
    }

    @Override
    public List<PasswordRule> findByUserId(Long userId) {
        return passwordRuleMapper.selectList(
                new LambdaQueryWrapper<PasswordRuleEntity>()
                        .eq(PasswordRuleEntity::getUserId, userId)
                        .orderByDesc(PasswordRuleEntity::getCreatedAt))
                .stream().map(assembler::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<PasswordRule> findById(Long id) {
        return Optional.ofNullable(passwordRuleMapper.selectById(id))
                .map(assembler::toDomain);
    }

    @Override
    public Optional<PasswordRule> findByUserIdAndRuleName(Long userId, String ruleName) {
        return Optional.ofNullable(passwordRuleMapper.selectOne(
                new LambdaQueryWrapper<PasswordRuleEntity>()
                        .eq(PasswordRuleEntity::getUserId, userId)
                        .eq(PasswordRuleEntity::getRuleName, ruleName)))
                .map(assembler::toDomain);
    }

    @Override
    public PasswordRule updateById(PasswordRule rule) {
        PasswordRuleEntity entity = assembler.toEntity(rule);
        passwordRuleMapper.updateById(entity);
        return rule;
    }

    @Override
    public void deleteById(Long id) {
        passwordRuleMapper.deleteById(id);
    }
}
