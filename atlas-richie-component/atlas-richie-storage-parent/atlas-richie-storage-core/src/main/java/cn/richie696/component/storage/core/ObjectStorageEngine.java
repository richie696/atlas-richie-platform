/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.storage.core;

/**
 * 统一对象存储运行时代理接口。
 *
 * <p>手工模式下 {@code objectStorageEngine} 是可热切换的 JDK 代理。把服务端
 * 存储能力和直传/直读能力合并到同一个代理类型，避免代理只声明
 * {@link StorageEngine} 时导致 {@link DirectStorageEngine} 注入或类型判断失败。</p>
 */
public interface ObjectStorageEngine extends StorageEngine, DirectStorageEngine {
}
