package cn.richie696.gateway.config;

import cn.richie696.component.secret.api.SecretSnapshotChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySecretRefreshParticipantTest {

    @Test
    void commitAndRollbackAtomicallyReplaceOnlyTheSecret() {
        AuthenticationConfig current = new AuthenticationConfig();
        current.setSecretKey("old-key");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("candidate", Map.of(
                "platform.gateway.security.authentication.secret-key", "new-key")));
        var prepared = new GatewaySecretRefreshParticipant(current).prepare(
                environment,
                new SecretSnapshotChangedEvent("test", "v1", "test", "v2", Instant.now()));

        prepared.commit();
        assertThat(current.getSecretKey()).isEqualTo("new-key");

        prepared.rollback();
        assertThat(current.getSecretKey()).isEqualTo("old-key");
    }
}
