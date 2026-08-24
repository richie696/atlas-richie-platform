package cn.richie696.component.oauth.core.support;

import cn.richie696.component.oauth.core.config.OAuth2Properties;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.secret.api.SecretSnapshotChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacAccessTokenSignerRotationTest {

    @Test
    void secretRefreshSignsWithNewKeyAndStillVerifiesPreviousToken() {
        OAuth2Properties properties = new OAuth2Properties();
        properties.setTokenSecret("old-secret-key-with-at-least-32-bytes");
        HmacAccessTokenSigner signer = new HmacAccessTokenSigner(properties);
        ClientConfig client = ClientConfig.builder().clientId("client-1").tokenValidDuration(1).build();
        String oldToken = signer.sign("client-1", client, List.of("read"), "api");

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("candidate", Map.of(
                "platform.component.oauth.token-secret", "new-secret-key-with-at-least-32-bytes",
                "platform.component.oauth.signing-key-verification-window", "PT1H")));
        var prepared = signer.prepare(environment, new SecretSnapshotChangedEvent(
                "test", "v1", "test", "v2", Instant.now()));
        prepared.commit();

        String newToken = signer.sign("client-1", client, List.of("read"), "api");
        assertThat(signer.verify(oldToken).clientId()).isEqualTo("client-1");
        assertThat(signer.verify(newToken).clientId()).isEqualTo("client-1");
        assertThat(properties.getTokenSecret()).isEqualTo("new-secret-key-with-at-least-32-bytes");
    }

    @Test
    void previousHmacStopsVerifyingAfterBoundedWindow() throws Exception {
        OAuth2Properties properties = new OAuth2Properties();
        properties.setTokenSecret("old-secret-key-with-at-least-32-bytes");
        HmacAccessTokenSigner signer = new HmacAccessTokenSigner(properties);
        ClientConfig client = ClientConfig.builder().clientId("client-1").tokenValidDuration(1).build();
        String oldToken = signer.sign("client-1", client, List.of("read"), "api");

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("candidate", Map.of(
                "platform.component.oauth.token-secret", "new-secret-key-with-at-least-32-bytes",
                "platform.component.oauth.signing-key-verification-window", Duration.ofMillis(1).toString())));
        signer.prepare(environment, new SecretSnapshotChangedEvent(
                "test", "v1", "test", "v2", Instant.now())).commit();

        Thread.sleep(10);
        assertThatThrownBy(() -> signer.verify(oldToken))
                .isInstanceOf(cn.richie696.contract.exception.BusinessException.class);
    }
}
