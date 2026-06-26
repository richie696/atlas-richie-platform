package com.richie.context.common.api;

import com.richie.contract.exception.BusinessException;
import com.richie.contract.model.LoginUserPrincipal;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;

/**
 * 登录用户上下文。
 *
 * <p>通过 micrometer {@link ThreadLocalAccessor} 注册，支持跨异步边界自动传播。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-16 22:58:45
 */
public class LoginUserContextHolder {

    private static final String USER_CONTEXT_KEY = "login-user";
    private static final String TOKEN_CONTEXT_KEY = "login-token";
    private static final ThreadLocal<LoginUserPrincipal> USER_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<String> TOKEN_CONTEXT = new ThreadLocal<>();

    static {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new ThreadLocalAccessor<LoginUserPrincipal>() {
            @Override public Object key() { return USER_CONTEXT_KEY; }
            @Override public LoginUserPrincipal getValue() { return USER_CONTEXT.get(); }
            @Override public void setValue(LoginUserPrincipal value) { USER_CONTEXT.set(value); }
            @Override public void setValue() { USER_CONTEXT.remove(); }
        });
        ContextRegistry.getInstance().registerThreadLocalAccessor(new ThreadLocalAccessor<String>() {
            @Override public Object key() { return TOKEN_CONTEXT_KEY; }
            @Override public String getValue() { return TOKEN_CONTEXT.get(); }
            @Override public void setValue(String value) { TOKEN_CONTEXT.set(value); }
            @Override public void setValue() { TOKEN_CONTEXT.remove(); }
        });
    }

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
