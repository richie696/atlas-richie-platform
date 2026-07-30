/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.richie696.component.storage.core;

/**
 * 旧版聚合存储接口。
 *
 * @deprecated 请按调用侧职责替换为 {@link ServerStorageEngine} 或 {@link DirectStorageEngine}；
 * 本接口将在 1.0.0 正式版中彻底移除。
 */
@Deprecated(since = "1.0.0", forRemoval = true)
public interface StorageEngine extends ServerStorageEngine, DirectStorageEngine {
}
