package cn.richie696.component.oauth.core;

import cn.richie696.component.oauth.cache.InMemoryOAuthCache;
import cn.richie696.component.oauth.contract.model.OAuthDeviceAuthorizationRequest;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.core.spi.ClientRepository;
import cn.richie696.component.oauth.core.support.CacheBackedDeviceAuthorizationStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceAuthorizationServiceTest {

    @Test
    void issueApproveAndPollEnforcesIntervalAndConsumesOnce() throws Exception {
        ClientConfig client = ClientConfig.builder().clientId("device-client").enabled(true)
                .grantTypes(List.of("urn:ietf:params:oauth:grant-type:device_code"))
                .scopes(List.of("mcp:read")).build();
        ClientRegistry registry = new ClientRegistry(repository(client));
        var store = new CacheBackedDeviceAuthorizationStore(new InMemoryOAuthCache());
        DeviceAuthorizationService service = new DeviceAuthorizationService(registry, store,
                "https://auth.example/device", 600, 1);

        var issued = service.issue(new OAuthDeviceAuthorizationRequest("device-client",
                List.of("mcp:read"), "https://mcp.example"));
        assertThat(issued.userCode()).matches("[A-Z2-9]{4}-[A-Z2-9]{4}");

        assertThat(service.poll(issued.deviceCode(), "device-client").errorCode())
                .isEqualTo("authorization_pending");
        assertThat(service.poll(issued.deviceCode(), "device-client").errorCode())
                .isEqualTo("slow_down");

        Thread.sleep(1_100L);
        service.approve(issued.userCode(), "user-1");
        assertThat(service.poll(issued.deviceCode(), "device-client").authorized()).isTrue();
        assertThat(service.consumeAuthorized(issued.deviceCode(), "device-client")).isNotNull();
        assertThat(service.consumeAuthorized(issued.deviceCode(), "device-client")).isNull();
    }

    @Test
    void rejectsScopeOutsideClientRegistration() {
        ClientConfig client = ClientConfig.builder().clientId("device-client").enabled(true)
                .grantTypes(List.of("urn:ietf:params:oauth:grant-type:device_code"))
                .scopes(List.of("mcp:read")).build();
        DeviceAuthorizationService service = new DeviceAuthorizationService(new ClientRegistry(repository(client)),
                new CacheBackedDeviceAuthorizationStore(new InMemoryOAuthCache()), null, 600, 5);

        assertThatThrownBy(() -> service.issue(new OAuthDeviceAuthorizationRequest("device-client",
                List.of("mcp:write"), null))).isInstanceOf(RuntimeException.class);
    }

    private ClientRepository repository(ClientConfig client) {
        return new ClientRepository() {
            @Override public ClientConfig find(String clientId) {
                return client.getClientId().equals(clientId) ? client : null;
            }
            @Override public void save(ClientConfig value) { }
        };
    }
}
