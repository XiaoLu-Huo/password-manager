package com.pm.passwordmanager.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // 通用错误
    SUCCESS(0, "操作成功"),
    INTERNAL_ERROR(10001, "系统内部错误"),
    INVALID_REQUEST(10002, "请求参数无效"),

    // 认证错误 (2xxxx)
    MASTER_PASSWORD_WRONG(20001, "主密码错误"),
    ACCOUNT_LOCKED(20002, "账户已锁定，请稍后再试"),
    MASTER_PASSWORD_TOO_WEAK(20003, "主密码不满足复杂度要求"),
    TOTP_INVALID(20004, "验证码无效，请重新输入"),
    TOTP_REQUIRED(20005, "请输入 TOTP 验证码"),
    MFA_ALREADY_ENABLED(20006, "MFA 已启用"),
    MFA_NOT_ENABLED(20007, "MFA 未启用"),
    SESSION_EXPIRED(20008, "会话已过期，请重新解锁"),
    VAULT_LOCKED(20009, "密码库已锁定，请先解锁"),
    USERNAME_DUPLICATE(20010, "用户名已被占用"),
    EMAIL_DUPLICATE(20011, "邮箱已被占用"),
    USERNAME_INVALID_FORMAT(20012, "用户名格式不合法"),
    EMAIL_INVALID_FORMAT(20013, "邮箱格式不合法"),
    CREDENTIALS_INVALID(20014, "用户名或密码错误"),

    // 凭证错误 (3xxxx)
    CREDENTIAL_NOT_FOUND(30001, "凭证不存在"),
    CREDENTIAL_REQUIRED_FIELDS_MISSING(30002, "请填写所有必填项"),
    SAME_PASSWORD(30003, "新密码不能与当前密码相同"),

    // 密码生成错误 (4xxxx)
    PASSWORD_LENGTH_TOO_SHORT(40001, "密码长度不能少于 8 个字符"),
    PASSWORD_LENGTH_TOO_LONG(40002, "密码长度不能超过 128 个字符"),
    NO_CHAR_TYPE_SELECTED(40003, "至少选择一种字符类型"),
    RULE_NAME_DUPLICATE(40004, "规则名称已存在"),
    RULE_NOT_FOUND(40005, "密码规则不存在"),

    // 导入导出错误 (5xxxx)
    UNSUPPORTED_FILE_FORMAT(50001, "文件格式不支持，请使用有效的 Excel 文件"),
    IMPORT_DECRYPTION_FAILED(50002, "文件解密失败，请检查密码是否正确"),
    IMPORT_PARSE_ERROR(50003, "文件解析失败，数据格式不正确"),
    EXPORT_FAILED(50004, "导出失败"),

    // 密码历史错误 (6xxxx)
    PASSWORD_HISTORY_NOT_FOUND(60001, "密码历史记录不存在"),

    // 设置错误 (7xxxx)
    AUTO_LOCK_TIMEOUT_OUT_OF_RANGE(70001, "自动锁定超时时间必须在 1-60 分钟之间");

    private final int code;
    private final String message;
}
