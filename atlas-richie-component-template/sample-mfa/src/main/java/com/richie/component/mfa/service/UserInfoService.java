package com.richie.component.mfa.service;

import com.richie.component.mfa.domain.UserInfo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户信息服务
 */
public interface UserInfoService extends IService<UserInfo> {

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户信息或 null
     */
    UserInfo findByUsername(String username);
}

