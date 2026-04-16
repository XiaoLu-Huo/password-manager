package com.pm.passwordmanager.infrastructure.encryption;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

/**
 * Excel 文件加密/解密工具类。
 * 使用 Apache POI 的 AGILE 加密模式对 Excel 文件进行密码保护。
 */
public final class ExcelEncryptionUtil {

    private ExcelEncryptionUtil() {
    }

    /**
     * 对 Excel 文件字节数组进行密码加密。
     *
     * @param excelBytes 未加密的 Excel 文件内容
     * @param password   加密密码
     * @return 加密后的 Excel 文件字节数组
     */
    public static byte[] encrypt(byte[] excelBytes, String password) {
        try (POIFSFileSystem fs = new POIFSFileSystem()) {
            EncryptionInfo info = new EncryptionInfo(EncryptionMode.agile);
            Encryptor encryptor = info.getEncryptor();
            encryptor.confirmPassword(password);

            try (OutputStream encStream = encryptor.getDataStream(fs)) {
                encStream.write(excelBytes);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fs.writeFilesystem(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Excel encryption failed", e);
        }
    }

    /**
     * 解密受密码保护的 Excel 文件。
     *
     * @param encryptedBytes 加密的 Excel 文件内容
     * @param password       解密密码
     * @return 解密后的 Excel 文件字节数组
     */
    public static byte[] decrypt(byte[] encryptedBytes, String password) {
        try (POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(encryptedBytes))) {
            EncryptionInfo info = new EncryptionInfo(fs);
            Decryptor decryptor = Decryptor.getInstance(info);

            if (!decryptor.verifyPassword(password)) {
                return null;
            }

            try (InputStream dataStream = decryptor.getDataStream(fs);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = dataStream.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                return out.toByteArray();
            }
        } catch (GeneralSecurityException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Excel decryption failed", e);
        }
    }
}
