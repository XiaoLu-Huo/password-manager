package com.pm.passwordmanager.service;

import java.util.List;

import com.pm.passwordmanager.dto.request.GeneratePasswordRequest;
import com.pm.passwordmanager.dto.response.GeneratedPasswordResponse;
import com.pm.passwordmanager.entity.PasswordRuleEntity;

/**
 * 密码生成器服务接口。
 * 负责密码的生成（默认规则/自定义规则）以及密码规则的保存和查询。
 */
public interface PasswordGeneratorService {

    /**
     * 根据请求参数生成密码。
     * 支持默认规则（16位，含所有字符类型）和自定义规则。
     *
     * @param request 生成密码请求
     * @return 生成的密码及其强度评估
     */
    GeneratedPasswordResponse generatePassword(GeneratePasswordRequest request);

    /**
     * 保存自定义密码规则。
     *
     * @param userId 用户 ID
     * @param rule   密码规则实体
     * @return 保存后的规则实体（含 ID）
     */
    PasswordRuleEntity saveRule(Long userId, PasswordRuleEntity rule);

    /**
     * 查询用户的所有密码规则。
     *
     * @param userId 用户 ID
     * @return 密码规则列表
     */
    List<PasswordRuleEntity> getRulesByUserId(Long userId);

    /**
     * 根据 ID 查询密码规则。
     *
     * @param ruleId 规则 ID
     * @return 密码规则实体
     */
    PasswordRuleEntity getRuleById(Long ruleId);
}
