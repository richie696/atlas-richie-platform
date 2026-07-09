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
package com.richie.component.logging.enums;

/**
 * 记录类型枚举
 *
 * @author richie696
 * @version 1.0
 * @since 2022-12-30 09:49:42
 */
public enum RecordTypeEnum {

    /**
     * 日志文件
     */
    FILE,

    /**
     * Redis缓存（记录到缓存需要在项目中配置Redis，否则运行时会出错导致无法正确记录）
     */
    REDIS,

    /**
     * 消息队列（记录到消息队列需要在项目中配置Messaging组件的配置信息，否则运行时会出错导致无法正确记录）
     */
    MQ

}
