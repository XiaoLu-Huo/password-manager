package com.pm.passwordmanager.service.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.pm.passwordmanager.service.SessionService;

/**
 * 会话服务实现。
 * 在内存中管理用户的 DEK 会话。
 * 注意：完整的自动锁定超时逻辑将在 task 4.6 中实现。
 */
@Service
public class SessionServiceImpl implements SessionService {

    private final Map<Long, byte[]> dekStore = new ConcurrentHashMap<>();

    @Override
    public void storeDek(Long userId, byte[] dek) {
        dekStore.put(userId, dek);
    }

    @Override
    public byte[] getDek(Long userId) {
        return dekStore.get(userId);
    }

    @Override
    public void clearSession(Long userId) {
        dekStore.remove(userId);
    }

    @Override
    public boolean isSessionActive(Long userId) {
        return dekStore.containsKey(userId);
    }
}
