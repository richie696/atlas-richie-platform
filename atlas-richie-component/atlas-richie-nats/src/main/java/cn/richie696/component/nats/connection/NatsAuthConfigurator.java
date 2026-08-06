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
package cn.richie696.component.nats.connection;

import cn.richie696.component.nats.config.NatsProperties;
import cn.richie696.component.nats.enums.AuthType;
import cn.richie696.component.nats.exception.NatsConnectionException;
import io.nats.client.AuthHandler;
import io.nats.client.NKey;
import io.nats.client.Nats;
import io.nats.client.Options;
import lombok.extern.slf4j.Slf4j;

/**
 * NATS 认证配置器
 *
 * <p>根据 {@link NatsProperties.Auth} 配置将认证信息应用到 {@link Options.Builder}。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class NatsAuthConfigurator {

    /**
     * 将认证配置应用到 Options.Builder。
     *
     * <p>按 {@link AuthType} 分支派发：
     * <ul>
     *   <li>{@link AuthType#NONE} 或 {@code auth == null} 时直接返回，保持 jnats 默认无认证；</li>
     *   <li>{@link AuthType#TOKEN} 调用 {@link Options.Builder#token(char[])}；</li>
     *   <li>{@link AuthType#USERPASS} 调用 {@link Options.Builder#userInfo(char[], char[])}；</li>
     *   <li>{@link AuthType#CREDENTIALS} 走 {@link Options.Builder#credentialPath(String)}，
     *       由 jnats 负责加载 {@code .creds} 文件并签名；</li>
     *   <li>{@link AuthType#NKEY} 走 {@link Options.Builder#authHandler(AuthHandler)}，
     *       对每个服务端 nonce 用本地 NKey 私钥签名；</li>
     *   <li>{@link AuthType#JWT} 走 {@link Nats#staticCredentials(char[], char[])}，
     *       组合 JWT + 可选 seed 形成 DEC 凭据。</li>
     * </ul>
     *
     * @param builder Options.Builder 实例，将被原地修改
     * @param auth    认证配置；{@code null} 或 {@link AuthType#NONE} 时直接返回
     * @throws NatsConnectionException 当声明的认证类型缺少对应必填字段，或 NKey/JWT 凭据构造失败时
     */
    public void configure(Options.Builder builder, NatsProperties.Auth auth) {
        if (auth == null || auth.getType() == AuthType.NONE) {
            return;
        }

        // 各分支：先校验必填字段再调用 jnats 对应 setter；
        // 校验失败立即抛 NatsConnectionException，避免 jnats 在连接阶段才报模糊错误。
        switch (auth.getType()) {
            case TOKEN -> {
                if (auth.getToken() == null || auth.getToken().isBlank()) {
                    throw new NatsConnectionException("Auth type is TOKEN but token is not configured");
                }
                // TOKEN：服务端静态口令，直接传给 jnats。
                builder.token(auth.getToken().toCharArray());
                log.debug("NATS auth configured: TOKEN");
            }
            case USERPASS -> {
                if (auth.getUsername() == null || auth.getPassword() == null) {
                    throw new NatsConnectionException("Auth type is USERPASS but username/password is not configured");
                }
                // USERPASS：jnats 同时承担用户/密码校验。
                builder.userInfo(auth.getUsername().toCharArray(), auth.getPassword().toCharArray());
                log.debug("NATS auth configured: USERPASS");
            }
            case CREDENTIALS -> {
                if (auth.getCredentialsFile() == null || auth.getCredentialsFile().isBlank()) {
                    throw new NatsConnectionException("Auth type is CREDENTIALS but credentials-file is not configured");
                }
                // CREDENTIALS：.creds 文件同时含 JWT 与 NKey seed，
                // 交给 jnats 解析比手撕文件更安全（避免自行解析密钥材料）。
                builder.credentialPath(auth.getCredentialsFile());
                log.debug("NATS auth configured: CREDENTIALS [{}]", auth.getCredentialsFile());
            }
            case NKEY -> {
                if (auth.getSeed() == null || auth.getSeed().isBlank()) {
                    throw new NatsConnectionException("Auth type is NKEY but seed is not configured");
                }
                try {
                    var nkey = NKey.fromSeed(auth.getSeed().toCharArray());
                    // NKEY：服务端会发送 challenge nonce，本地用 NKey 私钥签名回传；
                    // AuthHandler 必须每次连接返回相同 public key，并返回 null JWT（纯 NKey 模式无 JWT）。
                    builder.authHandler(new AuthHandler() {
                        @Override
                        public byte[] sign(byte[] nonce) {
                            try {
                                return nkey.sign(nonce);
                            } catch (Exception e) {
                                throw new IllegalStateException("NKey sign failed", e);
                            }
                        }

                        @Override
                        public char[] getID() {
                            try {
                                return nkey.getPublicKey();
                            } catch (Exception e) {
                                throw new IllegalStateException("Failed to get NKey public key", e);
                            }
                        }

                        @Override
                        public char[] getJWT() {
                            return null;
                        }
                    });
                } catch (Exception e) {
                    throw new NatsConnectionException("Failed to create NKey auth handler", e);
                }
                log.debug("NATS auth configured: NKEY");
            }
            case JWT -> {
                if (auth.getJwt() == null || auth.getJwt().isBlank()) {
                    throw new NatsConnectionException("Auth type is JWT but jwt is not configured");
                }
                // JWT + 可选 seed：seed 缺失时 jnats 走静态 JWT 路径，依赖 accounts 配置放行；
                // 提供 seed 时则进入 DEC（JWT + NKey challenge）路径。
                char[] seedChars = (auth.getSeed() != null && !auth.getSeed().isBlank())
                        ? auth.getSeed().toCharArray() : null;
                builder.authHandler(Nats.staticCredentials(
                        auth.getJwt().toCharArray(), seedChars));
                log.debug("NATS auth configured: JWT");
            }
            default -> log.warn("Unknown NATS auth type: {}", auth.getType());
        }
    }
}
