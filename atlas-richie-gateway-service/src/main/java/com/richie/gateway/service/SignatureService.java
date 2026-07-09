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
package com.richie.gateway.service;

import com.richie.contract.model.LoginUserPrincipal;
import com.richie.contract.model.ApiResult;
import jakarta.annotation.Nonnull;

/**
 *  部门服务接口
 *
 * @author richie696
 * @version 1.0
 * @since 2023-07-25 22:48:49
 */
public interface SignatureService {

    /**
     * 创建签名
     *
     * @param result 用户信息
     * @return 返回签名
     */
    String createSignature(@Nonnull ApiResult<LoginUserPrincipal> result);

    /**
     * 作废令牌的方法
     * @param token 令牌
     * @return 返回调用结果
     */
    ApiResult<Void> invalidToken(String token);

    /**
     * 登出：将普通 token 与 MFA 令牌加入黑名单，并移除令牌对应用户缓存
     *
     * @param accessToken 普通访问令牌（请求头 X-Access-Token，可为空则仅处理 mfaToken）
     * @param mfaToken    MFA 临时令牌（可选，如曾走 MFA 流程则传入）
     * @return 返回调用结果
     */
    ApiResult<Void> logout(String accessToken, String mfaToken);

    /**
     * 通知租户过期
     * @param tenantCode 租户编码
     * @return 返回调用结果
     */
    ApiResult<Void> notifyTenantExpired(String tenantCode);
}
