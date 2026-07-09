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
 * 租户模式迁移拒绝异常。
 *
 * <p>当尝试对租户执行隔离模式变更（如从 COLUMN 切换到 DATABASE），
 * 但该操作未被授权或当前不允许时抛出。</p>
 *
 * @author richie696
 * @since 2.0
 */
@Getter
public class TenantModeMigrationException extends RuntimeException {

    private final Long tenantId;

    public TenantModeMigrationException(Long tenantId, String message) {
        super(message);
        this.tenantId = tenantId;
    }

}
