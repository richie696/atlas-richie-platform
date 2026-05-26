package com.richie.contract.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 基础用户信息VO父类（用于签名，所有子系统的UserInfo都需要继承）
 *
 * @author richie696
 * @version 1.0
 * @since 2024-02-22 22:02:58
 */
@Data
@Accessors(chain = true)
public class LoginUserPrincipal implements Serializable {

    /**
     * 租户ID（JWT令牌生成使用，如果项目没有租户可不填）
     */
    protected String tenantCode;

    /**
     * 租户账号过期时间
     */
    protected OffsetDateTime tenantExpiredTime;

    /**
     * 用户名
     */
    protected String username;

    /**
     * 其他需要签名的参数
     */
    private Map<String, String> signParams = new HashMap<>();

    /**
     * 默认构造方法
     */
    public LoginUserPrincipal() {
    }

    /**
     * 添加签名参数
     * @param key   签名参数key
     * @param value 签名参数value
     */
    public void addParam(String key, String value) {
        signParams.put(key, value);
    }

    /**
     * 清空签名参数
     */
    public void clearParams() {
        signParams.clear();
    }
}
