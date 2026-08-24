/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api;

/**
 * 运行期 Secret 只读入口。
 */
public interface SecretResolver {

    default SecretValue resolve(String logicalName) {
        return resolve(SecretReference.latest(logicalName));
    }

    SecretValue resolve(SecretReference reference);

    default SecretMetadata metadata(String logicalName) {
        return metadata(SecretReference.latest(logicalName));
    }

    SecretMetadata metadata(SecretReference reference);
}
