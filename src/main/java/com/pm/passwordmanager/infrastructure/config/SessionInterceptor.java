package com.pm.passwordmanager.infrastructure.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.pm.passwordmanager.domain.service.SessionService;
import com.pm.passwordmanager.exception.BusinessException;
import com.pm.passwordmanager.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 会话拦截器。
 * 对受保护的 API 端点验证 Bearer Token，并将 userId 存入请求属性。
 * 同时刷新用户活动时间以防止自动锁定。
 */
@Component
@RequiredArgsConstructor
public class SessionInterceptor implements HandlerInterceptor {

    public static final String USER_ID_ATTR = "currentUserId";

    private final SessionService sessionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS preflight requests should pass through
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }

        String token = authHeader.substring(7);
        Long userId = sessionService.getUserIdByToken(token);
        if (userId == null) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }

        // Refresh activity to prevent auto-lock timeout
        sessionService.refreshActivity(userId);

        // Store userId in request for controllers to use
        request.setAttribute(USER_ID_ATTR, userId);
        return true;
    }
}
