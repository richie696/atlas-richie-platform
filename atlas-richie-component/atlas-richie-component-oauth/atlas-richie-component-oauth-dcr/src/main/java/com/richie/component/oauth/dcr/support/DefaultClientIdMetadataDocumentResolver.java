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
package com.richie.component.oauth.dcr.support;

import com.richie.component.cache.GlobalCache;
import com.richie.component.oauth.core.config.OAuth2RedisKey;
import com.richie.component.oauth.dcr.model.ClientIdMetadataDocument;
import com.richie.component.oauth.dcr.spi.ClientIdMetadataDocumentResolver;
import lombok.extern.slf4j.Slf4j;

/**
 * Client ID Metadata Document 解析器默认实现
 * <p>
 * 从 Redis 读取客户端元数据。
 *
 * @author richie696
 * @since 2026-06-12
 */
@Slf4j
public class DefaultClientIdMetadataDocumentResolver implements ClientIdMetadataDocumentResolver {

    private final GlobalCache globalCache;
    private final SSRFProtection ssrfProtection;

    public DefaultClientIdMetadataDocumentResolver(GlobalCache globalCache, SSRFProtection ssrfProtection) {
        this.globalCache = globalCache;
        this.ssrfProtection = ssrfProtection;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ClientIdMetadataDocument resolve(String clientId, String metadataUri) {
        if (clientId == null) {
            return null;
        }

        String redisKey = OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(clientId);
        ClientIdMetadataDocument document = globalCache.struct().get(redisKey, ClientIdMetadataDocument.class);

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
        String metadataUri = globalCache.field().get(redisKey, "metadataUri", String.class);
        return metadataUri;
    }
}
