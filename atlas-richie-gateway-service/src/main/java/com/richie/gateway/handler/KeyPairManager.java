package com.richie.gateway.handler;

import com.richie.gateway.config.GatewayConfig;
import com.richie.gateway.utils.EccCryptoUtils;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class KeyPairManager {
    @Getter
    private volatile long expiryTime;
    private volatile KeyPair keyPair;
    private volatile String keyId;
    private final GatewayConfig config;

    @PostConstruct
    public void init() {
        refreshKeyPair();
    }

    public synchronized void refreshKeyPair() {
        // 生成新密钥对
        try {
            this.keyPair = EccCryptoUtils.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 设置过期时间，比如1小时后
        this.expiryTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(config.getEccCrypto().getGatewayKeyExpire());
        this.keyId = UUID.randomUUID().toString();
        // 这里可以加日志
    }

    public KeyPair getKeyPair() {
        // 检查是否过期
        if (isExpired()) {
            refreshKeyPair();
        }
        return keyPair;
    }

    public String getKeyId() {
        if (isExpired()) {
            refreshKeyPair();
        }
        return keyId;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }

}
