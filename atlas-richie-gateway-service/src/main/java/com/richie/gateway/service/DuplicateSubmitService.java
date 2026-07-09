/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.gateway.service;

import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * 防重复提交服务接口
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 */
public interface DuplicateSubmitService {

    /**
     * 检查请求路径是否需要防重复提交
     *
     * @param path 请求路径
     * @return 是否需要防重复提交
     */
    boolean shouldCheckDuplicateSubmit(String path);

    /**
     * 检查是否为重复提交
     *
     * @param request 请求对象
     * @param requestBody 请求体内容（可选）
     * @return 是否为重复提交
     */
    boolean isDuplicateSubmit(ServerHttpRequest request, String requestBody);

    /**
     * 记录请求提交
     *
     * @param request 请求对象
     * @param requestBody 请求体内容（可选）
     */
    void recordSubmit(ServerHttpRequest request, String requestBody);

    /**
     * 生成请求标识
     *
     * @param request 请求对象
     * @param requestBody 请求体内容（可选）
     * @return 请求标识
     */
    String generateRequestId(ServerHttpRequest request, String requestBody);

}
