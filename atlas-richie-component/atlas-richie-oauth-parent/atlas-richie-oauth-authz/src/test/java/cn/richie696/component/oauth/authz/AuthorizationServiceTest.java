package cn.richie696.component.oauth.authz;

import cn.richie696.component.oauth.authz.spi.AuthorizationCodeStore;
import cn.richie696.component.oauth.contract.model.OAuthAuthorizationRequest;
import cn.richie696.component.oauth.core.ClientRegistry;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.contract.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthorizationServiceTest {
    @Test
    void validatesRequestsIssuesCodesAndCreatesVerifiers() {
        ClientRegistry registry = mock(ClientRegistry.class);
        AuthorizationCodeStore store = mock(AuthorizationCodeStore.class);
        PKCESupport pkce = mock(PKCESupport.class);
        ClientConfig config = ClientConfig.builder()
                .enabled(true).redirectUris(List.of("https://client.example/callback"))
                .scopes(List.of("read")).grantTypes(List.of("authorization_code")).build();
        when(registry.isClientValid("client")).thenReturn(true);
        when(registry.getClient("client")).thenReturn(config);
        when(pkce.generateCodeVerifier()).thenReturn("verifier");
        AuthorizationService service = new AuthorizationService(registry, store, pkce);
        OAuthAuthorizationRequest request = new OAuthAuthorizationRequest(
                "client", "https://client.example/callback", "code", List.of("read"),
                "state", null, "challenge", "S256");

        assertThat(service.validate(request)).isSameAs(request);
        String code = service.issueCode(request, "user-1");
        assertThat(code).isNotBlank();
        verify(store).storeAuthorizationCode(eq(code), eq("client"), eq(request.redirectUri()),
                eq("challenge"), eq("S256"), eq(List.of("read")), eq("user-1"),
                isNull(), isNull(), eq(600L));
        assertThat(service.createCodeVerifier()).isEqualTo("verifier");
    }

    @Test
    void rejectsInvalidProtocolAndClientConstraints() {
        ClientRegistry registry = mock(ClientRegistry.class);
        AuthorizationService service = new AuthorizationService(registry, mock(AuthorizationCodeStore.class), new PKCESupport());
        OAuthAuthorizationRequest base = new OAuthAuthorizationRequest(
                "client", "https://client.example/callback", "code", List.of("read"),
                null, null, "challenge", "S256");
        assertThatThrownBy(() -> service.validate(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.validate(new OAuthAuthorizationRequest(
                "client", "https://client.example/callback#fragment", "code", List.of(), null, null, "c", "S256")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.validate(new OAuthAuthorizationRequest(
                "client", base.redirectUri(), "token", base.scopes(), null, null, "challenge", "S256")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.validate(new OAuthAuthorizationRequest(
                "client", base.redirectUri(), "code", base.scopes(), null, null, "challenge", "plain")))
                .isInstanceOf(BusinessException.class);
        when(registry.isClientValid("client")).thenReturn(false);
        assertThatThrownBy(() -> service.validate(base)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.issueCode(base, "")).isInstanceOf(BusinessException.class);
    }
}
