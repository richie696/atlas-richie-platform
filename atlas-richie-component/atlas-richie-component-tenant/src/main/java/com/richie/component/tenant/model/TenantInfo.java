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
package com.richie.component.tenant.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 租户运行时信息。
 *
 * <p>由 {@code TenantInfoProvider} 提供，包含租户的隔离模式、数据源名称、Schema 名称、
 * 表后缀、状态和灰度标志。策略层和拦截器层基于此对象执行隔离逻辑。</p>
 *
 * @author richie696
 * @since 2.0
 */
@Data
@Accessors(chain = true)
public class TenantInfo {

    /**
     * 租户 ID（数据库主键，Long 类型）
     */
    private Long tenantId;

    /**
     * 隔离模式
     */
    private IsolationMode mode;

    /**
     * 数据源名称（database 模式下指向租户专属数据源的 key）
     */
    private String dataSourceName;

    /**
     * 灰度数据源名称（canary 模式下指向灰度数据源）
     */
    private String canaryDataSourceName;

    /**
     * Schema 名称（schema 模式下使用，须经白名单校验：{@code ^[a-zA-Z0-9_]+$}）
     */
    private String schemaName;

    /**
     * 表名后缀（table 模式下使用，如 {@code "_1001"}）
     */
    private String tableSuffix;

    /**
     * 租户状态
     */
    private TenantStatus status;

    /**
     * 是否处于灰度阶段
     */
    private boolean canary;
}
