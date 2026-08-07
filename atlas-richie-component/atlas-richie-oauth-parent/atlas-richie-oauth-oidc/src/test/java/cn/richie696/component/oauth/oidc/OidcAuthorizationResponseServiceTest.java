package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.oidc.config.OidcProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidcAuthorizationResponseServiceTest {

    @Test
    void createsFormPostHybridResponseWithState() {
        OidcProperties properties = new OidcProperties();
        properties.setResponseTypesSupported(List.of("code", "code id_token"));
        var response = new OidcAuthorizationResponseService(properties)
                .success("code id_token", "form_post", "code-1", "id-token-1", null, "s&1");

        assertThat(response.responseMode()).isEqualTo("form_post");
        assertThat(response.parameters()).containsEntry("code", "code-1")
                .containsEntry("id_token", "id-token-1")
                .containsEntry("state", "s&1");
    }

    @Test
    void rejectsUnsupportedResponseModeAndMissingHybridPart() {
        OidcProperties properties = new OidcProperties();
        properties.setResponseTypesSupported(List.of("code id_token"));
        var service = new OidcAuthorizationResponseService(properties);
        assertThatThrownBy(() -> service.success("code id_token", "fragment", "c", "i", null, null))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.success("code id_token", "query", "c", null, null, null))
                .isInstanceOf(RuntimeException.class);
    }
}
