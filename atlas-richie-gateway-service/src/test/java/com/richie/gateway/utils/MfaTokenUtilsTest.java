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
package com.richie.gateway.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.richie.contract.gateway.config.TokenFilterConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * MfaTokenUtils 测试类
 * <p>
 * 覆盖 MfaTokenUtils 的核心行为：
 * <ul>
 *   <li>generateMfaToken：JWT 生成、密钥未配置异常、tenantId 条件注入</li>
 *   <li>getUserIdFromMfaToken / getTenantIdFromMfaToken / getUsernameFromMfaToken：空值与正常解析</li>
 *   <li>isValidMfaToken：空值、签名失败、已过期、type 不匹配、合法 token 各类场景</li>
 * </ul>
 * <p>
 * JwtUtils 是集成型工具（涉及 JWT decode/verify），这里用真实 JWT 而非静态 mock，
 * 以确保 MfaTokenUtils 调用的 JwtUtils.getArgument/getUsername/verify/getExpiredTime 路径全部真实经过。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MfaTokenUtilsTest {

    private static final String SECRET = "test-secret-123";

    @Mock
    private com.richie.gateway.config.GatewayConfig gatewayConfig;

    /**
     * 构建一个已配置 secret 的 TokenFilterConfig 并挂到 gatewayConfig 上。
     * HMAC256 要求密钥长度 >= 32 bytes，"test-secret-123" 正好 16 bytes，
     * auth0 的 JWT 库要求至少 32 bytes，所以此处用更长的密钥。
     */
    private void setupValidSecret() {
        TokenFilterConfig tokenCfg = new TokenFilterConfig();
        tokenCfg.setSecret(SECRET + "0123456789012345678901234567"); // 43 bytes > 32
        when(gatewayConfig.getToken()).thenReturn(tokenCfg);
    }

    private void setupBlankSecret() {
        TokenFilterConfig tokenCfg = new TokenFilterConfig();
        tokenCfg.setSecret("   ");
        when(gatewayConfig.getToken()).thenReturn(tokenCfg);
    }

    /**
     * 生成一个真实的 MFA_TOKEN JWT，供解析类测试使用。
     * 手动拼 JWT 是为了绕过 MfaTokenUtils 自身，单独验证解析路径。
     */
    private String generateTestMfaToken(String userId, String tenantId, String username, long expiresAtMs) {
        // HMAC256 要求密钥至少 32 bytes
        String realSecret = SECRET + "0123456789012345678901234567";
        Algorithm algorithm = Algorithm.HMAC256(realSecret);
        var builder = JWT.create()
                .withSubject(username)
                .withExpiresAt(new Date(expiresAtMs))
                .withClaim("type", "MFA_TOKEN")
                .withClaim("userId", userId)
                .withClaim("username", username);
        if (tenantId != null && !tenantId.isBlank()) {
            builder.withClaim("tenantId", tenantId);
        }
        return builder.sign(algorithm);
    }

    @Nested
    @DisplayName("generateMfaToken 生成 MFA Token")
    class GenerateMfaTokenTests {

        @Test
        @DisplayName("返回有效 JWT，包含 userId、username、type=MFA_TOKEN 声明")
        void generateMfaToken_returnsValidJwt_withCorrectClaims() {
            setupValidSecret();
            MfaTokenUtils utils = new MfaTokenUtils(gatewayConfig);

            String token = utils.generateMfaToken("u-999", "tenant-1", "alice");

            // 验证是合法 JWT 格式（三段 base64 点分）
            assertThat(token).matches("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");

            // 验证解析出正确声明（走 JwtUtils.getArgument 路径）
            assertThat(com.richie.context.utils.spring.JwtUtils.getArgument(token, "userId")).isEqualTo("u-999");
            assertThat(com.richie.context.utils.spring.JwtUtils.getArgument(token, "username")).isEqualTo("alice");
            assertThat(com.richie.context.utils.spring.JwtUtils.getArgument(token, "type")).isEqualTo("MFA_TOKEN");
        }

        @Test
        @DisplayName("tenantId 非空时写入 JWT，为空时 JWT 中无 tenantId 声明")
        void generateMfaToken_tenantIdConditional() {
            setupValidSecret();
            MfaTokenUtils utils = new MfaTokenUtils(gatewayConfig);

            // 有 tenantId
            String withTenant = utils.generateMfaToken("u-1", "tenant-abc", "bob");
            assertThat(com.richie.context.utils.spring.JwtUtils.getArgument(withTenant, "tenantId")).isEqualTo("tenant-abc");

            // 无 tenantId（blank）
            String withoutTenant = utils.generateMfaToken("u-2", "", "charlie");
            assertThat(com.richie.context.utils.spring.JwtUtils.getArgument(withoutTenant, "tenantId")).isNull();
        }

        @Test
        @DisplayName("gatewayConfig.getToken().getSecret() 为 blank 时抛出 RuntimeException")
        void generateMfaToken_blankSecret_throwsRuntimeException() {
            setupBlankSecret();
            MfaTokenUtils utils = new MfaTokenUtils(gatewayConfig);

            assertThatThrownBy(() -> utils.generateMfaToken("u-1", null, "alice"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Token 密钥未配置");
        }
    }

    @Nested
    @DisplayName("getUserIdFromMfaToken 解析 userId")
    class GetUserIdFromMfaTokenTests {

        @Test
        @DisplayName("空白或 null 输入返回 null")
        void blankOrNullInput_returnsNull() {
            setupValidSecret();
            MfaTokenUtils utils = new MfaTokenUtils(gatewayConfig);

            assertThat(utils.getUserIdFromMfaToken(null)).isNull();
            assertThat(utils.getUserIdFromMfaToken("")).isNull();
            assertThat(utils.getUserIdFromMfaToken("   ")).isNull();
        }

        @Test
        @DisplayName("正常 token 解析返回 userId 声明")
        void validToken_returnsUserId() {
            setupValidSecret();
            MfaTokenUtils utils = new MfaTokenUtils(gatewayConfig);
            long future = System.currentTimeMillis() + 300_000;
            String token = generateTestMfaToken("u-123", "t-456", "dave", future);

            assertThat(utils.getUserIdFromMfaToken(token)).isEqualTo("u-123");
        }
    }

    @Nested
    @DisplayName("getTenantIdFromMfaToken 解析 tenantId")
    class GetTenantIdFromMfaTokenTests {

        @Test
        @DisplayName("空白或 null 输入返回 null")
        void blankOrNullInput_returnsNull() {
            setupValidSecret();
            MfaTokenUtils utils = new MfaTokenUtils(gatewayConfig);

            assertThat(utils.getTenantIdFromMfaToken(null)).isNull();
            assertThat(utils.getTenantIdFromMfaToken("")).isNull();
            assertThat(utils.getTenantIdFromMfaToken("   ")).isNull();
        }

        @Test
        @DisplayName("正常 token 解析返回 tenantId 声明")
        void validToken_returnsTenantId() {
            setupValidSecret();
            MfaTokenUtils utils = new MfaTokenUtils(gatewayConfig);
            long future = System.currentTimeMillis() + 300_000;
            String token = generateTestMfaToken("u-1", "tenant-xyz", "eve", future);

            assertThat(utils.getTenantIdFromMfaToken(token)).isEqualTo("tenant-xyz");
        }

        @Test
        @DisplayName("token 中无 tenantId 时返回 null（而非抛异常）")
        void tokenWithoutTenantId_returnsNull() {
            setupValidSecret();
            MfaTokenUtils utils = new MfaTokenUtils(gatewayConfig);
            long future = System.currentTimeMillis() + 300_000;
            // 没有 tenantId
            String token = generateTestMfaToken("u-2", null, "frank", future);

            assertThat(utils.getTenantIdFromMfaToken(token)).isNull();
        }
    }

    @Nested
    @DisplayName("getUsernameFromMfaToken 解析用户名（JWT subject）")
    class GetUsernameFromMfaTokenTests {

        @Test
        @DisplayName("空白或 null 输入返回 null")
        void blankOrNullInput_returnsNull() {
            setupValidSecret();
            MfaTokenUtils utils = new MfaTokenUtils(gatewayConfig);

            assertThat(utils.getUsernameFromMfaToken(null)).isNull();
            assertThat(utils.getUsernameFromMfaToken("")).isNull();
            assertThat(utils.getUsernameFromMfaToken("   ")).isNull();
        }

        @Test
        @DisplayName("正常 token 解析返回 JWT subject 作为用户名")
        void validToken_returnsUsername() {
            setupValidSecret();
            MfaTokenUtils utils = new MfaTokenUtils(gatewayConfig);
            long future = System.currentTimeMillis() + 300_000;
            String token = generateTestMfaToken("u-9", "t-9", "grace", future);

            // JwtUtils.getUsername 读取 JWT subject（username 声明由 MfaTokenUtils 写入）
            assertThat(utils.getUsernameFromMfaToken(token)).isEqualTo("grace");
        }
    }

    @Nested
    @DisplayName("isValidMfaToken 验证 MFA Token 合法性")
    class IsValidMfaTokenTests {

        private String realSecret() {
            return SECRET + "0123456789012345678901234567";
        }

        @Test
        @DisplayName("blank/null 输入返回 false")
        void blankOrNullInput_returnsFalse() {
            setupValidSecret();
            MfaTokenUtils utils = new MfaTokenUtils(gatewayConfig);

            assertThat(utils.isValidMfaToken(null)).isFalse();
            assertThat(utils.isValidMfaToken("")).isFalse();
            assertThat(utils.isValidMfaToken("   ")).isFalse();
        }

        @Test
        @DisplayName("签名不匹配的 token 返回 false")
        void wrongSignature_returnsFalse() {
            setupValidSecret();
            MfaTokenUtils utils = new MfaTokenUtils(gatewayConfig);
            // 用另一个密钥签的 token
            String wrongToken = JWT.create()
                    .withSubject("alice")
                    .withExpiresAt(new Date(System.currentTimeMillis() + 300_000))
                    .withClaim("type", "MFA_TOKEN")
                    .withClaim("userId", "u-1")
                    .withClaim("username", "alice")
                    .sign(Algorithm.HMAC256("completely-different-secret-32bytes!!"));

            assertThat(utils.isValidMfaToken(wrongToken)).isFalse();
        }

        @Test
        @DisplayName("已过期的 token 返回 false")
        void expiredToken_returnsFalse() {
            setupValidSecret();
            MfaTokenUtils utils = new MfaTokenUtils(gatewayConfig);
            long past = System.currentTimeMillis() - 60_000; // 1 分钟前已过期
            String expiredToken = generateTestMfaToken("u-1", "t-1", "alice", past);

            assertThat(utils.isValidMfaToken(expiredToken)).isFalse();
        }

        @Test
        @DisplayName("type != 'MFA_TOKEN' 时返回 false")
        void wrongType_returnsFalse() {
            setupValidSecret();
            MfaTokenUtils utils = new MfaTokenUtils(gatewayConfig);
            long future = System.currentTimeMillis() + 300_000;
            String wrongTypeToken = JWT.create()
                    .withSubject("alice")
                    .withExpiresAt(new Date(future))
                    .withClaim("type", "ACCESS_TOKEN") // 不是 MFA_TOKEN
                    .withClaim("userId", "u-1")
                    .withClaim("username", "alice")
                    .sign(Algorithm.HMAC256(realSecret()));

            assertThat(utils.isValidMfaToken(wrongTypeToken)).isFalse();
        }

        @Test
        @DisplayName("合法未过期的 MFA_TOKEN 返回 true")
        void validMfaToken_returnsTrue() {
            setupValidSecret();
            MfaTokenUtils utils = new MfaTokenUtils(gatewayConfig);
            long future = System.currentTimeMillis() + 300_000;
            String validToken = generateTestMfaToken("u-77", "t-77", "henry", future);

            assertThat(utils.isValidMfaToken(validToken)).isTrue();
        }
    }
}
