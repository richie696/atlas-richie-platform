/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap.spi;

/**
 * 启动期只读客户端。实现不得创建无界线程或执行管理操作。
 */
public interface SecretBootstrapClient extends AutoCloseable {

    SecretBootstrapResult load(SecretBootstrapRequest request);

    @Override
    default void close() {
    }
}
