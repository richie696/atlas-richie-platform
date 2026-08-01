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
package cn.richie696.context.utils.spring;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Gateway 与业务服务之间的内部租户身份断言工具。
 *
 * <p>格式为 {@code v1.tenantId.expiresAtEpochMillis.signature}。断言只承载
 * 租户 ID 和短期过期时间，不替代用户 JWT；下游服务仍需执行租户存在性、状态和
 * 数据隔离校验。</p>
 */
public final class TenantIdentityAssertionUtils {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String VERSION = "v1";

    private TenantIdentityAssertionUtils() {
    }

    /**
     * 创建内部租户身份断言。
     */
    public static String create(Long tenantId, long expiresAtEpochMillis, String secret) {
        if (tenantId == null || tenantId <= 0 || expiresAtEpochMillis <= 0 || secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("tenantId, expiry and assertion secret are required");
        }
        String payload = VERSION + "." + tenantId + "." + expiresAtEpochMillis;
        return payload + "." + sign(payload, secret);
    }

    /**
     * 验证断言并返回租户 ID；格式、签名或有效期任一不满足时返回 null。
     */
    public static Long verify(String assertion, String secret, long nowEpochMillis) {
        if (assertion == null || assertion.isBlank() || secret == null || secret.isBlank()) {
            return null;
        }
        String[] parts = assertion.split("\\.", -1);
        if (parts.length != 4 || !VERSION.equals(parts[0])) {
            return null;
        }
        try {
            long tenantId = Long.parseLong(parts[1]);
            long expiresAt = Long.parseLong(parts[2]);
            if (tenantId <= 0 || expiresAt <= nowEpochMillis) {
                return null;
            }
            String expected = sign(parts[0] + "." + parts[1] + "." + parts[2], secret);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    parts[3].getBytes(StandardCharsets.US_ASCII))) {
                return null;
            }
            return tenantId;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create tenant identity assertion", ex);
        }
    }
}
