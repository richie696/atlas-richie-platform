package cn.richie696.component.secret.provider.common;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteRequestSignerTest {
    @Test
    void producesHuaweiAuthorizationShape() {
        RemoteProviderProperties.Authentication authentication = credentials(
                RemoteProviderProperties.RequestSignature.HUAWEI_SDK_HMAC_SHA256);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://example.test/v1/secrets/a"));
        RemoteRequestSigner.apply(builder, URI.create("https://example.test/v1/secrets/a"), "GET", new byte[0], authentication, Map.of());
        HttpRequest request = builder.build();
        assertThat(request.headers().firstValue("Authorization").orElseThrow())
                .startsWith("SDK-HMAC-SHA256 Access=ak, SignedHeaders=content-type;host;x-sdk-date, Signature=");
        assertThat(request.headers().firstValue("X-Sdk-Date")).isPresent();
    }

    @Test
    void producesTencentAuthorizationShape() {
        RemoteProviderProperties.Authentication authentication = credentials(
                RemoteProviderProperties.RequestSignature.TENCENT_TC3_HMAC_SHA256);
        authentication.setApiAction("Encrypt");
        authentication.setApiVersion("2021-11-01");
        authentication.setSigningService("kms");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://kms.tencentcloudapi.com/"));
        RemoteRequestSigner.apply(builder, URI.create("https://kms.tencentcloudapi.com/"), "POST", "{}".getBytes(), authentication, Map.of("region", "ap-guangzhou"));
        String authorization = builder.build().headers().firstValue("Authorization").orElseThrow();
        assertThat(authorization).startsWith("TC3-HMAC-SHA256 Credential=ak/");
        assertThat(authorization).contains("SignedHeaders=content-type;host;x-tc-action");
    }

    @Test
    void producesVolcengineAndBaiduAuthorizationShapes() {
        for (RemoteProviderProperties.RequestSignature signature : new RemoteProviderProperties.RequestSignature[]{
                RemoteProviderProperties.RequestSignature.VOLCENGINE_HMAC_SHA256,
                RemoteProviderProperties.RequestSignature.BAIDU_BCE_V2}) {
            RemoteProviderProperties.Authentication authentication = credentials(signature);
            if (signature == RemoteProviderProperties.RequestSignature.VOLCENGINE_HMAC_SHA256) authentication.setSigningService("kms");
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://example.test/v1/secrets/a"));
            RemoteRequestSigner.apply(builder, URI.create("https://example.test/v1/secrets/a"), "GET", new byte[0], authentication, Map.of("region", "cn-beijing"));
            String authorization = builder.build().headers().firstValue("Authorization").orElseThrow();
            assertThat(authorization).startsWith(signature == RemoteProviderProperties.RequestSignature.VOLCENGINE_HMAC_SHA256
                    ? "HMAC-SHA256 Credential=ak/" : "bce-auth-v2/ak/");
        }
    }

    private static RemoteProviderProperties.Authentication credentials(RemoteProviderProperties.RequestSignature signature) {
        RemoteProviderProperties.Authentication authentication = new RemoteProviderProperties.Authentication();
        authentication.setType(RemoteProviderProperties.AuthenticationType.ACCESS_KEY);
        authentication.setAccessKeyId("ak");
        authentication.setAccessKeySecret("sk".toCharArray());
        authentication.setSignature(signature);
        return authentication;
    }
}
