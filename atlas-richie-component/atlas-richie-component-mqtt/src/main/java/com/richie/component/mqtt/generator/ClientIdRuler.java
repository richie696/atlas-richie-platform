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
package com.richie.component.mqtt.generator;

/**
 * 客户端ID生成规则接口
 * <p>
 * 定义MQTT客户端ID的生成规则，不同的实现可以提供不同的生成策略。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-13
 */
public interface ClientIdRuler {

    /**
     * 获取客户端ID
     *
     * @return 客户端ID字符串
     */
    String getClientId();

}
