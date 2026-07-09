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
package com.richie.contract.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 基础用户信息VO父类（用于签名，所有子系统的UserInfo都需要继承）
 *
 * @author richie696
 * @version 1.0
 * @since 2024-02-22 22:02:58
 */
@Data
@Accessors(chain = true)
public class LoginUserPrincipal implements Serializable {

    /**
     * 用户名
     */
    protected String username;

    /**
     * 其他需要签名的参数
     */
    private Map<String, String> signParams = new HashMap<>();

    /**
     * 租户功能是否启用（默认false）
     * <p>当 {@code atlas-richie-component-tenant} 在 classpath 上时，
     * JwtUtils 在生成令牌时自动将此字段与 {@link TenantFeature#isEnabled()} 合并后写入 JWT claims。
     * 调用方也可在构建子类后显式设置此字段来覆盖默认行为。</p>
     */
    private boolean tenantEnabled = false;

    /**
     * 默认构造方法
     */
    public LoginUserPrincipal() {
    }

    /**
     * 添加签名参数
     * @param key   签名参数key
     * @param value 签名参数value
     */
    public void addParam(String key, String value) {
        signParams.put(key, value);
    }

    /**
     * 清空签名参数
     */
    public void clearParams() {
        signParams.clear();
    }
}
