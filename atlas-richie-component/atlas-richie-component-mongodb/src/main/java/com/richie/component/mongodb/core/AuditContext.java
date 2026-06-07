package com.richie.component.mongodb.core;

/**
 * Provides access to the current audit context (user information).
 * <p>
 * This component retrieves the current user's name from the Spring Security
 * context for populating audit fields. If no authentication is present or
 * Spring Security is not on the classpath, returns "system" as the default.
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