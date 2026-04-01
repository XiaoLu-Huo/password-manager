package com.pm.passwordmanager.util;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * 密码学安全随机数生成工具（CSPRNG）。
 * 基于 java.security.SecureRandom 封装，提供随机字节、随机整数等生成方法。
 */
@Component
public class SecureRandomUtil {

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 生成指定长度的随机字节数组。
     *
     * @param length 字节长度
     * @return 随机字节数组
     */
    public byte[] generateRandomBytes(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive");
        }
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    /**
     * 生成 [0, bound) 范围内的随机整数。
     *
     * @param bound 上界（不包含）
     * @return 随机整数
     */
    public int generateRandomInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        return secureRandom.nextInt(bound);
    }
}
