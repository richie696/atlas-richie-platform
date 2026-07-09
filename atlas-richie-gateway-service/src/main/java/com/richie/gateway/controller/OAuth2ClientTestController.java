/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.gateway.controller;

import com.richie.component.oauth.core.ClientRegistry;
import com.richie.component.oauth.core.model.ClientConfig;
import com.richie.gateway.dto.ThirdPartyClientRegisterDTO;
import com.richie.gateway.vo.ThirdPartyClientRegisterVO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
@ConditionalOnBean(ClientRegistry.class)
public class OAuth2ClientTestController {

    private final ClientRegistry clientRegistry;

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
            ClientConfig config = clientRegistry.registerTestClient(request.getClientName());
            return ThirdPartyClientRegisterVO.builder()
                    .clientId(config.getClientId())
                    .clientSecret(config.getClientSecret())
                    .build();
        });
    }
}
