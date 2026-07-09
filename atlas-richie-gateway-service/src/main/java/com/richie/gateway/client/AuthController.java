/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.gateway.client;

import com.richie.contract.model.ApiResult;
import com.richie.contract.constant.GlobalConstants;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.gateway.service.SignatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * token相关接口
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 17:07:06
 */
@RestController
@Tag(name = "令牌服务接口")
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SignatureService signatureService;

    /**
     * 作废令牌接口
     *
     * @param token 待作废的令牌
     * @return 返回调用结果
     */
    @GetMapping("/invalid/{token}")
    @Operation(method = "GET", summary = "作废令牌的接口", description = "当令牌需要提前作废的时候，需要调用该接口进行拉黑，否则用户可以在令牌有效期内无限制访问权限内的所有功能。")
    public Mono<ApiResult<Void>> invalidToken(@Parameter(name = "需要作废的令牌", required = true) @PathVariable("token") String token) {
        return Mono.fromCallable(() -> signatureService.invalidToken(token))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 登出接口
     * <p>
     * 将普通 token 与 MFA 令牌加入黑名单（过期时间为各自令牌的剩余有效期），并移除令牌对应用户在缓存中的登录信息。
     * 普通访问令牌与 MFA 临时令牌均从请求头读取（x-rd-request-apitoken、x-rd-request-mfa-token），与 MFA 相关参数一致。
     *
     * @param accessToken 普通访问令牌（请求头 x-rd-request-apitoken，可选）
     * @param mfaToken    MFA 临时令牌（请求头 x-rd-request-mfa-token，可选；与 accessToken 至少传其一）
     * @return 调用结果
     */
    @GetMapping("/logout")
    @Operation(method = "POST", summary = "登出", description = "将普通 token 和 MFA 令牌加入黑名单，并移除对应用户缓存。")
    public Mono<ApiResult<Void>> logout(
            @Parameter(description = "普通访问令牌") @RequestHeader(value = JwtUtils.X_ACCESS_TOKEN, required = false) String accessToken,
            @Parameter(description = "MFA 临时令牌") @RequestHeader(value = GlobalConstants.X_MFA_TOKEN, required = false) String mfaToken) {
        if (StringUtils.isBlank(accessToken) && StringUtils.isBlank(mfaToken)) {
            return Mono.just(ApiResult.success());
        }
        return Mono.fromCallable(() -> signatureService.logout(accessToken, mfaToken))
                .subscribeOn(Schedulers.boundedElastic());
    }
}

