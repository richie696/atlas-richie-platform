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
package cn.richie696.component.oauth.authz.support;

import cn.richie696.component.oauth.cache.LegacyGlobalCacheOAuthCache;
import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.cache.OAuthLock;
import cn.richie696.component.oauth.authz.spi.AuthorizationCodeStore;
import cn.richie696.component.oauth.core.config.OAuth2RedisKey;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的 {@link AuthorizationCodeStore} 默认实现。
 * <p>
 * 通过 {@link OAuthCache} 间接访问 atlas-richie-component-cache,把授权码及绑定信息
 * (clientId/redirectUri/PKCE challenge/scopes/userId/resource/nonce)写到 Redis,所有 Key 前缀走
 * {@link cn.richie696.component.oauth.core.config.OAuth2RedisKey#OAUTH2_AUTHZ_CODE};消费动作在
 * {@link OAuthLock} 分布式锁内完成"读 + 删",保证一次性语义,杜绝并发兑换。
 * </p>
 * <p>
 * 处于 oauth-authz 的默认存储实现位置:由
 * {@link cn.richie696.component.oauth.authz.config.OAuth2AuthzAutoConfiguration} 在缺省 Bean 时
 * 注册,被 {@link cn.richie696.component.oauth.authz.AuthorizationEndpoint} 写入、被 {@link cn.richie696.component.oauth.authz.AuthorizationCodeGrant} 消费。
 * </p>
 * <p>
 * 解决的问题:让授权码的"短期状态 + 一次性消费"两个安全敏感点有一个开箱即用的 Redis 实现,业务方
 * 替换存储后端时无需重写并发控制;同时把 Key 命名收敛到 OAuth2RedisKey 防止散落。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
@Slf4j
public class DefaultAuthorizationCodeStore implements AuthorizationCodeStore {

    private static final long DEFAULT_TTL_SECONDS = 600;
    private final OAuthCache cache;

    public DefaultAuthorizationCodeStore() {
        this(new LegacyGlobalCacheOAuthCache());
    }

    public DefaultAuthorizationCodeStore(OAuthCache cache) {
        this.cache = cache;
    }

    @Override
    public void storeAuthorizationCode(
            String code,
            String clientId,
            String redirectUri,
            String codeChallenge,
            String codeChallengeMethod,
            List<String> scopes,
            String userId,
            long ttlSeconds
    ) {
        storeAuthorizationCode(code, clientId, redirectUri, codeChallenge, codeChallengeMethod,
                scopes, userId, null, ttlSeconds);
    }

    @Override
    public void storeAuthorizationCode(
            String code,
            String clientId,
            String redirectUri,
            String codeChallenge,
            String codeChallengeMethod,
            List<String> scopes,
            String userId,
            String nonce,
            long ttlSeconds
    ) {
        storeAuthorizationCode(code, clientId, redirectUri, codeChallenge, codeChallengeMethod,
                scopes, userId, null, nonce, ttlSeconds);
    }

    @Override
    public void storeAuthorizationCode(
            String code,
            String clientId,
            String redirectUri,
            String codeChallenge,
            String codeChallengeMethod,
            List<String> scopes,
            String userId,
            String resource,
            String nonce,
            long ttlSeconds
    ) {
        String key = OAuth2RedisKey.OAUTH2_AUTHZ_CODE.getKey(code);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("clientId", clientId);
        data.put("redirectUri", redirectUri);
        data.put("codeChallenge", codeChallenge != null ? codeChallenge : "");
        data.put("codeChallengeMethod", codeChallengeMethod != null ? codeChallengeMethod : "");
        data.put("scopes", String.join(" ", scopes != null ? scopes : Collections.emptyList()));
        data.put("userId", userId != null ? userId : "");
        data.put("resource", resource != null ? resource : "");
        data.put("nonce", nonce != null ? nonce : "");
        data.put("createdAt", String.valueOf(System.currentTimeMillis()));
        long ttl = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
        cache.put(key, data, TimeUnit.SECONDS.toMillis(ttl));
        log.debug("存储授权码: code={}, clientId={}, ttl={}s", code, clientId, ttl);
    }

    @Override
    public Map<String, String> loadAuthorizationCode(String code) {
        String key = OAuth2RedisKey.OAUTH2_AUTHZ_CODE.getKey(code);
        Map<?, ?> stored = cache.get(key, Map.class);
        if (stored == null) {
            return null;
        }
        Map<String, String> result = new java.util.LinkedHashMap<>();
        stored.forEach((field, value) -> result.put(String.valueOf(field), value == null ? null : String.valueOf(value)));
        return result;
    }

    /** 在 OAuthCache 分布式锁内完成读取和删除，避免授权码并发兑换。 */
    @Override
    public AuthorizationCodeConsumeResult consume(String code) {
        String key = OAuth2RedisKey.OAUTH2_AUTHZ_CODE.getKey(code);
        try (OAuthLock lock = cache.tryLock(key + ":consume", 5L)) {
            if (!lock.acquired()) {
                return AuthorizationCodeConsumeResult.notFound();
            }
            Map<String, String> data = loadAuthorizationCode(code);
            if (data == null || data.isEmpty()) {
                return AuthorizationCodeConsumeResult.notFound();
            }
            cache.remove(key);
            return AuthorizationCodeConsumeResult.consumed(data);
        }
    }

    @Override
    public void consumeAuthorizationCode(String code) {
        String key = OAuth2RedisKey.OAUTH2_AUTHZ_CODE.getKey(code);
        cache.remove(key);
        log.debug("消费授权码: code={}", code);
    }
}
