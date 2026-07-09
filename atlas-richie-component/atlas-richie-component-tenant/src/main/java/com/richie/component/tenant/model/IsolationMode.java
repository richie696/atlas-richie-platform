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
package com.richie.component.tenant.model;

/**
 * 多租户隔离模式枚举。
 *
 * <p>定义五种标准隔离模式，每种模式对应不同的数据隔离策略。
 * 在 Hybrid 模式下，每个租户固定使用其中一种模式。</p>
 *
 * @author richie696
 * @since 2.0
 */
public enum IsolationMode {

    /**
     * 列隔离：共享数据库、共享表，通过 {@code tenant_id} 列区分租户数据。
     * SQL 自动注入 {@code WHERE tenant_id = ?} 条件。
     */
    COLUMN,

    /**
     * 表隔离：共享数据库，每个租户独立表（表名后缀区分）。
     * SQL 执行前动态替换表名。
     */
    TABLE,

    /**
     * Schema 隔离：共享数据库实例，每个租户独立 Schema。
     * SQL 执行前通过 {@code SET LOCAL search_path} 切换 Schema（仅 PostgreSQL/Oracle）。
     */
    SCHEMA,

    /**
     * 数据库隔离：每个租户独立数据库实例。
     * 通过 {@code AbstractRoutingDataSource} 路由到租户专属数据源。
     */
    DATABASE,

    /**
     * 混合模式：按租户固定隔离模式。
     * 运行时根据 {@code TenantInfo.mode} 分派到对应的策略实现。
     */
    HYBRID
}
