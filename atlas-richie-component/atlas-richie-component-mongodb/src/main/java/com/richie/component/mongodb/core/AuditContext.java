package com.richie.component.mongodb.core;

/**
 * 提供当前审计上下文（用户信息）的访问。
 * <p>
 * 此组件从 Spring Security 上下文获取当前用户名，用于填充审计字段。
 * 如果不存在认证信息或 Spring Security 不在 classpath 中，则默认返回 "system"。
 *
 * @author Richie
 */
public class AuditContext {

    private static final String SPRING_SECURITY_CONTEXT_CLASS = "org.springframework.security.core.context.SecurityContextHolder";

    public String currentUser() {
        try {
            Class<?> securityContextHolderClass = Class.forName(SPRING_SECURITY_CONTEXT_CLASS);
            Object context = securityContextHolderClass.getMethod("getContext").invoke(null);
            if (context == null) {
                return "system";
            }
            Object authentication = context.getClass().getMethod("getAuthentication").invoke(context);
            if (authentication == null) {
                return "system";
            }
            boolean isAuthenticated = (Boolean) authentication.getClass().getMethod("isAuthenticated").invoke(authentication);
            if (!isAuthenticated) {
                return "system";
            }
            Object name = authentication.getClass().getMethod("getName").invoke(authentication);
            return name != null ? name.toString() : "system";
        } catch (ClassNotFoundException e) {
            return "system";
        } catch (Exception e) {
            return "system";
        }
    }
}