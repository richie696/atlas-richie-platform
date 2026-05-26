package com.richie.component.messaging.service;


import com.richie.component.messaging.domain.UserInfo;

/**
 * 消息队列测试服务接口
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-02 11:31:57
 */
public interface UserService {

    /**
     * 打招呼的方法
     *
     * @param name 名字
     * @return 返回招呼
     */
    String sayHello(String name);

    /**
     * 打招呼的方法
     *
     * @param userInfo 用户信息
     * @return 返回招呼
     */
    String sayHello(UserInfo userInfo);

}
