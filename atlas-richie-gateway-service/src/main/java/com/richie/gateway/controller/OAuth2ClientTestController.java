package com.richie.gateway.controller;

import com.richie.gateway.dto.ThirdPartyClientRegisterDTO;
import com.richie.gateway.service.OAuth2ClientService;
import com.richie.gateway.vo.ThirdPartyClientConfigVO;
import com.richie.gateway.vo.ThirdPartyClientRegisterVO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * OAuth2.0 客户端注册（测试用）接口
 * <p>
 * 仅用于本地/测试环境，快速生成 clientId/clientSecret 并写入 Redis，方便联调第三方网关。
 * <p>
 * 生产环境自动禁用：通过 {@link Profile} 注解排除生产环境（prod）
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-18
 */
@RestController
@Profile("!prod")
@RequestMapping("/api/oauth2/test/client")
@RequiredArgsConstructor
public class OAuth2ClientTestController {

    private final OAuth2ClientService oAuth2ClientService;

    /**
     * 注册一个测试用第三方客户端
     *
     * @param requestMono 请求体，仅需提供 clientName
     * @return 返回生成的 client_id 和 client_secret
     */
    @PostMapping(path = "/register",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ThirdPartyClientRegisterVO> register(@RequestBody Mono<ThirdPartyClientRegisterDTO> requestMono) {
        return requestMono.map(request -> {
            ThirdPartyClientConfigVO config = oAuth2ClientService.registerTestClient(request.getClientName());
            return ThirdPartyClientRegisterVO.builder()
                    .clientId(config.getClientId())
                    .clientSecret(config.getClientSecret())
                    .build();
        });
    }
}
