package cn.richie696.component.oauth.core.support;

import cn.richie696.component.oauth.core.config.OAuth2Properties;
import cn.richie696.component.oauth.core.model.ClientConfig;
import com.auth0.jwt.JWT;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenClaimsCustomizerTest {

    @Test
    void hmacSignerIncludesTrustedTenantClaimButDoesNotAllowReservedOverride() {
        OAuth2Properties properties = new OAuth2Properties();
        properties.setTokenSecret("test-secret-key-32chars-long!!!!");
        properties.setIssuer("https://issuer.example");
        ClientConfig client = ClientConfig.builder()
                .clientId("client-1").enabled(true).tokenValidDuration(1).build();

        String token = new HmacAccessTokenSigner(properties).sign(
                "client-1", client, List.of("read"), "https://api.example",
                Map.of("tenant_id", "tenant-1", "aud", "attacker"));

        var decoded = JWT.decode(token);
        assertThat(decoded.getClaim("tenant_id").asString()).isEqualTo("tenant-1");
        assertThat(decoded.getAudience()).containsExactly("https://api.example");
    }
}
