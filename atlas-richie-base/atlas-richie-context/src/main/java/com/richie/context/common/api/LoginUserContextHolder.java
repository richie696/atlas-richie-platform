package com.richie.context.common.api;

import com.richie.contract.exception.BusinessException;
import com.richie.contract.model.LoginUserPrincipal;
import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 登录用户上下文
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-16 22:58:45
 */
public class LoginUserContextHolder {

    private static final TransmittableThreadLocal<LoginUserPrincipal> USER_CONTEXT = new TransmittableThreadLocal<>();
    private static final TransmittableThreadLocal<String> TOKEN_CONTEXT = new TransmittableThreadLocal<>();

    private LoginUserContextHolder() {
    }

    /**
     * 设置用户信息的方法
     *
     * @param user 用户信息
     * @param <T> 用户信息类型
     */
    public static <T extends LoginUserPrincipal> void setUserInfo(T user) {
        USER_CONTEXT.set(user);
    }

    /**
     * 清除用户信息的方法
     */
    public static void clear() {
        USER_CONTEXT.remove();
        TOKEN_CONTEXT.remove();
    }

    /**
     * 获取用户信息的方法
     *
     * @return 用户信息
     * @param <T> 用户信息类型
     */
    public static <T extends LoginUserPrincipal> T getUserInfo() {
        var userInfo = USER_CONTEXT.get();
        if (userInfo == null) {
            throw new BusinessException("用户信息为空");
        }
        //noinspection unchecked
        return (T) userInfo;
    }

    /**
     * 获取用户信息的方法
     *
     * @param allowNull 是否允许空值
     * @return 用户信息
     * @param <T> 用户信息类型
     */
    public static <T extends LoginUserPrincipal> T getUserInfo(boolean allowNull) {
        var userInfo = USER_CONTEXT.get();
        if (!allowNull && userInfo == null) {
            throw new BusinessException("用户信息为空");
        }
        //noinspection unchecked
        return (T) userInfo;
    }

    /**
     * 设置用户信息的方法
     *
     * @param token 用户令牌
     */
    public static void setToken(String token) {
        TOKEN_CONTEXT.set(token);
    }

    /**
     * 获取用户访问令牌信息的方法
     *
     * @return 返回用户访问令牌
     */
    public static String getToken() {
        return TOKEN_CONTEXT.get();
    }

}
