/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api;

/**
 * 在受控生命周期内消费临时明文的函数接口。
 *
 * <p>回调返回后组件立即清零传入数组。调用方不得保存数组引用；确需长期副本时必须自行承担清零责任。</p>
 */
@FunctionalInterface
public interface SecretCallback<T> {

    T apply(byte[] plaintext);
}
