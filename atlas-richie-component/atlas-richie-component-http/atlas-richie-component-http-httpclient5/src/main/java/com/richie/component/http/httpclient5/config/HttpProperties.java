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
package com.richie.component.http.httpclient5.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.TimeUnit;

/**
 * HttpClient5 实现配置。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.http.httpclient5")
public class HttpProperties {

    /** 连接请求超时（默认：5，单位：秒）。从连接池获取连接的超时时间。 */
    private Integer connectionRequestTimeout = 5;
    private TimeUnit connectionRequestTimeoutTimeUnit = TimeUnit.SECONDS;

    /** 响应超时（默认：5，单位：秒）。等待服务端返回数据的超时。 */
    private Integer responseTimeout = 5;
    private TimeUnit responseTimeoutTimeUnit = TimeUnit.SECONDS;

    /** 连接管理器最大总连接数（默认：250）。 */
    private Integer maxTotal = 250;

    /** 每个 Route 最大连接数（默认：25）。 */
    private Integer defaultMaxPerRoute = 25;

}
