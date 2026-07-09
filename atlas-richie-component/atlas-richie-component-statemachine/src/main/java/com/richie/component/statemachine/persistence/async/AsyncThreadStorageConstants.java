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
package com.richie.component.statemachine.persistence.async;

/**
 * 异步线程池存储管理器常量
 * <p>
 * 仅限当前包访问，包含线程池、线程名称、键分隔符等常量定义。
 * 
 *
 * @author richie696
 * @since 1.0.0
 */
interface AsyncThreadStorageConstants {

    /**
     * 线程池关闭等待时间（秒）
     */
    int SHUTDOWN_WAIT_SECONDS = 5;

    /**
     * 历史记录键分隔符
     */
    String HISTORY_KEY_SEPARATOR = ":";
}
