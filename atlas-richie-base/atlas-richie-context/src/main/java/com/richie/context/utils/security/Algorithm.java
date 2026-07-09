/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.context.utils.security;

/**
 * 支持的加密算法枚举
 * <p>
 * 涵盖国际标准算法（FIPS/NIST）与国密算法（GM/T），
 * 配合 {@link CryptoUtils} 统一入口调用。
 *
 * @author richie696
 * @version 1.0
 * @since 2026/06/02
 */
public enum Algorithm {

    /**
     * AES 对称加密（FIPS Pub 197）
     * <p>默认模式：AES/GCM/NoPadding，密钥长度 128/256-bit。</p>
     */
    AES,

    /**
     * RSA 非对称加密/数字签名（PKCS#1 v2.2）
     * <p>默认填充：OAEPWithSHA-256AndMGF1Padding，密钥长度 2048-bit 起。</p>
     */
    RSA,

    /**
     * ECDSA 椭圆曲线数字签名（FIPS Pub 186-4）
     * <p>默认曲线：secp256r1（P-256），签名算法：SHA256withECDSA。</p>
     */
    ECDSA,

    /**
     * ECDH 椭圆曲线密钥协商（NIST SP 800-56A）
     * <p>默认曲线：secp256r1（P-256），派生共享密钥。</p>
     */
    ECDH,

    /**
     * DSA 数字签名算法（FIPS Pub 186-4）
     * <p>推荐密钥长度 4096-bit，签名算法：SHA256withDSA。</p>
     */
    DSA,

    /**
     * SM2 国密椭圆曲线公钥密码算法（GM/T 0003-2012）
     * <p>支持加密、数字签名、密钥协商三种能力。</p>
     */
    SM2,

    /**
     * SM4 国密分组密码算法（GM/T 0002-2012）
     * <p>默认模式：SM4/GCM/NoPadding（BC 实现），密钥长度 128-bit。</p>
     */
    SM4
}
