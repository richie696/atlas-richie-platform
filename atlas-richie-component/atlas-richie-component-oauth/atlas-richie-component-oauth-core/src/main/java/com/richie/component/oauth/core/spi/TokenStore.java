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
package com.richie.component.oauth.core.spi;

import com.richie.component.oauth.core.model.ClientConfig;

import java.util.Map;

/**
 * Token 存储抽象
 * <p>
 * 定义 refresh_token 存储、access_token 黑名单、IP 绑定等持久化契约。
 * 默认使用 Redis 实现，可通过 SPI 替换为 JDBC 等实现。
 *
 * @author richie696
 * @since 2026-06-12
 */
public interface TokenStore {

    void storeRefreshToken(String refreshToken, String clientId, String ip, ClientConfig config);

    Map<String, String> loadRefreshToken(String refreshToken);

    void removeRefreshToken(String refreshToken);

    void addToBlacklist(String accessToken, long ttlMillis);

    boolean isBlacklisted(String accessToken);

    void bindAccessTokenIp(String accessToken, String clientId, String ip, long ttlMillis);

    void removeAccessTokenIpBinding(String accessToken);

    void storeClientRefreshTokenIndex(String clientId, String refreshToken, long ttlMillis);

    String getClientRefreshTokenIndex(String clientId);

    void removeClientRefreshTokenIndex(String clientId);

    long incrementDailyIssueCount(String clientId, String date, long ttlMillis);

    long incrementAnomalyRefreshCount(String clientId);

    long incrementAnomalyRateLimit(String clientId);
}
