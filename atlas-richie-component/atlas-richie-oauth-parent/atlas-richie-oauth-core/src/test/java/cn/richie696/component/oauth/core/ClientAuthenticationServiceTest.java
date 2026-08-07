package cn.richie696.component.oauth.core;

import cn.richie696.component.oauth.core.model.ClientAuthenticationRequest;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.core.spi.ClientRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAuthenticationServiceTest {

    @Test
    void enforcesConfiguredMethodAndSupportsPublicClient() {
        ClientConfig confidential = ClientConfig.builder().clientId("web").clientSecret("secret")
                .enabled(true).tokenEndpointAuthMethod("client_secret_basic").build();
        ClientConfig publicClient = ClientConfig.builder().clientId("spa").enabled(true)
                .tokenEndpointAuthMethod("none").build();
        ClientRegistry registry = new ClientRegistry(repository(confidential, publicClient));
        ClientAuthenticationService service = new ClientAuthenticationService(registry);

        assertThat(service.authenticate(ClientAuthenticationRequest.clientSecretBasic("web", "secret")).authenticated())
                .isTrue();
        assertThat(service.authenticate(ClientAuthenticationRequest.clientSecretPost("web", "secret")).authenticated())
                .isFalse();
        assertThat(service.authenticate(ClientAuthenticationRequest.publicClient("spa")).authenticated())
                .isTrue();
    }

    private ClientRepository repository(ClientConfig... clients) {
        return new ClientRepository() {
            @Override public ClientConfig find(String clientId) {
                for (ClientConfig client : clients) {
                    if (client.getClientId().equals(clientId)) return client;
                }
                return null;
            }
            @Override public void save(ClientConfig client) { }
        };
    }
}
