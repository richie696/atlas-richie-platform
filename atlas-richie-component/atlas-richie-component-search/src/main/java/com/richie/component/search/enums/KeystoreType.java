package com.richie.component.search.enums;

/**
 * 证书类型枚举
 *
 * <p>定义支持的 SSL 证书文件格式。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-08-08
 */
public enum KeystoreType {
    /**
     * JKS 格式
     *
     * <p>Java KeyStore 格式，Java 原生支持。
     */
    JKS,

    /**
     * PKCS12 格式
     *
     * <p>跨平台标准格式，推荐使用。
     */
    PKCS12,

    /**
     * PEM 格式
     *
     * <p>文本格式证书，易于查看和编辑。
     */
    PEM
}
