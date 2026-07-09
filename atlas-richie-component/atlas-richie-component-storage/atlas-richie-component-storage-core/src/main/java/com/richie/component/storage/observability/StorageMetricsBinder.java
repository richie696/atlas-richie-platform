/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.observability;

import com.richie.component.storage.core.StorageEngineRegistry;
import com.richie.component.storage.enums.StorageEngineEnum;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 存储引擎运行时指标绑定器（Micrometer）
 * <p>
 * 实现 {@link MeterBinder}，Spring Boot Actuator 在启动时自动调用
 * {@link #bindTo(MeterRegistry)} 将以下指标注册到监控系统：
 * <ul>
 *   <li>{@code storage.engine.switch.total}（Gauge，tag=engine）— 累计切换次数</li>
 *   <li>{@code storage.engine.register.total}（Gauge，tag=engine）— 累计启动注册次数</li>
 *   <li>{@code storage.engine.default.type}（Gauge）— 当前默认引擎类型枚举名（{@code hashCode()}）</li>
 *   <li>{@code storage.engine.registered.count}（Gauge）— 当前已注册引擎数量</li>
 * </ul>
 * 注册/切换次数通过 {@link StorageEngineRegistry#getMetrics()} 公开的 {@link AtomicLong} 暴露。
 *
 * @author richie696
 */
@Slf4j
@RequiredArgsConstructor
public class StorageMetricsBinder implements MeterBinder {

    private final StorageEngineRegistry registry;

    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        StorageEngineMetrics metrics = registry.getMetrics();
        meterRegistry.gauge("storage.engine.default.type", registry,
                r -> (double) r.getCurrentEngineType().hashCode());
        meterRegistry.gauge("storage.engine.registered.count", registry,
                r -> (double) r.snapshot().size());

        for (StorageEngineEnum type : StorageEngineEnum.values()) {
            meterRegistry.gauge("storage.engine.switch.total",
                    io.micrometer.core.instrument.Tags.of(Tag.of("engine", type.name())),
                    metrics.switchCount(type), AtomicLong::doubleValue);
            meterRegistry.gauge("storage.engine.register.total",
                    io.micrometer.core.instrument.Tags.of(Tag.of("engine", type.name())),
                    metrics.registerCount(type), AtomicLong::doubleValue);
        }
        log.info("存储引擎指标已注册到 MeterRegistry");
    }
}
