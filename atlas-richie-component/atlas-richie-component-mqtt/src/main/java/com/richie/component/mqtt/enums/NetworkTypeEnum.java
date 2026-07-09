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
package com.richie.component.mqtt.enums;

/**
 * 网络类型枚举
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-15 09:34:22
 */
public enum NetworkTypeEnum {

    /**
     * 公网服务
     */
    PUBLIC,

    /**
     * 虚拟私有网络
     */
    VPC;

    /**
     * 获取切换后的网络类型
     * <p>
     * 如果当前是公网，返回VPC；如果当前是VPC，返回公网。
     *
     * @return 切换后的网络类型
     */
    public NetworkTypeEnum getSwitchNetwork() {
        return this == PUBLIC ? VPC : PUBLIC;
    }

}
