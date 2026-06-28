package com.richie.component.nats.connection;

import com.richie.component.nats.config.NatsProperties;
import com.richie.component.nats.enums.AuthType;
import com.richie.component.nats.exception.NatsConnectionException;
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
     * 将认证配置应用到 Options.Builder
     *
     * @param builder Options.Builder 实例
     * @param auth    认证配置
     */
    public void configure(Options.Builder builder, NatsProperties.Auth auth) {
        if (auth == null || auth.getType() == AuthType.NONE) {
            return;
        }

        switch (auth.getType()) {
            case TOKEN -> {
                if (auth.getToken() == null || auth.getToken().isBlank()) {
                    throw new NatsConnectionException("Auth type is TOKEN but token is not configured");
                }
                builder.token(auth.getToken().toCharArray());
                log.debug("NATS auth configured: TOKEN");
            }
            case USERPASS -> {
                if (auth.getUsername() == null || auth.getPassword() == null) {
                    throw new NatsConnectionException("Auth type is USERPASS but username/password is not configured");
                }
                builder.userInfo(auth.getUsername().toCharArray(), auth.getPassword().toCharArray());
                log.debug("NATS auth configured: USERPASS");
            }
            case CREDENTIALS -> {
                if (auth.getCredentialsFile() == null || auth.getCredentialsFile().isBlank()) {
                    throw new NatsConnectionException("Auth type is CREDENTIALS but credentials-file is not configured");
                }
                builder.credentialPath(auth.getCredentialsFile());
                log.debug("NATS auth configured: CREDENTIALS [{}]", auth.getCredentialsFile());
            }
            case NKEY -> {
                if (auth.getSeed() == null || auth.getSeed().isBlank()) {
                    throw new NatsConnectionException("Auth type is NKEY but seed is not configured");
                }
                try {
                    var nkey = io.nats.client.NKey.fromSeed(auth.getSeed().toCharArray());
                    builder.authHandler(new io.nats.client.AuthHandler() {
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
                char[] seedChars = (auth.getSeed() != null && !auth.getSeed().isBlank())
                        ? auth.getSeed().toCharArray() : null;
                builder.authHandler(io.nats.client.Nats.staticCredentials(
                        auth.getJwt().toCharArray(), seedChars));
                log.debug("NATS auth configured: JWT");
            }
            default -> log.warn("Unknown NATS auth type: {}", auth.getType());
        }
    }
}
