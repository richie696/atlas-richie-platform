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
package com.richie.component.storage.bean;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SMB 3.0存储配置
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-04 14:42:49
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "platform.component.storage.smb3")
public class Smb3Config {

    /**
     * 是否启用SMB3存储（true：启用，false：禁用[默认]）
     */
    private Boolean enable = false;

    /**
     * 主机（IP或域名）
     */
    private String domain;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * Base路径（默认：/storage/）
     */
    private String basePath = "/storage/";

    /**
     * 是否启用DFS（默认：false）
     */
    private boolean dfs = false;

    /**
     * DFS TTL（默认：300）
     */
    private Integer dfsTtl = 300;

    /**
     * 是否启用严格视图（默认：false）
     */
    private boolean strictView = false;

    /**
     * 是否启用SMB3签名（默认：false）
     */
    private boolean convertToFQDN = false;

}
