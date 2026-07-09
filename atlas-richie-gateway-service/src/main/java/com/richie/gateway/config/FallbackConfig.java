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
package com.richie.gateway.config;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 降级响应配置
 * <p>
 * 支持按 URL 路径配置不同的降级响应消息，配置存放在 Nacos 配置中心，可随时修改自定义内容
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-18
 */
@Data
@NoArgsConstructor
public class FallbackConfig implements Serializable {

    /**
     * 是否启用降级响应配置（默认：true）
     */
    private boolean enabled = true;

    /**
     * 默认降级响应消息（当没有匹配到具体路径时使用）
     */
    private String defaultMessage = "服务暂不可用，请稍后再试！";

    /**
     * 按路径配置的降级响应消息列表
     * <p>
     * 支持 Ant 路径匹配模式，例如：
     * - /api/order/**：匹配所有订单相关接口
     * - /api/user/**：匹配所有用户相关接口
     * - /api/**：匹配所有 API 接口
     * <p>
     * 匹配顺序：按配置顺序匹配，第一个匹配成功的路径将使用对应的消息
     */
    private List<PathMessage> pathMessages = new ArrayList<>();

    /**
     * 路径与消息的映射配置
     */
    @Data
    @NoArgsConstructor
    public static class PathMessage implements Serializable {

        /**
         * 路径模式（支持 Ant 路径匹配，如 /api/order/**）
         */
        private String path;

        /**
         * 该路径对应的降级响应消息
         */
        private String message;
    }
}
