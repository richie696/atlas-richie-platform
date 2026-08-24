/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.crypto;

/**
 * 密钥用途，防止同一逻辑引用跨用途误用。
 */
public enum KeyPurpose {
    ENVELOPE_ENCRYPTION,
    SIGNING,
    HMAC,
    DIRECT_ENCRYPTION
}
