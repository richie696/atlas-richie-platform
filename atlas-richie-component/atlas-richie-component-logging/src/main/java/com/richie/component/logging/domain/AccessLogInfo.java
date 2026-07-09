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
package com.richie.component.logging.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 系统访问日志信息实体类
 *
 * @author richie696
 * @version 1.0
 * @since 2022-03-29 13:35:03
 */
@Data
@Accessors(chain = true)
@TableName("access_log_info")
public class AccessLogInfo implements Serializable {

    /**
     * 默认构造函数（供 MyBatis/序列化使用）。
     */
    public AccessLogInfo() {
    }

    /**
     * 日志ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 租户ID
     */
    @TableField(value = "tenant_id")
    private String tenantId;

    /**
     * 操作人ID
     */
    @TableField(value = "operator_id")
    private String operatorId;

    /**
     * 操作人
     */
    @TableField(value = "operator")
    private String operator;

    /**
     * 操作人IP
     */
    @TableField(value = "ip")
    private String ip;

    /**
     * 操作时间
     */
    @TableField(value = "operate_time")
    private OffsetDateTime operateTime;

    /**
     * 耗时（单位：毫秒）
     */
    @TableField(value = "elapsed_time")
    private Long elapsedTime;

    /**
     * 日志标题
     */
    @TableField(value = "title")
    private String title;

    /**
     * 请求方法（POST/GET/DELETE/PUT）
     */
    @TableField(value = "method")
    private String method;

    /**
     * 访问地址
     */
    @TableField(value = "url")
    private String url;

    /**
     * 请求消息体
     */
    @TableField(value = "request_body")
    private String requestBody;

    /**
     * 应答消息体
     */
    @TableField(value = "response_body")
    private String responseBody;

    /**
     * 扩展信息
     */
    @TableField(value = "extra")
    private String extra;

}
