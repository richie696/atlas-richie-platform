/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mfa.core.crypto.kms;

/**
 * 云KMS提供方接口
 * <p>
 * 用于统一所有云KMS服务商实现（AWS、阿里云、腾讯云、火山引擎、华为云）的接口
 * <p>
 * 实现类位于：crypto.kms 包
 * <p>
 * 支持的实现：
 * <ul>
 *   <li>{@link AwsKmsEngine}：AWS KMS</li>
 *   <li>{@link AliyunKmsEngine}：阿里云KMS</li>
 *   <li>{@link TencentKmsEngine}：腾讯云KMS</li>
 *   <li>{@link VolcengineKmsEngine}：火山引擎KMS</li>
 *   <li>{@link HuaweiKmsEngine}：华为云KMS</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
public interface CloudKmsEngine {

    /**
     * 加密数据
     * <p>
     * 使用云 KMS 服务对明文进行加密
     *
     * @param plaintext 明文数据（必填）
     * @return 加密后的密文（Base64编码或服务商特定格式）
     * @throws RuntimeException 如果加密失败
     */
    String encrypt(String plaintext);

    /**
     * 解密数据
     * <p>
     * 使用云 KMS 服务对密文进行解密
     *
     * @param ciphertext 密文（Base64编码或服务商特定格式，必填）
     * @return 解密后的明文
     * @throws RuntimeException 如果解密失败
     */
    String decrypt(String ciphertext);

    /**
     * 检查云KMS服务是否可用
     * <p>
     * 用于检查云 KMS 服务连接是否正常，是否可以进行加密/解密操作
     *
     * @return 云KMS服务是否可用
     * <ul>
     *   <li>{@code true}：服务可用，可以进行加密/解密操作</li>
     *   <li>{@code false}：服务不可用，无法进行加密/解密操作</li>
     * </ul>
     */
    boolean isAvailable();
}
