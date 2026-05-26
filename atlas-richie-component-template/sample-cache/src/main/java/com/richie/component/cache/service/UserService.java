package com.richie.component.cache.service;

import com.richie.component.cache.domain.UserInfo;

public interface UserService {

    /**
     * 处理用户信息的业务逻辑
     *
     * @param userInfo 用户信息
     * @throws Exception 当处理失败时抛出异常，阻止消息被确认
     */
    void processUserInfo(UserInfo userInfo) throws Exception;

    /**
     * 将用户标记为错误状态
     *
     * @param userInfo 用户信息
     * @param message  错误消息
     */
    void markUserAsError(UserInfo userInfo, String message);

}
