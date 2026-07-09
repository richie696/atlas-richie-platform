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
 * 租户迁移中异常。
 *
 * <p>当租户处于 {@code MIGRATING} 状态时抛出，表示租户正在进行数据迁移（模式切换），
 * 暂时拒绝访问。HTTP 状态码应为 503。</p>
 *
 * @author richie696
 * @since 2.0
 */
@Getter
public class TenantMigratingException extends RuntimeException {

    private final Long tenantId;

    public TenantMigratingException(Long tenantId) {
        super("Tenant " + tenantId + " is currently migrating, please try again later");
        this.tenantId = tenantId;
    }

}
