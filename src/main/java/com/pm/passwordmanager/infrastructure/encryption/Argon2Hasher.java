package com.pm.passwordmanager.infrastructure.encryption;

import java.security.SecureRandom;
import java.util.Base64;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.springframework.stereotype.Component;

/**
 * Argon2id 哈希工具类。
 * 使用 Bouncy Castle 实现主密码的哈希、验证和密钥派生。
 */
@Component
public class Argon2Hasher {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int ITERATIONS = 3;
    private static final int MEMORY_KB = 65536; // 64 MB
    private static final int PARALLELISM = 4;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 使用 Argon2id 对密码进行哈希。
     *
     * @param password 明文密码
     * @param salt     盐值
     * @return Base64 编码的哈希值
     */
    public String hash(String password, byte[] salt) {
        byte[] hashBytes = computeArgon2id(password, salt, HASH_LENGTH);
        return Base64.getEncoder().encodeToString(hashBytes);
    }

    /**
     * 验证密码与哈希是否匹配。
     *
     * @param password     明文密码
     * @param salt         盐值
     * @param expectedHash Base64 编码的期望哈希值
     * @return 匹配返回 true，否则返回 false
     */
    public boolean verify(String password, byte[] salt, String expectedHash) {
        String actualHash = hash(password, salt);
        return constantTimeEquals(actualHash, expectedHash);
    }

    /**
     * 从密码派生指定长度的密钥（用于 KEK）。
     *
     * @param password       明文密码
     * @param salt           盐值
     * @param keyLengthBytes 密钥长度（字节）
     * @return 派生的密钥字节数组
     */
    public byte[] deriveKey(String password, byte[] salt, int keyLengthBytes) {
        return computeArgon2id(password, salt, keyLengthBytes);
    }

    /**
     * 生成密码学安全的随机盐值。
     *
     * @return 16 字节的随机盐值
     */
    public byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        return salt;
    }

    private byte[] computeArgon2id(String password, byte[] salt, int outputLength) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withIterations(ITERATIONS)
                .withMemoryAsKB(MEMORY_KB)
                .withParallelism(PARALLELISM)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        byte[] result = new byte[outputLength];
        generator.generateBytes(password.toCharArray(), result);
        return result;
    }

    /**
     * 常量时间字符串比较，防止时序攻击。
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
