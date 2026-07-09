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
package com.richie.component.oauth.authz.support;

import com.richie.component.cache.GlobalCache;
import com.richie.component.oauth.core.config.OAuth2RedisKey;
import com.richie.component.oauth.authz.spi.AuthorizationCodeStore;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis 实现的 AuthorizationCodeStore
 * <p>
 * 使用 GlobalCache（Redis）存储授权码数据。
 *
 * @author richie696
 * @since 2026-06-12
 */
@Slf4j
public class DefaultAuthorizationCodeStore implements AuthorizationCodeStore {

    private static final long DEFAULT_TTL_SECONDS = 600;

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
        String key = OAuth2RedisKey.OAUTH2_AUTHZ_CODE.getKey(code);
        Map<String, Object> data = Map.of(
                "clientId", clientId,
                "redirectUri", redirectUri,
                "codeChallenge", codeChallenge != null ? codeChallenge : "",
                "codeChallengeMethod", codeChallengeMethod != null ? codeChallengeMethod : "",
                "scopes", String.join(" ", scopes != null ? scopes : Collections.emptyList()),
                "userId", userId != null ? userId : "",
                "createdAt", String.valueOf(System.currentTimeMillis())
        );
        long ttl = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
        GlobalCache.struct().set(key, data, TimeUnit.SECONDS.toMillis(ttl));
        log.debug("存储授权码: code={}, clientId={}, ttl={}s", code, clientId, ttl);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> loadAuthorizationCode(String code) {
        String key = OAuth2RedisKey.OAUTH2_AUTHZ_CODE.getKey(code);
        return GlobalCache.field().getAll(key, String.class);
    }

    @Override
    public void consumeAuthorizationCode(String code) {
        String key = OAuth2RedisKey.OAUTH2_AUTHZ_CODE.getKey(code);
        GlobalCache.key().removeCache(key);
        log.debug("消费授权码: code={}", code);
    }
}
