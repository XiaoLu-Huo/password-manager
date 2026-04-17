package com.pm.passwordmanager.domain.service.impl;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.pm.passwordmanager.domain.service.SessionService;

/**
 * 会话服务实现。
 * 在内存中管理用户的 DEK 会话，支持自动锁定超时检测。
 * 锁定时安全擦除内存中的 DEK 数据。
 * 支持 Bearer Token 认证：生成 UUID 令牌并映射到用户会话。
 */
@Service
public class SessionServiceImpl implements SessionService {

    static final int DEFAULT_AUTO_LOCK_MINUTES = 15;
    static final int MIN_AUTO_LOCK_MINUTES = 1;
    static final int MAX_AUTO_LOCK_MINUTES = 60;

    private final Map<Long, SessionData> sessions = new ConcurrentHashMap<>();
    /** token → userId mapping for Bearer token lookup */
    private final Map<String, Long> tokenToUser = new ConcurrentHashMap<>();
    /** userId → token mapping for cleanup on session clear */
    private final Map<Long, String> userToToken = new ConcurrentHashMap<>();

    @Override
    public void storeDek(Long userId, byte[] dek) {
        // Preserve timeout setting from existing session before clearing
        int timeout = DEFAULT_AUTO_LOCK_MINUTES;
        SessionData existing = sessions.get(userId);
        if (existing != null) {
            timeout = existing.autoLockMinutes;
        }

        // Clear any existing session (secure wipe of old DEK)
        clearSession(userId);

        sessions.put(userId, new SessionData(
                dek.clone(),
                Instant.now(),
                timeout
        ));
    }

    @Override
    public byte[] getDek(Long userId) {
        SessionData session = sessions.get(userId);
        if (session == null) {
            return null;
        }

        if (isExpired(session)) {
            clearSession(userId);
            return null;
        }

        return session.dek;
    }

    @Override
    public void clearSession(Long userId) {
        SessionData session = sessions.remove(userId);
        if (session != null && session.dek != null) {
            // Secure wipe: overwrite DEK bytes with zeros
            Arrays.fill(session.dek, (byte) 0);
        }
        // Clean up token mappings
        String token = userToToken.remove(userId);
        if (token != null) {
            tokenToUser.remove(token);
        }
    }

    @Override
    public boolean isSessionActive(Long userId) {
        SessionData session = sessions.get(userId);
        if (session == null) {
            return false;
        }

        if (isExpired(session)) {
            clearSession(userId);
            return false;
        }

        return true;
    }

    @Override
    public void refreshActivity(Long userId) {
        SessionData session = sessions.get(userId);
        if (session != null && !isExpired(session)) {
            session.lastActivity = Instant.now();
        }
    }

    @Override
    public void setAutoLockTimeout(Long userId, int minutes) {
        if (minutes < MIN_AUTO_LOCK_MINUTES || minutes > MAX_AUTO_LOCK_MINUTES) {
            throw new IllegalArgumentException(
                    "Auto-lock timeout must be between " + MIN_AUTO_LOCK_MINUTES
                            + " and " + MAX_AUTO_LOCK_MINUTES + " minutes");
        }

        SessionData session = sessions.get(userId);
        if (session != null) {
            session.autoLockMinutes = minutes;
        }
    }

    @Override
    public int getAutoLockTimeout(Long userId) {
        SessionData session = sessions.get(userId);
        if (session != null) {
            return session.autoLockMinutes;
        }
        return DEFAULT_AUTO_LOCK_MINUTES;
    }

    private boolean isExpired(SessionData session) {
        Instant expiry = session.lastActivity.plusSeconds((long) session.autoLockMinutes * 60);
        return Instant.now().isAfter(expiry);
    }

    @Override
    public String generateToken(Long userId) {
        // Remove old token if exists
        String oldToken = userToToken.remove(userId);
        if (oldToken != null) {
            tokenToUser.remove(oldToken);
        }
        String token = UUID.randomUUID().toString();
        tokenToUser.put(token, userId);
        userToToken.put(userId, token);
        return token;
    }

    @Override
    public Long getUserIdByToken(String token) {
        if (token == null) {
            return null;
        }
        Long userId = tokenToUser.get(token);
        if (userId == null) {
            return null;
        }
        // Verify session is still active (not expired)
        if (!isSessionActive(userId)) {
            // Session expired — clean up token
            tokenToUser.remove(token);
            userToToken.remove(userId);
            return null;
        }
        return userId;
    }

    /**
     * Internal session data holder.
     */
    static class SessionData {
        byte[] dek;
        volatile Instant lastActivity;
        volatile int autoLockMinutes;

        SessionData(byte[] dek, Instant lastActivity, int autoLockMinutes) {
            this.dek = dek;
            this.lastActivity = lastActivity;
            this.autoLockMinutes = autoLockMinutes;
        }
    }
}
