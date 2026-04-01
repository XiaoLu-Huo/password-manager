package com.pm.passwordmanager.service.impl;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.pm.passwordmanager.service.SessionService;

/**
 * 会话服务实现。
 * 在内存中管理用户的 DEK 会话，支持自动锁定超时检测。
 * 锁定时安全擦除内存中的 DEK 数据。
 */
@Service
public class SessionServiceImpl implements SessionService {

    static final int DEFAULT_AUTO_LOCK_MINUTES = 5;
    static final int MIN_AUTO_LOCK_MINUTES = 1;
    static final int MAX_AUTO_LOCK_MINUTES = 60;

    private final Map<Long, SessionData> sessions = new ConcurrentHashMap<>();

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
