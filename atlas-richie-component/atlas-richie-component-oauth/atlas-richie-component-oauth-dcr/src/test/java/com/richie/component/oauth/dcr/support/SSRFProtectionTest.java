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
import com.richie.component.cache.ops.ValueOps;
import com.richie.component.oauth.core.config.OAuth2RedisKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SSRFProtection 测试")
class SSRFProtectionTest {

    @Mock
    private GlobalCache globalCache;
    @Mock
    private ValueOps valueOps;

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为 null")
    void isUrlSafe_withNullUrl_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            boolean result = ssrfProtection.isUrlSafe(null);

            assertThat(result).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为空白")
    void isUrlSafe_withBlankUrl_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            assertThat(ssrfProtection.isUrlSafe("")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("   ")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("\t")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为 HTTP（仅允许 HTTPS）")
    void isUrlSafe_withHttpUrl_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            boolean result = ssrfProtection.isUrlSafe("http://example.com");

            assertThat(result).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为 IP 地址")
    void isUrlSafe_withIpAddress_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            assertThat(ssrfProtection.isUrlSafe("https://192.168.1.1")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://10.0.0.1")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://172.16.0.1")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://8.8.8.8")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为 localhost")
    void isUrlSafe_withLocalhost_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            assertThat(ssrfProtection.isUrlSafe("https://localhost")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://LOCALHOST")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为 127.0.0.1")
    void isUrlSafe_with127001_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            assertThat(ssrfProtection.isUrlSafe("https://127.0.0.1")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为私有 IP（10.x.x.x）")
    void isUrlSafe_withPrivateIp10Range_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            assertThat(ssrfProtection.isUrlSafe("https://10.0.0.1")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://10.255.255.255")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://10.1.1.1")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为私有 IP（192.168.x.x）")
    void isUrlSafe_withPrivateIp192168Range_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            assertThat(ssrfProtection.isUrlSafe("https://192.168.0.1")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://192.168.255.255")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://192.168.1.100")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为私有 IP（172.16-31.x.x）")
    void isUrlSafe_withPrivateIp172Range_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            assertThat(ssrfProtection.isUrlSafe("https://172.16.0.1")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://172.31.255.255")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://172.20.0.1")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为 IPv6 环回地址 ::1")
    void isUrlSafe_withIpv6Loopback_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            assertThat(ssrfProtection.isUrlSafe("https://[::1]")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为 IPv6 链路本地地址 fe80::")
    void isUrlSafe_withIpv6LinkLocal_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            assertThat(ssrfProtection.isUrlSafe("https://[fe80::1]")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为保留 IPv6 地址")
    void isUrlSafe_withReservedIpv6Address_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            assertThat(ssrfProtection.isUrlSafe("https://[::]")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://[::ffff:127.0.0.1]")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://[2001:db8::1]")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://[fc00::1]")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://[fd00::1]")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 true 当 URL 为有效的 HTTPS 公网域名")
    void isUrlSafe_withValidHttpsUrl_returnsTrue() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("example.com"), String.class))
                    .thenReturn(null);
            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("www.example.com"), String.class))
                    .thenReturn("93.184.216.34");

            assertThat(ssrfProtection.isUrlSafe("https://example.com")).isTrue();
            assertThat(ssrfProtection.isUrlSafe("https://www.example.com")).isTrue();
        }
    }

    @Test
    @DisplayName("isUrlSafe 尊重域名白名单")
    void isUrlSafe_withWhitelistedDomain_returnsTrue() {
        List<String> allowedDomains = List.of("trusted.example.com", "api.partner.com");

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, allowedDomains, 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("trusted.example.com"), String.class))
                    .thenReturn("93.184.216.34");
            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("api.partner.com"), String.class))
                    .thenReturn("93.184.216.35");
            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("sub.trusted.example.com"), String.class))
                    .thenReturn("93.184.216.36");

            assertThat(ssrfProtection.isUrlSafe("https://trusted.example.com")).isTrue();
            assertThat(ssrfProtection.isUrlSafe("https://api.partner.com")).isTrue();
            assertThat(ssrfProtection.isUrlSafe("https://sub.trusted.example.com")).isTrue();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当域名不在白名单")
    void isUrlSafe_withNonWhitelistedDomain_returnsFalse() {
        List<String> allowedDomains = List.of("trusted.example.com");

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, allowedDomains, 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("untrusted.example.com"), String.class))
                    .thenReturn(null);

            assertThat(ssrfProtection.isUrlSafe("https://untrusted.example.com")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 使用缓存的 DNS 解析结果")
    void isUrlSafe_usesCachedDns() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("cached.example.com"), String.class))
                    .thenReturn("93.184.216.34");

            assertThat(ssrfProtection.isUrlSafe("https://cached.example.com")).isTrue();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 DNS 解析失败")
    void isUrlSafe_whenDnsResolutionFails_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("nonexistent.invalid"), String.class))
                    .thenReturn(null);

            assertThat(ssrfProtection.isUrlSafe("https://nonexistent.invalid")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当解析后为内网 IP")
    void isUrlSafe_whenResolvedToInternalIp_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("internal.evil.com"), String.class))
                    .thenReturn("192.168.1.100");

            assertThat(ssrfProtection.isUrlSafe("https://internal.evil.com")).isFalse();
        }
    }

    @Test
    @DisplayName("构造函数处理 null allowedDomains")
    void constructor_withNullAllowedDomains_handlesGracefully() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, null, 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("example.com"), String.class))
                    .thenReturn(null);

            assertThat(ssrfProtection.isUrlSafe("https://example.com")).isTrue();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 格式错误（MalformedURLException）")
    void isUrlSafe_withMalformedUrl_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            assertThat(ssrfProtection.isUrlSafe("not-a-valid-url")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为链路本地 IP（169.254.x.x）")
    void isUrlSafe_withLinkLocalIpv4_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            assertThat(ssrfProtection.isUrlSafe("https://169.254.1.1")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://169.254.255.254")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为零地址（0.0.0.0）")
    void isUrlSafe_withZeroIpv4_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            assertThat(ssrfProtection.isUrlSafe("https://0.0.0.0")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为 CGNAT 地址（100.64-127.x.x）")
    void isUrlSafe_withCGNATIpv4_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            assertThat(ssrfProtection.isUrlSafe("https://100.64.0.1")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://100.127.255.255")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为 192.0.0.x/24 保留地址")
    void isUrlSafe_with192_0_0_0_Ipv4_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            assertThat(ssrfProtection.isUrlSafe("https://192.0.0.1")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://192.0.0.255")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为 TEST-NET-1 地址（192.0.2.x）")
    void isUrlSafe_with192_0_2_0_Ipv4_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            assertThat(ssrfProtection.isUrlSafe("https://192.0.2.1")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://192.0.2.255")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 URL 为组播 IP（224.x.x.x 及以上）")
    void isUrlSafe_withMulticastIpv4_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            assertThat(ssrfProtection.isUrlSafe("https://224.0.0.1")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://240.0.0.1")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://255.255.255.255")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 true 当 DNS 解析到公网 IP（8.8.8.8）")
    void isUrlSafe_withPublicIpv4_allowed() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("public-dns.google"), String.class))
                    .thenReturn("8.8.8.8");

            assertThat(ssrfProtection.isUrlSafe("https://public-dns.google")).isTrue();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 DNS 解析到链路本地 IP（169.254.x.x）")
    void isUrlSafe_whenResolvedToLinkLocal169_254_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("internal-linklocal.com"), String.class))
                    .thenReturn("169.254.1.1");

            assertThat(ssrfProtection.isUrlSafe("https://internal-linklocal.com")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 DNS 解析到 CGNAT 地址")
    void isUrlSafe_whenResolvedToCGNAT100_64_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("cgnat-domain.com"), String.class))
                    .thenReturn("100.64.0.1");

            assertThat(ssrfProtection.isUrlSafe("https://cgnat-domain.com")).isFalse();

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("cgnat-boundary.com"), String.class))
                    .thenReturn("100.127.0.1");

            assertThat(ssrfProtection.isUrlSafe("https://cgnat-boundary.com")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 DNS 解析到 192.0.0.x 保留地址")
    void isUrlSafe_whenResolvedTo192_0_0_1_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("testnet192.com"), String.class))
                    .thenReturn("192.0.0.1");

            assertThat(ssrfProtection.isUrlSafe("https://testnet192.com")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 DNS 解析到 TEST-NET-1 地址（192.0.2.x）")
    void isUrlSafe_whenResolvedTo192_0_2_1_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("testnet1.com"), String.class))
                    .thenReturn("192.0.2.1");

            assertThat(ssrfProtection.isUrlSafe("https://testnet1.com")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 DNS 解析到组播 IP")
    void isUrlSafe_whenResolvedToMulticast224_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("multicast.com"), String.class))
                    .thenReturn("224.0.0.1");

            assertThat(ssrfProtection.isUrlSafe("https://multicast.com")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 true 当 DNS 解析到公网 IPv6 地址")
    void isUrlSafe_withPublicIpv6_allowed() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("public-ipv6.example"), String.class))
                    .thenReturn("2001:1::1");

            assertThat(ssrfProtection.isUrlSafe("https://public-ipv6.example")).isTrue();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 DNS 解析到 IPv6 链路本地地址")
    void isUrlSafe_whenResolvedToIpv6LinkLocal_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("ipv6-linklocal.com"), String.class))
                    .thenReturn("fe80::1");

            assertThat(ssrfProtection.isUrlSafe("https://ipv6-linklocal.com")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 DNS 解析到 IPv6 环回地址")
    void isUrlSafe_whenResolvedToIpv6Loopback_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("ipv6-loopback.com"), String.class))
                    .thenReturn("::1");

            assertThat(ssrfProtection.isUrlSafe("https://ipv6-loopback.com")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 DNS 解析到 IPv6 唯一本地地址")
    void isUrlSafe_whenResolvedToIpv6UniqueLocal_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("ipv6-ula-fc.com"), String.class))
                    .thenReturn("fc00::1");
            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("ipv6-ula-fd.com"), String.class))
                    .thenReturn("fd00::1");

            assertThat(ssrfProtection.isUrlSafe("https://ipv6-ula-fc.com")).isFalse();
            assertThat(ssrfProtection.isUrlSafe("https://ipv6-ula-fd.com")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 DNS 解析到 IPv6 文档地址")
    void isUrlSafe_whenResolvedToIpv6Documentation_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("ipv6-doc.com"), String.class))
                    .thenReturn("2001:db8::1");

            assertThat(ssrfProtection.isUrlSafe("https://ipv6-doc.com")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 DNS 解析到 IPv6 100:: 地址段")
    void isUrlSafe_whenResolvedToIpv6_100_ReturnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("ipv6-100.com"), String.class))
                    .thenReturn("100::1");

            assertThat(ssrfProtection.isUrlSafe("https://ipv6-100.com")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 DNS 解析到 IPv6 2001:10:: 地址段")
    void isUrlSafe_whenResolvedToIpv6_2001_10_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("ipv6-2001-10.com"), String.class))
                    .thenReturn("2001:10::1");

            assertThat(ssrfProtection.isUrlSafe("https://ipv6-2001-10.com")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 false 当 DNS 解析到 IPv6 广播地址")
    void isUrlSafe_whenResolvedToIpv6Broadcast_returnsFalse() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("ipv6-broadcast.com"), String.class))
                    .thenReturn("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff");

            assertThat(ssrfProtection.isUrlSafe("https://ipv6-broadcast.com")).isFalse();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 true 当 DNS 解析到 CGNAT 范围外但 first=100 的公网 IP")
    void isUrlSafe_whenResolvedTo100_63_255_isPublic_returnsTrue() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("public-100-63.com"), String.class))
                    .thenReturn("100.63.0.1");

            assertThat(ssrfProtection.isUrlSafe("https://public-100-63.com")).isTrue();
        }
    }

    @Test
    @DisplayName("isUrlSafe 返回 true 当 DNS 解析到 192.0.1.x 公网 IP")
    void isUrlSafe_whenResolvedTo192_0_1_x_isPublic_returnsTrue() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            SSRFProtection ssrfProtection = new SSRFProtection(globalCache, List.of(), 3600L);

            when(valueOps.get(OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey("public-192-0-1.com"), String.class))
                    .thenReturn("192.0.1.1");

            assertThat(ssrfProtection.isUrlSafe("https://public-192-0-1.com")).isTrue();
        }
    }
}
