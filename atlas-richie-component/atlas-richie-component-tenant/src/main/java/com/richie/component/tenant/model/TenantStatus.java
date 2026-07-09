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
package com.richie.component.tenant.model;

/**
 * 租户状态枚举。
 *
 * <p>标识租户在生命周期中的当前状态，控制租户是否可被路由访问。</p>
 *
 * @author richie696
 * @since 2.0
 */
public enum TenantStatus {

    /**
     * 活跃状态：租户可正常访问。
     */
    ACTIVE,

    /**
     * 停用状态：租户被管理员停用，拒绝访问。
     */
    INACTIVE,

    /**
     * 迁移中：租户正在进行数据迁移（模式切换），暂时拒绝访问（503）。
     */
    MIGRATING,

    /**
     * 初始化中：租户正在创建/分配资源，尚未就绪。
     */
    PROVISIONING,

    /**
     * 已过期：租户账户过期（expiredTime &lt; 当前时间），拒绝访问。
     */
    EXPIRED
}
