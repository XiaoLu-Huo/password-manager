package com.pm.passwordmanager.infrastructure.encryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;

/**
 * Property 8: 凭证加密往返
 * 验证：对任意数据使用 DEK 加密后再解密，应得到原始数据。
 *
 * Validates: Requirements 3.3
 */
@Label("Feature: password-manager, Property 8: 凭证加密往返")
class CredentialEncryptionPropertyTest {

    private final EncryptionEngine encryptionEngine = new EncryptionEngine();

    /**
     * 对任意字节数组明文，使用生成的 DEK 加密后再用相同 DEK 解密，应得到原始明文。
     *
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 100)
    @Label("should_returnOriginalPlaintext_when_encryptThenDecryptWithSameDek")
    void should_returnOriginalPlaintext_when_encryptThenDecryptWithSameDek(
            @ForAll @Size(min = 1, max = 512) byte[] plaintext
    ) {
        byte[] dek = encryptionEngine.generateDek();

        EncryptedData encrypted = encryptionEngine.encrypt(plaintext, dek);
        byte[] decrypted = encryptionEngine.decrypt(encrypted, dek);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    /**
     * 对任意字节数组明文，使用一个 DEK 加密后用不同的 DEK 解密，应抛出异常。
     *
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 100)
    @Label("should_failDecryption_when_differentDekUsed")
    void should_failDecryption_when_differentDekUsed(
            @ForAll @Size(min = 1, max = 512) byte[] plaintext
    ) {
        byte[] dek1 = encryptionEngine.generateDek();
        byte[] dek2 = encryptionEngine.generateDek();
        Assume.that(!java.util.Arrays.equals(dek1, dek2));

        EncryptedData encrypted = encryptionEngine.encrypt(plaintext, dek1);

        assertThatThrownBy(() -> encryptionEngine.decrypt(encrypted, dek2))
                .isInstanceOf(RuntimeException.class);
    }
}
