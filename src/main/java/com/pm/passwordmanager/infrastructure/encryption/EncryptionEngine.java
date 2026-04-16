package com.pm.passwordmanager.infrastructure.encryption;

import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * AES-256-GCM 加密引擎。
 * 提供凭证数据的加密、解密以及密钥和 IV 的生成。
 */
@Component
public class EncryptionEngine {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12; // 96-bit
    private static final int DEK_LENGTH_BYTES = 32; // 256-bit

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 使用 AES-256-GCM 加密明文数据。
     *
     * @param plaintext 明文字节数组
     * @param key       AES-256 密钥（32 字节）
     * @return 包含密文和 IV 的 EncryptedData
     */
    public EncryptedData encrypt(byte[] plaintext, byte[] key) {
        try {
            byte[] iv = generateIv();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return new EncryptedData(ciphertext, iv);
        } catch (Exception e) {
            throw new RuntimeException("AES-256-GCM encryption failed", e);
        }
    }

    /**
     * 使用 AES-256-GCM 解密密文数据。
     *
     * @param encryptedData 包含密文和 IV 的 EncryptedData
     * @param key           AES-256 密钥（32 字节）
     * @return 解密后的明文字节数组
     */
    public byte[] decrypt(EncryptedData encryptedData, byte[] key) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, encryptedData.getIv()));
            return cipher.doFinal(encryptedData.getCiphertext());
        } catch (Exception e) {
            throw new RuntimeException("AES-256-GCM decryption failed", e);
        }
    }

    /**
     * 生成随机的数据加密密钥（DEK），256-bit。
     *
     * @return 32 字节的随机密钥
     */
    public byte[] generateDek() {
        byte[] dek = new byte[DEK_LENGTH_BYTES];
        secureRandom.nextBytes(dek);
        return dek;
    }

    /**
     * 生成随机的初始化向量（IV），96-bit。
     *
     * @return 12 字节的随机 IV
     */
    public byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        return iv;
    }
}
