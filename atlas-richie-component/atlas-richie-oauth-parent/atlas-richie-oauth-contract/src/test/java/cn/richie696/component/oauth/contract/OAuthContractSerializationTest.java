package cn.richie696.component.oauth.contract;

import cn.richie696.component.oauth.contract.model.OAuthAuthorizationRequest;
import cn.richie696.component.oauth.contract.model.OAuthDeviceAuthorizationRequest;
import cn.richie696.component.oauth.contract.model.OAuthDeviceAuthorizationResponse;
import cn.richie696.component.oauth.contract.model.OAuthIntrospectionResponse;
import cn.richie696.component.oauth.contract.model.OAuthPrincipal;
import cn.richie696.component.oauth.contract.model.OAuthTokenRequest;
import cn.richie696.component.oauth.contract.model.OAuthTokenResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthContractSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void tokenRequestAndResponseRoundTrip() throws Exception {
        OAuthTokenRequest request = new OAuthTokenRequest(
                "authorization_code", "client-1", "secret", "code-1", "verifier",
                "https://client/callback", null, "openid profile", "https://api.example");
        OAuthTokenRequest decodedRequest = mapper.readValue(mapper.writeValueAsString(request),
                OAuthTokenRequest.class);
        assertThat(decodedRequest).isEqualTo(request);

        OAuthTokenResponse response = new OAuthTokenResponse("at-1", "Bearer", 3600,
                "rt-1", "read");
        String responseJson = mapper.writeValueAsString(response);
        assertThat(responseJson).contains("\"access_token\":\"at-1\"")
                .contains("\"token_type\":\"Bearer\"")
                .contains("\"expires_in\":3600");
        OAuthTokenResponse decodedResponse = mapper.readValue(responseJson,
                OAuthTokenResponse.class);
        assertThat(decodedResponse).isEqualTo(response);
    }

    @Test
    void immutableCollectionsAndClaimsSurviveRoundTrip() throws Exception {
        OAuthAuthorizationRequest request = new OAuthAuthorizationRequest(
                "client-1", "https://client/callback", "code", List.of("openid"),
                "state", "https://api.example", "challenge", "S256", "nonce");
        OAuthPrincipal principal = new OAuthPrincipal("user-1", "client-1", "issuer",
                "api", "jti-1", List.of("read"), Map.of("tenant_id", "tenant-1"));
        OAuthIntrospectionResponse introspection = new OAuthIntrospectionResponse(
                true, "client-1", "Bearer", "read", "user-1", "issuer", "api",
                100, 90, "jti-1", Map.of("tenant_id", "tenant-1"));

        assertThat(mapper.readValue(mapper.writeValueAsString(request), OAuthAuthorizationRequest.class))
                .isEqualTo(request);
        assertThat(mapper.readValue(mapper.writeValueAsString(principal), OAuthPrincipal.class))
                .isEqualTo(principal);
        assertThat(mapper.readValue(mapper.writeValueAsString(introspection), OAuthIntrospectionResponse.class))
                .isEqualTo(introspection);
    }

    @Test
    void deviceAuthorizationContractUsesStandardJsonNamesAndNormalizesNulls() throws Exception {
        OAuthDeviceAuthorizationRequest request = new OAuthDeviceAuthorizationRequest(
                "device-client", null, "https://mcp.example");
        assertThat(request.scopes()).isEmpty();

        OAuthDeviceAuthorizationResponse response = new OAuthDeviceAuthorizationResponse(
                "device-1", "ABCD-2345", "https://auth.example/device",
                "https://auth.example/device?user_code=ABCD-2345", 600, 5);
        String json = mapper.writeValueAsString(response);
        assertThat(json).contains("\"device_code\":\"device-1\"")
                .contains("\"user_code\":\"ABCD-2345\"")
                .contains("\"verification_uri\":\"https://auth.example/device\"")
                .contains("\"expires_in\":600");
        assertThat(mapper.readValue(json, OAuthDeviceAuthorizationResponse.class)).isEqualTo(response);
    }

    @Test
    void principalAndAuthorizationRequestsDefensivelyCopyCollections() throws Exception {
        OAuthPrincipal principal = new OAuthPrincipal("sub", "client", "issuer", null,
                null, null, null);
        assertThat(principal.scopes()).isEmpty();
        assertThat(principal.claims()).isEmpty();
        assertThat(principal.hasScope(null)).isFalse();

        OAuthAuthorizationRequest request = new OAuthAuthorizationRequest(
                "client", "https://client/callback", "code", null,
                null, null, null, null, null, "query");
        assertThat(request.scopes()).isEmpty();
        assertThat(request.resourceIndicators()).isEmpty();
        assertThat(mapper.writeValueAsString(request)).contains("\"response_mode\":\"query\"");
    }
}
