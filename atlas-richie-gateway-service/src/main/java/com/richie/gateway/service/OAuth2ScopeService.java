package com.richie.gateway.service;

import java.util.List;
import java.util.Set;

/**
 * OAuth2.0 Scope 权限服务接口（网关侧）
 * <p>
 * 负责从 Redis 读取接口所需的 scope 配置，用于网关权限验证
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-XX
 */
public interface OAuth2ScopeService {

    /**
     * 根据请求路径和 HTTP 方法查找接口所需的 Scope 列表
     * <p>
     * 匹配规则：
     * 1. 使用 Ant 路径匹配（支持 ?、*、**）
     * 2. 优先匹配更具体的路径模式
     * 3. 如果接口未配置 scope 或 require_scope=false，返回空列表（表示不需要 scope 验证）
     *
     * @param path   请求路径（如 /api/order/123）
     * @param method HTTP 方法（GET/POST/PUT/DELETE）
     * @return 接口所需的 Scope 编码列表，如果不需要 scope 验证则返回空列表
     */
    List<String> getRequiredScopes(String path, String method);

    /**
     * 验证 token 中的 scope 是否包含所需的 scope
     * <p>
     * 验证规则：
     * 1. 如果接口不需要 scope 验证（requiredScopes 为空），返回 true
     * 2. 如果接口需要 scope 验证，token 中的 scope 必须包含至少一个所需的 scope（OR 逻辑）
     *
     * @param tokenScopes      Token 中的 scope 列表（从 JWT 中提取）
     * @param requiredScopes   接口所需的 scope 列表
     * @return true 如果验证通过，false 否则
     */
    boolean verifyScope(Set<String> tokenScopes, List<String> requiredScopes);

    /**
     * 从 JWT Token 中提取 scope 列表
     *
     * @param accessToken Access Token（JWT 格式）
     * @return Scope 列表，如果 token 中没有 scope 或解析失败则返回空集合
     */
    Set<String> extractScopesFromToken(String accessToken);
}
