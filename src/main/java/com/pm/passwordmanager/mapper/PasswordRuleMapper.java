package com.pm.passwordmanager.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pm.passwordmanager.entity.PasswordRuleEntity;

@Mapper
public interface PasswordRuleMapper extends BaseMapper<PasswordRuleEntity> {
}
