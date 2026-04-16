package com.pm.passwordmanager.exception;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.pm.passwordmanager.api.dto.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        ApiResponse<Void> body = ApiResponse.error(e.getErrorCode());
        HttpStatus status = mapToHttpStatus(e.getErrorCode());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        ApiResponse<Void> body = ApiResponse.error(ErrorCode.INVALID_REQUEST.getCode(), message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("未知异常", e);
        ApiResponse<Void> body = ApiResponse.error(ErrorCode.INTERNAL_ERROR);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * 根据业务错误码映射到合适的 HTTP 状态码。
     * 认证/会话类错误 → 401
     * 资源不存在 → 404
     * 参数校验类 → 400
     * 其他业务错误 → 422 (Unprocessable Entity)
     */
    private HttpStatus mapToHttpStatus(ErrorCode errorCode) {
        int code = errorCode.getCode();

        // 认证相关 (2xxxx) — 会话过期、密码库锁定返回 401
        if (code == ErrorCode.SESSION_EXPIRED.getCode() || code == ErrorCode.VAULT_LOCKED.getCode()) {
            return HttpStatus.UNAUTHORIZED;
        }
        // 认证相关其他错误（密码错误、账户锁定等）→ 400
        if (code >= 20000 && code < 30000) {
            return HttpStatus.BAD_REQUEST;
        }

        // 资源不存在
        if (code == ErrorCode.CREDENTIAL_NOT_FOUND.getCode() || code == ErrorCode.PASSWORD_HISTORY_NOT_FOUND.getCode()) {
            return HttpStatus.NOT_FOUND;
        }

        // 参数校验类
        if (code == ErrorCode.CREDENTIAL_REQUIRED_FIELDS_MISSING.getCode()
                || code == ErrorCode.PASSWORD_LENGTH_TOO_SHORT.getCode()
                || code == ErrorCode.PASSWORD_LENGTH_TOO_LONG.getCode()
                || code == ErrorCode.NO_CHAR_TYPE_SELECTED.getCode()
                || code == ErrorCode.AUTO_LOCK_TIMEOUT_OUT_OF_RANGE.getCode()
                || code == ErrorCode.INVALID_REQUEST.getCode()) {
            return HttpStatus.BAD_REQUEST;
        }

        // 导入导出错误
        if (code >= 50000 && code < 60000) {
            return HttpStatus.BAD_REQUEST;
        }

        // 其他业务错误
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
