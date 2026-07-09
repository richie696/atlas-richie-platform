/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mqtt.utils;

import com.richie.component.mqtt.config.ServerInfo;
import com.richie.component.mqtt.enums.MqttProtocolEnum;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * MQTT SSL/TLS 工具类
 * <p>
 * 提供统一的 SSL/TLS 连接工厂创建功能，支持两种模式：
 * <ul>
 *   <li><strong>X.509证书认证模式</strong>：双向SSL认证，使用CA证书、客户端证书和私钥</li>
 *   <li><strong>简单TLS模式</strong>：仅用户名密码认证，适用于腾讯云MQTT等场景</li>
 * </ul>
 * <p>
 * <strong>使用场景：</strong>
 * <ul>
 *   <li>HiveMQ MQTT 客户端：通过 {@link #createTrustManagerFactory(ServerInfo.Ssl, MqttProtocolEnum)} 和
 *       {@link #createKeyManagerFactory(ServerInfo.Ssl, MqttProtocolEnum)} 创建 TrustManagerFactory 和 KeyManagerFactory</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-04
 */
@Slf4j
public class MqttSslUtils {

    /**
     * 创建 SSL/TLS Context（用于 HiveMQ MQTT 客户端）
     * <p>
     * 根据配置自动选择 X.509 证书认证模式或简单 TLS 模式。
     *
     * @param ssl      SSL配置对象，如果为 null 或未配置证书信息，则使用简单 TLS 模式
     * @param protocol 连接协议类型，用于判断是否需要 SSL/TLS
     * @return SSLContext 实例
     * @throws Exception 当创建 SSL Context 失败时抛出异常
     */
    public static SSLContext createSslContext(ServerInfo.Ssl ssl, MqttProtocolEnum protocol) throws Exception {
        // 检查是否配置了X.509证书（双向SSL认证）
        boolean hasX509Cert = Objects.nonNull(ssl)
                && Objects.nonNull(ssl.getCaCert()) && !ssl.getCaCert().isEmpty()
                && Objects.nonNull(ssl.getClientCert()) && !ssl.getClientCert().isEmpty()
                && Objects.nonNull(ssl.getClientKey()) && !ssl.getClientKey().isEmpty();

        if (protocol == MqttProtocolEnum.SSL && hasX509Cert) {
            // X.509证书认证模式（双向SSL认证）
            return createX509SslContext(ssl);
        } else {
            // 简单TLS模式（仅用户名密码认证，参考腾讯云MQTT示例）
            return createSimpleTlsContext();
        }
    }

    /**
     * 创建 X.509 证书认证的 SSL Context
     * <p>
     * 使用CA证书、客户端证书和私钥进行双向SSL认证。
     *
     * @param ssl SSL配置对象，必须包含 caCert、clientCert 和 clientKey
     * @return SSLContext 实例
     * @throws Exception 当创建 SSL Context 失败时抛出异常
     */
    private static SSLContext createX509SslContext(ServerInfo.Ssl ssl) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        var cf = CertificateFactory.getInstance("X.509");

        // load CA certificate
        var caCrtFile = new ByteArrayInputStream(Base64.getDecoder().decode(ssl.getCaCert()));
        var caCert = (X509Certificate) cf.generateCertificate(caCrtFile);

        // load client certificate
        var crtFile = new ByteArrayInputStream(Base64.getDecoder().decode(ssl.getClientCert()));
        var cert = (X509Certificate) cf.generateCertificate(crtFile);

        // load client private key
        var encodeByte = Base64.getDecoder().decode(ssl.getClientKey());
        var keySpec = new PKCS8EncodedKeySpec(encodeByte);
        var kf = KeyFactory.getInstance("RSA");
        var privateKey = kf.generatePrivate(keySpec);

        // CA certificate is used to authenticate server
        var caKs = KeyStore.getInstance(KeyStore.getDefaultType());
        caKs.load(null, null);
        caKs.setCertificateEntry("ca-certificate", caCert);
        var tmf = TrustManagerFactory.getInstance("X509");
        tmf.init(caKs);

        // client key and certificates are sent to server so it can authenticate us
        // 注意：这里使用空密码，因为私钥已经通过 Base64 解码
        char[] password = "".toCharArray();
        var ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("certificate", cert);
        ks.setKeyEntry("private-key", privateKey, password,
                new java.security.cert.Certificate[]{cert});
        var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password);

        // finally, create SSL context
        var context = SSLContext.getInstance("TLSv1.3");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return context;
    }

    /**
     * 创建简单 TLS 连接的 SSL Context
     * <p>
     * 使用默认的 TrustManager，仅通过用户名密码认证，适用于腾讯云MQTT等场景。
     * <p>
     * 参考：{@code sample.txt} 中的简单TLS连接方式
     *
     * @return SSLContext 实例
     * @throws Exception 当创建 SSL Context 失败时抛出异常
     */
    private static SSLContext createSimpleTlsContext() throws Exception {
        // 使用默认的TLS上下文，不进行证书验证（适用于使用用户名密码认证的场景）
        // 注意：这种方式会跳过证书验证，仅适用于信任的服务器环境
        var context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        return context;
    }

    /**
     * 判断是否需要 SSL/TLS 连接
     *
     * @param protocol 连接协议类型
     * @return 如果需要 SSL/TLS 连接返回 true，否则返回 false
     */
    public static boolean needsSsl(MqttProtocolEnum protocol) {
        return protocol != null && protocol.needsSsl();
    }

    /**
     * 创建 TrustManagerFactory（用于 HiveMQ 客户端）
     * <p>
     * 根据配置创建 TrustManagerFactory，用于验证服务器证书。
     *
     * @param ssl      SSL配置对象，如果为 null 或未配置证书信息，则返回 null（使用默认 TrustManager）
     * @param protocol 连接协议类型
     * @return TrustManagerFactory 实例，如果使用简单 TLS 模式则返回 null
     * @throws Exception 当创建 TrustManagerFactory 失败时抛出异常
     */
    public static TrustManagerFactory createTrustManagerFactory(ServerInfo.Ssl ssl, MqttProtocolEnum protocol) throws Exception {
        // 检查是否配置了X.509证书（双向SSL认证）
        boolean hasX509Cert = Objects.nonNull(ssl)
                && Objects.nonNull(ssl.getCaCert()) && !ssl.getCaCert().isEmpty();

        if (hasX509Cert) {
            Security.addProvider(new BouncyCastleProvider());
            var cf = CertificateFactory.getInstance("X.509");

            // load CA certificate
            var caCrtFile = new ByteArrayInputStream(Base64.getDecoder().decode(ssl.getCaCert()));
            var caCert = (X509Certificate) cf.generateCertificate(caCrtFile);

            // CA certificate is used to authenticate server
            var caKs = KeyStore.getInstance(KeyStore.getDefaultType());
            caKs.load(null, null);
            caKs.setCertificateEntry("ca-certificate", caCert);
            var tmf = TrustManagerFactory.getInstance("X509");
            tmf.init(caKs);

            return tmf;
        } else {
            // 简单 TLS 模式：返回 null，使用默认的 TrustManager
            return null;
        }
    }

    /**
     * 创建 KeyManagerFactory（用于 HiveMQ 客户端）
     * <p>
     * 根据配置创建 KeyManagerFactory，用于客户端证书认证。
     *
     * @param ssl      SSL配置对象，如果为 null 或未配置证书信息，则返回 null（不使用客户端证书）
     * @param protocol 连接协议类型
     * @return KeyManagerFactory 实例，如果使用简单 TLS 模式则返回 null
     * @throws Exception 当创建 KeyManagerFactory 失败时抛出异常
     */
    public static KeyManagerFactory createKeyManagerFactory(ServerInfo.Ssl ssl, MqttProtocolEnum protocol) throws Exception {
        // 检查是否配置了X.509证书（双向SSL认证）
        boolean hasX509Cert = Objects.nonNull(ssl)
                && Objects.nonNull(ssl.getClientCert()) && !ssl.getClientCert().isEmpty()
                && Objects.nonNull(ssl.getClientKey()) && !ssl.getClientKey().isEmpty();

        if (protocol == MqttProtocolEnum.SSL && hasX509Cert) {
            Security.addProvider(new BouncyCastleProvider());
            var cf = CertificateFactory.getInstance("X.509");

            // load client certificate
            var crtFile = new ByteArrayInputStream(Base64.getDecoder().decode(ssl.getClientCert()));
            var cert = (X509Certificate) cf.generateCertificate(crtFile);

            // load client private key
            var encodeByte = Base64.getDecoder().decode(ssl.getClientKey());
            var keySpec = new PKCS8EncodedKeySpec(encodeByte);
            var kf = KeyFactory.getInstance("RSA");
            var privateKey = kf.generatePrivate(keySpec);

            // client key and certificates are sent to server so it can authenticate us
            char[] password = "".toCharArray();
            var ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("certificate", cert);
            ks.setKeyEntry("private-key", privateKey, password,
                    new java.security.cert.Certificate[]{cert});
            var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, password);

            return kmf;
        } else {
            // 简单 TLS 模式：返回 null，不使用客户端证书
            return null;
        }
    }
}
