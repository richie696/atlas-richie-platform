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
package cn.richie696.component.oauth.dcr.support;

import cn.richie696.component.cache.GlobalCache;
import cn.richie696.component.oauth.cache.LegacyGlobalCacheOAuthCache;
import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.core.config.OAuth2RedisKey;
import cn.richie696.component.oauth.dcr.model.ClientIdMetadataDocument;
import cn.richie696.component.oauth.dcr.spi.ClientIdMetadataDocumentResolver;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ClientIdMetadataDocumentResolver} 的 Redis 默认实现。
 * <p>
 * 从 {@code OAUTH2_CLIENT_META} Key 直接读取已注册的元数据文档;若调用方同时传入外部 metadataUri,
 * 在返回文档前由 {@link SSRFProtection} 校验该 URI 是否安全(只校验、不发起实际请求)。
 * </p>
 * <p>
 * 处于 oauth-dcr 的默认解析器位置:由
 * {@link cn.richie696.component.oauth.dcr.config.OAuth2DCRAutoConfiguration} 在缺省 Bean 时
 * 注册,被 {@link cn.richie696.component.oauth.dcr.DynamicClientRegistrationEndpoint} 在更新与读取客户端元数据时调用;同时兼容
 * 旧版 {@code LegacyGlobalCacheOAuthCache} 与新版 {@link OAuthCache}。
 * </p>
 * <p>
 * 解决的问题:把"如何按 clientId 拿到元数据文档"封装为 SPI 默认实现,同时在解析路径上把 SSRF 校验
 * 串起来,避免业务方自己写解析器时漏掉安全检查。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
@Slf4j
public class DefaultClientIdMetadataDocumentResolver implements ClientIdMetadataDocumentResolver {

    private final OAuthCache cache;
    private final SSRFProtection ssrfProtection;

    public DefaultClientIdMetadataDocumentResolver(GlobalCache globalCache, SSRFProtection ssrfProtection) {
        this(new LegacyGlobalCacheOAuthCache(), ssrfProtection);
    }

    public DefaultClientIdMetadataDocumentResolver(OAuthCache cache, SSRFProtection ssrfProtection) {
        this.cache = cache;
        this.ssrfProtection = ssrfProtection;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ClientIdMetadataDocument resolve(String clientId, String metadataUri) {
        if (clientId == null) {
            return null;
        }

        String redisKey = OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(clientId);
        ClientIdMetadataDocument document = cache.get(redisKey, ClientIdMetadataDocument.class);

        if (document != null && metadataUri != null && ssrfProtection != null) {
            if (!ssrfProtection.isUrlSafe(metadataUri)) {
                log.warn("Client metadata URI 不安全: clientId={}, metadataUri={}", clientId, metadataUri);
            }
        }

        return document;
    }

    @Override
    public String getMetadataUri(String clientId) {
        if (clientId == null) {
            return null;
        }

        String redisKey = OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(clientId);
        if (cache instanceof LegacyGlobalCacheOAuthCache legacy) {
            return legacy.getField(redisKey, "metadataUri", String.class);
        }
        return cache.get(redisKey + ":metadataUri", String.class);
    }
}
