/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api;

/**
 * 可显式销毁的 Secret 值。调用方必须关闭实例并清零复制出的数组。
 */
public interface SecretValue extends AutoCloseable {

    byte[] copyBytes();

    char[] copyChars();

    int size();

    boolean destroyed();

    @Override
    void close();
}
