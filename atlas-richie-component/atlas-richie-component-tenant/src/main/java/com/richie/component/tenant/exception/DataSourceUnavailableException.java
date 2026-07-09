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
package com.richie.component.tenant.exception;

import lombok.Getter;

/**
 * 数据源不可用异常。
 *
 * <p>当数据源熔断器处于 OPEN 状态时抛出，表示对应数据源暂时不可访问。
 * 可能影响单个租户（database 模式）或全租户（shared 数据源）。</p>
 *
 * @author richie696
 * @since 2.0
 */
@Getter
public class DataSourceUnavailableException extends RuntimeException {

    private final String dataSourceKey;

    public DataSourceUnavailableException(String dataSourceKey, String message) {
        super(message);
        this.dataSourceKey = dataSourceKey;
    }

    public DataSourceUnavailableException(String message) {
        super(message);
        this.dataSourceKey = null;
    }

}
