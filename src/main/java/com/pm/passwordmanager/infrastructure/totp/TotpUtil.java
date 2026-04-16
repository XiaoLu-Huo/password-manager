package com.pm.passwordmanager.infrastructure.totp;

import org.springframework.stereotype.Component;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.recovery.RecoveryCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import static dev.samstevens.totp.util.Utils.getDataUriForImage;

/**
 * TOTP 工具类。
 * 使用 java-totp 库实现 TOTP 密钥生成、二维码 URI 生成和验证码验证。
 */
@Component
public class TotpUtil {

    private static final String ISSUER = "PasswordManager";
    private static final int SECRET_LENGTH = 32;
    private static final int RECOVERY_CODE_COUNT = 8;
    private static final int DIGITS = 6;
    private static final int PERIOD = 30;

    private final SecretGenerator secretGenerator;
    private final CodeVerifier codeVerifier;
    private final RecoveryCodeGenerator recoveryCodeGenerator;

    public TotpUtil() {
        this.secretGenerator = new DefaultSecretGenerator(SECRET_LENGTH);
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, DIGITS);
        this.codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        this.recoveryCodeGenerator = new RecoveryCodeGenerator();
    }

    /**
     * 生成 TOTP 密钥。
     *
     * @return 32 字符的 Base32 编码密钥
     */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /**
     * 生成用于认证器应用扫描的二维码 Data URI。
     *
     * @param secret TOTP 密钥
     * @param label  用户标识（如邮箱或用户名）
     * @return Base64 编码的 PNG 图片 Data URI
     */
    public String generateQrCodeDataUri(String secret, String label) {
        QrData qrData = new QrData.Builder()
                .label(label)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(DIGITS)
                .period(PERIOD)
                .build();
        try {
            QrGenerator generator = new ZxingPngQrGenerator();
            byte[] imageData = generator.generate(qrData);
            return getDataUriForImage(imageData, generator.getImageMimeType());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate TOTP QR code", e);
        }
    }

    /**
     * 验证用户提交的 TOTP 验证码。
     *
     * @param secret TOTP 密钥
     * @param code   用户输入的 6 位验证码
     * @return 验证通过返回 true，否则返回 false
     */
    public boolean verifyCode(String secret, String code) {
        return codeVerifier.isValidCode(secret, code);
    }

    /**
     * 生成一组一次性恢复码。
     *
     * @return 恢复码数组
     */
    public String[] generateRecoveryCodes() {
        return recoveryCodeGenerator.generateCodes(RECOVERY_CODE_COUNT);
    }
}
