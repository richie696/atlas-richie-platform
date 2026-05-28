package com.richie.component.mfa.core.crypto.hsm;

/**
 * HSM（硬件安全模块）引擎接口
 * <p>
 * 用于统一所有 HSM 实现（Thales、SafeNet 等）的加解密能力抽象。
 * <p>
 * 具体厂商实现类位于 {@code crypto.hsm} 包下，通过 Spring 条件装配和配置选择。
 * <p>
 * 此接口位于 core 模块，供 management 和 validation 模块共同使用
 *
 * @author richie696
 * @since 1.0.0
 */
public interface HsmEngine {

    /**
     * 使用 HSM 加密数据
     * <p>
     * 通过硬件安全模块对明文进行加密，提供硬件级别的安全保护
     *
     * @param plaintext 明文数据（必填）
     * @return 加密后的密文（通常为 Base64 编码或厂商自定义格式）
     * @throws RuntimeException 如果加密失败
     */
    String encrypt(String plaintext);

    /**
     * 使用 HSM 解密数据
     * <p>
     * 通过硬件安全模块对密文进行解密
     *
     * @param ciphertext 密文（通常为 Base64 编码或厂商自定义格式，必填）
     * @return 解密后的明文
     * @throws RuntimeException 如果解密失败
     */
    String decrypt(String ciphertext);

    /**
     * 检查 HSM 服务是否可用
     * <p>
     * 用于检查 HSM 设备连接是否正常，是否可以进行加密/解密操作
     *
     * @return HSM服务是否可用
     * <ul>
     *   <li>{@code true}：HSM设备可用，可以进行加密/解密操作</li>
     *   <li>{@code false}：HSM设备不可用，无法进行加密/解密操作</li>
     * </ul>
     */
    boolean isAvailable();
}
