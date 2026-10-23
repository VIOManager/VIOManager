package com.viomanager.person.support;

import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import cn.hutool.core.util.StrUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 人员模块敏感数据处理支撑：身份证 AES/GCM 加解密 + 手机号脱敏。
 * 建表注释明确 id_card "AES，禁止明文入库"；工程未引 hutool-crypto，这里直接用 JDK 自带
 * JCE 实现（AES-128-GCM，随机 IV 前置拼进密文，Base64 存储）。
 * 密钥来源：环境变量 VIO_IDCARD_AES_KEY（PRD §5 配置走环境变量；@Value 直接读环境变量，
 * 不需要改 application.yml）。⚠️ 生产必须设置强随机密钥；轮换密钥后旧密文无法解密。
 */
@Component
public class PersonSensitiveSupport {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec keySpec;

    private final SecureRandom random = new SecureRandom();

    public PersonSensitiveSupport(
            @Value("${VIO_IDCARD_AES_KEY:viomanager-dev-idcard-aes-key-32bytes-min}") String secret) {
        this.keySpec = deriveKey(secret);
    }

    /**
     * 身份证号明文 → AES/GCM 密文（Base64）。空值原样返回 null，保证"未填身份证"不落脏数据。
     */
    public String encryptIdCard(String plain) {
        if (StrUtil.isBlank(plain)) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.trim().getBytes(StandardCharsets.UTF_8));
            byte[] stored = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, stored, 0, iv.length);
            System.arraycopy(cipherText, 0, stored, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(stored);
        } catch (Exception e) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "身份证号加密失败");
        }
    }

    /**
     * 密文 → 身份证号明文。解密失败（密钥轮换/数据损坏）返回 null 而不是抛错，
     * 避免个别脏数据把整页查询打挂；调用方按"无明文"处理即可。
     */
    public String decryptIdCard(String stored) {
        if (StrUtil.isBlank(stored)) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            if (all.length <= IV_LENGTH) {
                return null;
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec,
                    new GCMParameterSpec(TAG_BITS, all, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 身份证号后 4 位（含末位 X），存 id_card_last4 供列表脱敏展示，避免为展示而解密。
     */
    public String last4OfIdCard(String plain) {
        String trimmed = StrUtil.trimToNull(plain);
        if (trimmed == null) {
            return null;
        }
        return trimmed.length() <= 4
                ? trimmed
                : trimmed.substring(trimmed.length() - 4);
    }

    /**
     * 任意长度口令 → 16 字节 AES-128 密钥（SHA-256 摘要取前 16 位），
     * 避免"密钥必须正好 16 字节"的使用门槛。
     */
    private SecretKeySpec deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            byte[] key = new byte[16];
            System.arraycopy(hash, 0, key, 0, key.length);
            return new SecretKeySpec(key, ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必带算法，到这里说明 JVM 环境异常，直接起不来比带病运行好
            throw new IllegalStateException("初始化身份证加密密钥失败", e);
        }
    }
}
