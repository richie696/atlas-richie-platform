/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.secret.provider.aliyun;

import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import com.aliyun.credentials.Client;
import com.aliyun.kms20160120.models.DecryptRequest;
import com.aliyun.kms20160120.models.EncryptRequest;
import com.aliyun.kms20160120.models.GetSecretValueRequest;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;

import java.io.IOException;
import java.nio.file.Files;

final class AliyunClientFactory {
    AliyunSecretClient create(
            AliyunSecretConfigurationResolver.ResolvedAliyunConfiguration resolved,
            BootstrapSecretProperties bootstrap) {
        try {
            Client credentials = new Client();
            Config config = new Config()
                    .setCredential(credentials)
                    .setRegionId(resolved.properties().getRegion())
                    .setProtocol(protocol(resolved.properties()))
                    .setEndpoint(endpoint(resolved.properties()))
                    .setConnectTimeout(toMillis(bootstrap.getResilience().getConnectTimeout()))
                    .setReadTimeout(toMillis(bootstrap.getResilience().getReadTimeout()));
            if (resolved.properties().getCaFile() != null) {
                config.setCa(Files.readString(resolved.properties().getCaFile()));
            }
            com.aliyun.kms20160120.Client sdk = new com.aliyun.kms20160120.Client(config);
            RuntimeOptions runtime = new RuntimeOptions()
                    .setAutoretry(true)
                    .setMaxAttempts(Math.max(1, bootstrap.getResilience().getMaxAttempts()))
                    .setConnectTimeout(toMillis(bootstrap.getResilience().getConnectTimeout()))
                    .setReadTimeout(toMillis(bootstrap.getResilience().getReadTimeout()));
            AliyunKmsGateway gateway = new AliyunKmsGateway() {
                @Override public com.aliyun.kms20160120.models.GetSecretValueResponse getSecretValue(
                        GetSecretValueRequest request) throws Exception {
                    return sdk.getSecretValueWithOptions(request, runtime);
                }
                @Override public com.aliyun.kms20160120.models.EncryptResponse encrypt(
                        EncryptRequest request) throws Exception {
                    return sdk.encryptWithOptions(request, runtime);
                }
                @Override public com.aliyun.kms20160120.models.DecryptResponse decrypt(
                        DecryptRequest request) throws Exception {
                    return sdk.decryptWithOptions(request, runtime);
                }
                @Override public void close() { }
            };
            return new AliyunSecretClient(resolved, bootstrap, gateway, gateway::close);
        } catch (IOException exception) {
            throw new SecretConfigurationException("SEC-BOOT-003", "Cannot read Alibaba Cloud KMS CA file", exception);
        } catch (Exception exception) {
            throw new SecretConfigurationException("SEC-PROVIDER-001", "Cannot initialize Alibaba Cloud KMS", exception);
        }
    }

    private String endpoint(AliyunSecretProperties properties) {
        return properties.getEndpoint() == null
                ? "kms." + properties.getRegion() + ".aliyuncs.com"
                : properties.getEndpoint().getAuthority();
    }
    private String protocol(AliyunSecretProperties properties) {
        return properties.getEndpoint() == null
                ? "HTTPS" : properties.getEndpoint().getScheme().toUpperCase(java.util.Locale.ROOT);
    }
    private int toMillis(java.time.Duration duration) {
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, duration.toMillis()));
    }
}
