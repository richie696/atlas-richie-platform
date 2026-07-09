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
package com.richie.component.logging.config;

import com.richie.component.logging.enums.RecordTypeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 操作日志配置参数
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-09 16:00:00
 */
@Data
@ConfigurationProperties(prefix = "platform.component.logging")
public class OperateLogProperties {

    /**
     * 默认构造函数（供配置绑定使用）。
     */
    public OperateLogProperties() {
    }

    /**
     * 是否启用操作日志
     */
    private boolean enable = true;

    /**
     * 记录类型（FILE：记录文件，REDIS：缓存，KAFKA：消息队列）
     */
    private RecordTypeEnum recordType = RecordTypeEnum.FILE;

    /**
     * 是否启用请求参数体持久化（默认启用）
     */
    private boolean requestBodyPersistent = true;

    /**
     * 是否限制请求参数体大小（默认不限制）
     */
    private boolean requestBodySizeLimit = false;

    /**
     * 请求参数体最大字符长度
     */
    private int requestBodyMaxLength = 200;

    /**
     * 是否启用请求参数体持久化（默认启用）
     */
    private boolean responseBodyPersistent = true;

    /**
     * 是否限制响应参数体大小（默认不限制，如果为 true，请配置
     * { @snippet
     *  responseBodyMaxLength 参数，此参数默认字符长度 200
     *  }
     */
    private boolean responseBodySizeLimit = false;

    /**
     * 响应参数体最大字符长度
     */
    private int responseBodyMaxLength = 200;

    /**
     * 是否启用数据库持久化（默认启用）
     */
    private boolean dbPersistent = true;

    /**
     * 持久化数据库时一次获取的数据量（默认：500）
     */
    private int dbBatchSize = 500;

    /**
     * 记录类型为消息队列时，消息队列的 topic 名称（默认：access-log-out-0）
     */
    private String mqTopicName = "access-log-out-0";

    /**
     * 日志保存到redis时临时暂存的路径
     */
    private String cacheAccessLogKey = "platform:access-log";

    /**
     * 启用全局切点，进入Controller的所有请求都会被拦截（默认：false）
     */
    private boolean enableGlobalAdvice = false;

    /**
     * 是否打印异常信息（默认：false）
     */
    private boolean printException = false;
}
