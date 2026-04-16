package com.pm.passwordmanager.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pm.passwordmanager.infrastructure.persistence.entity.UserEntity;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
