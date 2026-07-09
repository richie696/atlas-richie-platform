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
package com.richie.component.tenant.strategy;

import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import org.apache.ibatis.plugin.Invocation;

/**
 * 多租户隔离策略接口。
 *
 * <p>每种隔离模式对应一个策略实现，在 SQL 执行前完成预处理
 * （如切换数据源、设置表后缀、切换 Schema 等）。</p>
 *
 * @author richie696
 * @since 2.0
 */
public interface TenancyStrategy {

    /**
     * 声明该策略是否支持指定的隔离模式。
     *
     * @param mode 隔离模式
     * @return 支持返回 {@code true}
     */
    boolean supports(IsolationMode mode);

    /**
     * SQL 执行前的预处理。
     *
     * @param invocation MyBatis 调用上下文
     * @param tenantInfo 当前租户的运行时信息
     */
    void beforeSqlExecute(Invocation invocation, TenantInfo tenantInfo);
}
