/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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