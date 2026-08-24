/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api;

/**
 * Provider 可以显式声明的 Secret 与密码运算能力。
 */
public enum SecretCapability {
    SECRET_READ,
    SECRET_WRITE,
    SECRET_VERSIONING,
    SECRET_DELETE,
    DYNAMIC_CREDENTIAL,
    LEASE_RENEW,
    DIRECT_ENCRYPT,
    DIRECT_DECRYPT,
    DATA_KEY_GENERATE,
    KEY_WRAP,
    KEY_UNWRAP,
    REWRAP,
    SIGN,
    VERIFY,
    HMAC,
    RANDOM,
    KEY_ROTATE
}
