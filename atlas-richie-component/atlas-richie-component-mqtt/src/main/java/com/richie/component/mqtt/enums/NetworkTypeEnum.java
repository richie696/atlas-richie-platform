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
