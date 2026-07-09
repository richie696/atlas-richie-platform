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
 * 租户未找到异常。
 *
 * <p>当 {@code TenantInfoProvider.getTenantInfo(tenantId)} 返回 {@code null} 时抛出，
 * 表示租户在 {@code sys_tenant} 表中不存在或未被注册。</p>
 *
 * @author richie696
 * @since 2.0
 */
@Getter
public class TenantNotFoundException extends RuntimeException {

    private final Long tenantId;

    public TenantNotFoundException(Long tenantId) {
        super("Tenant not found: " + tenantId);
        this.tenantId = tenantId;
    }

    public TenantNotFoundException(String message) {
        super(message);
        this.tenantId = null;
    }

}
