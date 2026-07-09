/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mongodb.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * MongoDB Micrometer 指标记录器
 *
 * <p>提供 MongoDB 操作的指标收集功能：
 * <ul>
 *   <li><strong>Timer</strong>：记录操作耗时分布</li>
 *   <li><strong>Counter</strong>：记录操作成功/失败次数</li>
 *   <li><strong>Error Counter</strong>：按错误类型统计异常</li>
 * </ul>
 */
@Component
public class MongodbMetricsRecorder {

    private static final String METRIC_PREFIX = "mongodb.";
    private static final String OPERATION_DURATION = METRIC_PREFIX + "operation.duration";
    private static final String OPERATION_COUNT = METRIC_PREFIX + "operation.count";
    private static final String ERRORS_COUNT = METRIC_PREFIX + "errors";

    private final MeterRegistry meterRegistry;

    public MongodbMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 开始计时
     *
     * @param operation 操作类型
     * @param collection 集合名称
     * @return Timer.Sample 用于后续停止计时
     */
    public Timer.Sample start(String operation, String collection) {
        return Timer.start(meterRegistry);
    }

    /**
     * 停止计时并记录结果
     *
     * @param sample 计时起点
     * @param operation 操作类型
     * @param collection 集合名称
     * @param success 是否成功
     */
    public void stop(Timer.Sample sample, String operation, String collection, boolean success) {
        String resultTag = success ? "success" : "error";
        Timer timer = meterRegistry.timer(OPERATION_DURATION,
                "operation", operation,
                "collection", collection,
                "result", resultTag);
        sample.stop(timer);

        Counter counter = meterRegistry.counter(OPERATION_COUNT,
                "operation", operation,
                "collection", collection,
                "result", resultTag);
        counter.increment();
    }

    /**
     * 记录错误
     *
     * @param t 异常对象
     */
    public void recordError(Throwable t) {
        String errorType = t.getClass().getSimpleName();
        Counter counter = meterRegistry.counter(ERRORS_COUNT, "error_type", errorType);
        counter.increment();
    }
}
