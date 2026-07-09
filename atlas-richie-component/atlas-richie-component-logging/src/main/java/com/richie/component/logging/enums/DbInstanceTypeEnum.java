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
package com.richie.component.logging.enums;

/**
 * 数据库实例类型枚举（用于日志/数据源等配置区分）。
 *
 * @author richie696
 * @since 2022-10-09
 */
public enum DbInstanceTypeEnum {

    /** 引用（共享）数据源 */
    REF,

    /** 独立数据源 */
    STANDALONE

}
