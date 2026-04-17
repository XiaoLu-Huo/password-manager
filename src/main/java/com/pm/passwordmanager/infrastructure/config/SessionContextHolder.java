package com.pm.passwordmanager.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 从当前请求上下文中获取会话拦截器设置的 userId。
 */
public final class SessionContextHolder {

    private SessionContextHolder() {}

    /**
     * 获取当前请求中经过会话拦截器验证的用户 ID。
     *
     * @return userId，如果不在请求上下文中则返回 null
     */
    public static Long getCurrentUserId() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            Object userId = request.getAttribute(SessionInterceptor.USER_ID_ATTR);
            if (userId instanceof Long) {
                return (Long) userId;
            }
        }
        return null;
    }
}
