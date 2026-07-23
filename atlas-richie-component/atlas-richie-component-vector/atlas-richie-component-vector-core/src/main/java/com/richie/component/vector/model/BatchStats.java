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
package com.richie.component.vector.model;

import java.time.Duration;

/**
 * 批次执行统计。
 *
 * @param total             总记录数
 * @param succeeded         成功数
 * @param failed            失败数
 * @param elapsed           总耗时
 * @param embeddingApiCalls 嵌入 API 调用次数（按批次数算，例如一次 embed(List) 算 1）
 * @param writeApiCalls     写入 API 调用次数
 * @author richie696
 * @since 2.0.0
 */
public record BatchStats(
        long total,
        long succeeded,
        long failed,
        Duration elapsed,
        long embeddingApiCalls,
        long writeApiCalls
) {

    /**
     * 构造一个空的初始状态（total=0）
     */
    public static BatchStats empty() {
        return new BatchStats(0, 0, 0, Duration.ZERO, 0, 0);
    }
}