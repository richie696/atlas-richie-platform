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
package com.richie.context.common.api.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 多租户审计领域基类
 *
 * <p>在 {@link AuditDomain} 基础上增加了租户 ID 字段 {@code tenantId}。
 * 适用于同时需要多租户数据隔离和审计追踪的业务表。</p>
 *
 * @author richie696
 * @since 1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class TenantAuditDomain extends AuditDomain implements TenantAware {

    /**
     * 租户 ID
     */
    protected Long tenantId;

    @Override
    public Long getTenantId() {
        return tenantId;
    }

    @Override
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

}
