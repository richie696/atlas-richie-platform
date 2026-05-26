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
