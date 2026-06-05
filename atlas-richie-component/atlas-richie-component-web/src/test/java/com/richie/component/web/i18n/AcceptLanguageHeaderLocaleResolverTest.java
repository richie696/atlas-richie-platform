package com.richie.component.web.i18n;

import com.richie.contract.constant.GlobalConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AcceptLanguageHeaderLocaleResolverTest {

    private AcceptLanguageHeaderLocaleResolver resolver;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        resolver = new AcceptLanguageHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.CHINA);
        request = mock(HttpServletRequest.class);
    }

    @Test
    void resolveLocale_usesDefaultWhenHeaderMissing() {
        when(request.getHeader(GlobalConstants.X_RD_REQUEST_LANGUAGE)).thenReturn(null);

        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.CHINA);
    }

    @Test
    void resolveLocale_parsesCustomHeader() {
        when(request.getHeader(GlobalConstants.X_RD_REQUEST_LANGUAGE)).thenReturn("en-US,en;q=0.9");

        assertThat(resolver.resolveLocale(request).getLanguage()).isEqualTo("en");
    }

    @Test
    void resolveLocale_usesRequestLocaleWhenDefaultUnsetAndHeaderBlank() {
        resolver.setDefaultLocale(null);
        when(request.getHeader(GlobalConstants.X_RD_REQUEST_LANGUAGE)).thenReturn(" ");
        when(request.getLocale()).thenReturn(Locale.FRANCE);

        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.FRANCE);
    }

    @Test
    void resolveLocale_matchesSupportedLocaleByLanguage() {
        resolver.setSupportedLocales(List.of(Locale.US));
        when(request.getHeader(GlobalConstants.X_RD_REQUEST_LANGUAGE)).thenReturn("en-GB,en;q=0.9");

        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.US);
    }
}
