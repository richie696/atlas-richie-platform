package com.richie.gateway.controller;

import com.richie.gateway.dto.ThirdPartyClientRegisterDTO;
import com.richie.gateway.service.OAuth2ClientService;
import com.richie.gateway.vo.ThirdPartyClientConfigVO;
import com.richie.gateway.vo.ThirdPartyClientRegisterVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OAuth2ClientTestController")
@ExtendWith(MockitoExtension.class)
class OAuth2ClientTestControllerTest {

    @Mock
    private OAuth2ClientService oAuth2ClientService;

    @InjectMocks
    private OAuth2ClientTestController controller;

    @Nested
    @DisplayName("register")
    class RegisterTest {

        @Test
        @DisplayName("should map request DTO to response VO with clientId and clientSecret")
        void shouldMapDtoToVo() {
            ThirdPartyClientConfigVO configVO = ThirdPartyClientConfigVO.builder()
                    .clientId("test-client-id")
                    .clientSecret("test-secret-xyz")
                    .build();
            when(oAuth2ClientService.registerTestClient(anyString())).thenReturn(configVO);

            ThirdPartyClientRegisterDTO request = new ThirdPartyClientRegisterDTO();
            request.setClientName("TestClient");

            Mono<ThirdPartyClientRegisterVO> result = controller.register(Mono.just(request));

            StepVerifier.create(result)
                    .assertNext(vo -> {
                        assertThat(vo.getClientId()).isEqualTo("test-client-id");
                        assertThat(vo.getClientSecret()).isEqualTo("test-secret-xyz");
                    })
                    .verifyComplete();

            verify(oAuth2ClientService).registerTestClient("TestClient");
        }

        @Test
        @DisplayName("should delegate to service with exact clientName")
        void shouldDelegateWithCorrectName() {
            ThirdPartyClientConfigVO configVO = ThirdPartyClientConfigVO.builder()
                    .clientId("cid")
                    .clientSecret("cs")
                    .build();
            when(oAuth2ClientService.registerTestClient("MyApp")).thenReturn(configVO);

            ThirdPartyClientRegisterDTO request = new ThirdPartyClientRegisterDTO();
            request.setClientName("MyApp");

            Mono<ThirdPartyClientRegisterVO> result = controller.register(Mono.just(request));

            StepVerifier.create(result)
                    .assertNext(vo -> assertThat(vo.getClientId()).isEqualTo("cid"))
                    .verifyComplete();

            verify(oAuth2ClientService).registerTestClient("MyApp");
        }
    }
}
