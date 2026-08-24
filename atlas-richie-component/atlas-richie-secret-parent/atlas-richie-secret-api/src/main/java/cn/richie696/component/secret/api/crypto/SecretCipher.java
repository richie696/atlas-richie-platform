/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.crypto;

import cn.richie696.component.secret.api.SecretCallback;

/**
 * 面向业务项目的最小知识加密门面。
 *
 * <p>调用方只需知道逻辑 Key、待处理值和业务 AAD 上下文，不需要接触 Provider、
 * 物理 Key ID、Wrapped DEK、算法参数或 Envelope 编解码器。</p>
 */
public interface SecretCipher {

    String encrypt(String logicalKey, byte[] plaintext);

    <T> T decrypt(String ciphertext, SecretCallback<T> callback);

    String rewrap(String ciphertext, String targetLogicalKey);
}
