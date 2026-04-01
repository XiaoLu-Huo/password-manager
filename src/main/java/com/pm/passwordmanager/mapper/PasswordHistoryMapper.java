package com.pm.passwordmanager.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pm.passwordmanager.entity.PasswordHistoryEntity;

@Mapper
public interface PasswordHistoryMapper extends BaseMapper<PasswordHistoryEntity> {
}
