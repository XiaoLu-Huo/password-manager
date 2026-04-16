package com.pm.passwordmanager.infrastructure.encryption;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * AES-256-GCM 加密结果，包含密文和初始化向量。
 */
@Data
@AllArgsConstructor
public class EncryptedData {
    private byte[] ciphertext;
    private byte[] iv;
}
