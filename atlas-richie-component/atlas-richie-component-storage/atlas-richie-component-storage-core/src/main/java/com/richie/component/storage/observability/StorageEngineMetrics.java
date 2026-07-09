/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.observability;

import com.richie.component.storage.enums.StorageEngineEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 存储引擎运行时计数器容器
 * <p>
 * 为每个引擎类型维护独立的 {@link AtomicLong} 计数器：
 * <ul>
 *   <li>{@link #switchCount(StorageEngineEnum)} — 运行时切换累计次数</li>
 *   <li>{@link #registerCount(StorageEngineEnum)} — 启动时注册累计次数</li>
 * </ul>
 * 由 {@link com.richie.component.storage.core.StorageEngineRegistry} 在切换/注册时递增。
 * 监控侧通过 {@link StorageMetricsBinder} 将这些原子计数器绑定到 Micrometer。
 *
 * @author richie696
 */
@Slf4j
public class StorageEngineMetrics {

    private final Map<StorageEngineEnum, AtomicLong> switchCounts = new EnumMap<>(StorageEngineEnum.class);
    private final Map<StorageEngineEnum, AtomicLong> registerCounts = new EnumMap<>(StorageEngineEnum.class);

    public StorageEngineMetrics() {
        for (StorageEngineEnum type : StorageEngineEnum.values()) {
            switchCounts.put(type, new AtomicLong());
            registerCounts.put(type, new AtomicLong());
        }
    }

    /**
     * 获取指定引擎类型的切换次数计数器（永不为 null）
     */
    public AtomicLong switchCount(StorageEngineEnum type) {
        return switchCounts.get(type);
    }

    /**
     * 获取指定引擎类型的注册次数计数器（永不为 null）
     */
    public AtomicLong registerCount(StorageEngineEnum type) {
        return registerCounts.get(type);
    }

    /**
     * 递增指定引擎类型的切换次数
     */
    public void incrementSwitch(StorageEngineEnum type) {
        switchCounts.get(type).incrementAndGet();
    }

    /**
     * 递增指定引擎类型的注册次数
     */
    public void incrementRegister(StorageEngineEnum type) {
        registerCounts.get(type).incrementAndGet();
    }
}
