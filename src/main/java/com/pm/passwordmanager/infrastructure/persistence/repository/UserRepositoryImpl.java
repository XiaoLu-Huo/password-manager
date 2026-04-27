package com.pm.passwordmanager.infrastructure.persistence.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pm.passwordmanager.domain.model.User;
import com.pm.passwordmanager.domain.repository.UserRepository;
import com.pm.passwordmanager.infrastructure.persistence.assembler.UserAssembler;
import com.pm.passwordmanager.infrastructure.persistence.entity.UserEntity;
import com.pm.passwordmanager.infrastructure.persistence.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

/**
 * 用户仓储实现。通过 UserAssembler（MapStruct）完成 Entity ↔ Domain Model 转换。
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;
    private final UserAssembler assembler;

    @Override
    public Optional<User> findFirst() {
        return Optional.ofNullable(userMapper.selectOne(null))
                .map(assembler::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        return Optional.ofNullable(userMapper.selectOne(wrapper))
                .map(assembler::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("email", email);
        return Optional.ofNullable(userMapper.selectOne(wrapper))
                .map(assembler::toDomain);
    }

    @Override
    public User save(User user) {
        UserEntity entity = assembler.toEntity(user);
        userMapper.insert(entity);
        user.setId(entity.getId());
        return user;
    }

    @Override
    public void updateById(User user) {
        UserEntity entity = assembler.toEntity(user);
        userMapper.updateById(entity);
    }
}
