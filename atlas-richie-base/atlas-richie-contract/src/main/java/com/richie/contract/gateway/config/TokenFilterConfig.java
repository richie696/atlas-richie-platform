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
package com.richie.contract.gateway.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 令牌过滤器配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 17:30:25
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "platform.gateway.contract.token")
public class TokenFilterConfig {

    /** 默认构造函数，供配置绑定使用。 */
    public TokenFilterConfig() {
    }

    /**
     * 是否启用令牌过滤器（默认：true）
     */
    private boolean enable = true;

    /**
     * 令牌有效期（单位：小时，默认：1小时）
     */
    private int tokenValidDuration = 1;

    /**
     * 到期前多久执行续期（单位：分钟，默认：10分钟）
     */
    private int expirationRenewalTime = 10;

    /**
     * JWT令牌的秘钥
     */
    private String secret;

    /**
     * JWT黑名单令牌的缓存路径
     */
    private String blacklistPath = "platform:gateway:token:";

    /**
     * 忽略接口
     */
    private List<String> ignoreUriList = new ArrayList<>(10);

    /**
     * 登录页URI列表
     * <p>
     * 所有需要执行令牌签发的登录页路径（支持正则匹配）
     */
    private List<String> loginUriList = new ArrayList<>(4);

    /**
     * 启用MFA的登录页URI列表
     * <p>
     * 只有在此列表中的登录页才会进行MFA验证检查。
     * <p>
     * 如果为空，则所有登录页都不启用MFA（即使MFA组件已启用）。
     * <p>
     * 支持正则匹配，例如：
     * <ul>
     *     <li>{@code /gateway/login}：精确匹配</li>
     *     <li>{@code /api/auth/.*}：正则匹配所有以 /api/auth/ 开头的路径</li>
     * </ul>
     * <p>
     * 典型使用场景：
     * <ul>
     *     <li>多个业务系统共用网关，但只有部分系统需要MFA</li>
     *     <li>管理后台登录需要MFA，普通用户登录不需要MFA</li>
     * </ul>
     */
    private List<String> mfaEnabledLoginUriList = new ArrayList<>(4);

    /**
     * 获取过期时间毫秒数的方法
     *
     * @return 返回过期时间毫秒数
     */
    @JsonIgnore
    public long getExpireTimeMillis() {
        return TimeUnit.HOURS.toMillis(tokenValidDuration);
    }
}
