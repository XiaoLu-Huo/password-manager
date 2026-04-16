package com.pm.passwordmanager.domain.service;

import java.util.List;

import com.pm.passwordmanager.api.dto.response.GeneratedPasswordResponse;
import com.pm.passwordmanager.domain.command.GeneratePasswordCommand;
import com.pm.passwordmanager.domain.model.PasswordRule;

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
    GeneratedPasswordResponse generatePassword(GeneratePasswordCommand command);

    /**
     * 保存自定义密码规则。
     *
     * @param userId 用户 ID
     * @param rule   密码规则领域模型
     * @return 保存后的规则（含 ID）
     */
    PasswordRule saveRule(Long userId, PasswordRule rule);

    /**
     * 查询用户的所有密码规则。
     *
     * @param userId 用户 ID
     * @return 密码规则列表
     */
    List<PasswordRule> getRulesByUserId(Long userId);

    /**
     * 根据 ID 查询密码规则。
     *
     * @param ruleId 规则 ID
     * @return 密码规则领域模型
     */
    PasswordRule getRuleById(Long ruleId);

    /**
     * 更新已有密码规则。
     *
     * @param userId 用户 ID
     * @param ruleId 规则 ID
     * @param rule   更新后的密码规则
     * @return 更新后的规则
     */
    PasswordRule updateRule(Long userId, Long ruleId, PasswordRule rule);

    /**
     * 根据 ID 删除密码规则。
     *
     * @param ruleId 规则 ID
     */
    void deleteRule(Long ruleId);
}
