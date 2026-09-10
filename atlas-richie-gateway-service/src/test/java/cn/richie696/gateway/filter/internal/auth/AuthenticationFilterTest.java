package cn.richie696.gateway.filter.internal.auth;

import cn.richie696.contract.constant.GlobalConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationFilterTest {

    @Test
    void replacesClientSuppliedIdentityHeadersWithVerifiedJwtIdentity() {
        var request = MockServerHttpRequest.get("/api/orders")
                .header("X-Client-Id", "spoofed-client")
                .header("X-User-Id", "spoofed-user")
                .header(GlobalConstants.X_TENANT_ID, "spoofed-tenant")
                .build();

        var sanitized = AuthenticationFilter.sanitizeInternalHeaders(request, "verified-user-42");

        assertThat(sanitized.getHeaders().getFirst("X-Client-Id")).isEqualTo("verified-user-42");
        assertThat(sanitized.getHeaders().getFirst("X-User-Id")).isEqualTo("verified-user-42");
        assertThat(sanitized.getHeaders().getFirst(GlobalConstants.X_TENANT_ID)).isNull();
    }

    @Test
    void removesClientSuppliedIdentityHeadersWhenVerifiedTokenHasNoIdentity() {
        var request = MockServerHttpRequest.get("/api/orders")
                .header("X-Client-Id", "spoofed-client")
                .header("X-User-Id", "spoofed-user")
                .build();

        var sanitized = AuthenticationFilter.sanitizeInternalHeaders(request, " ");

        assertThat(sanitized.getHeaders().getFirst("X-Client-Id")).isNull();
        assertThat(sanitized.getHeaders().getFirst("X-User-Id")).isNull();
    }
}
