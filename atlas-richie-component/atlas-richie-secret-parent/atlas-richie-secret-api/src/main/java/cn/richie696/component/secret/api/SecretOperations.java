/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api;

import cn.richie696.component.secret.api.crypto.SignatureValue;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;

/**
 * 面向业务项目的统一最少知识外观。
 *
 * <p>业务只传递逻辑名称和业务数据。版本选择、Provider 路由、命名空间、AAD、
 * 物理 Key、Envelope、算法与明文生命周期全部由组件内部处理。</p>
 */
public interface SecretOperations {

    <T> T read(String logicalName, SecretCallback<T> callback);

    String encrypt(String logicalKey, byte[] plaintext);

    <T> T decrypt(String ciphertext, SecretCallback<T> callback);

    /** Signs data with a Provider-managed, non-exportable private key. */
    default SignatureValue sign(String logicalKey, byte[] payload) {
        throw new SecretConfigurationException(
                "SEC-CAP-001", "The selected Secret Provider does not support signing");
    }

    /** Verifies data with a Provider-managed, non-exportable private key. */
    default boolean verify(String logicalKey, byte[] payload, SignatureValue signature) {
        throw new SecretConfigurationException(
                "SEC-CAP-001", "The selected Secret Provider does not support signature verification");
    }
}
