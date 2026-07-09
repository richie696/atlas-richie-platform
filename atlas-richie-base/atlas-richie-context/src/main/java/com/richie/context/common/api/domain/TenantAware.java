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
package com.richie.context.common.api.domain;

/**
 * 租户感知接口
 *
 * <p>实现此接口表示实体支持多租户数据隔离。
 * MyBatis-Plus 多租户插件通过此接口识别需要添加租户条件过滤的实体。</p>
 *
 * @author richie696
 * @since 1.0
 */
public interface TenantAware {

    /**
     * 获取租户 ID
     */
    Long getTenantId();

    /**
     * 设置租户 ID
     */
    void setTenantId(Long tenantId);

}
