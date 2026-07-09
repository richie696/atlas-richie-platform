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
package com.richie.gateway.bean;


import com.richie.gateway.config.SecurityFilterConfig;
import com.richie.gateway.enums.SecurityRuleEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * 请求度量信息类
 *
 * @author richie696
 * @version 1.0
 * @since 2021/06/29
 */
@Data
@Accessors(chain = true)
public class RequestMetric implements Serializable {

    /**
     * 访问IP
     */
    private String ip;
    /**
     * 上个记录周期的起始时间
     */
    private long time;
    /**
     * 访问次数
     */
    private int count;
    /**
     * 访问规则
     */
    private SecurityRuleEnum rule;
    /**
     * 封禁结束时间
     */
    private Date blockTime;

    /**
     * 增加访问次数
     * @return 访问次数
     */
    public int addCount() {
        return ++this.count;
    }

    /**
     * 重置时间
     * @param config 安全过滤器配置
     */
    public void resetTime(SecurityFilterConfig config) {
        long time = System.currentTimeMillis() - this.time;
        if (time > config.getSecurityTimeInterval()) {
            this.time = System.currentTimeMillis();
            this.count = 0;
        }
    }

}
