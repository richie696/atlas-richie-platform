/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.gateway.filter;

/**
 * 网关过滤器执行顺序枚举
 * 数值越小，优先级越高（越先执行）
 * 
 * 过滤器执行顺序设计原则：
 * 1. 基础设施层：国际化、加密解密等基础处理
 * 2. 安全防护层：安全策略、限流、黑名单等
 * 3. 认证授权层：Token签发、认证、SSO、权限验证
 * 4. 业务逻辑层：防重复提交、租户隔离等
 * 5. 路由转发层：负载均衡、灰度发布等
 * 
 * 数值设计原则：
 * - 使用100为间隔，便于后续插入新的过滤器
 * - 负数表示前置处理，正数表示后置处理
 * - 0作为认证过滤器的基准点
 * 
 * 执行顺序说明：
 * 1. I18nFilter (-1000): 国际化处理，最优先执行
 * 2. EccCryptoFilter (-900): ECC加密解密处理
 * 3. SecurityFilter (-800): 安全防护（IP限流、黑名单等）
 * 4. AnomalyDetectionFilter (-799): 通用异常检测处理（暴力破解、异常IP、通用限流）
 * 5. IssueTokensFilter (-700): Token签发处理
 * 6. AuthenticationFilter (0): JWT认证处理（基准点）
 * 7. SsoFilter (100): 单点登录处理
 * 8. DuplicateSubmitFilter (200): 防重复提交处理
 * 9. TenantFilter (300): 租户隔离处理
 * 10. InterfaceAuthFilter (400): 接口权限处理
 * 11. OAuth2AnomalyDetectionFilter (401): OAuth2.0专属异常检测处理（Token重放、异常刷新、客户端限流）
 * 12. OAuth2AuditFilter (402): OAuth2.0审计日志处理
 * 13. CanaryIdExtractorFilter (450): 灰度ID提取处理
 * 14. CanaryLoadBalancerFilter (LOAD_BALANCER_CLIENT_FILTER_ORDER + 2): 灰度负载均衡处理
 *
 * @author richie696
 * @version 2.0
 * @since 2025-07-27
 */
public enum FilterOrder {
    
    /**
     * 国际化过滤器 - 基础设施层，最优先执行
     * 处理请求的国际化信息，设置语言环境
     * 必须在所有其他过滤器之前执行，确保后续过滤器能获取正确的语言环境
     */
    I18N_FILTER(-1000, "国际化过滤器"),
    
    /**
     * ECC加密解密过滤器 - 基础设施层，高优先级
     * 处理请求和响应的ECC加密解密
     * 在安全防护之前执行，确保加密解密的安全性
     */
    ECC_CRYPTO_FILTER(-900, "ECC加密解密过滤器"),
    
    /**
     * 安全过滤器 - 安全防护层，高优先级
     * 处理IP限流、黑名单、安全策略等
     * 在认证之前执行，防止恶意请求消耗认证资源
     */
    SECURITY_FILTER(-800, "安全过滤器"),
    
    /**
     * 通用异常检测过滤器 - 安全防护层，高优先级
     * 处理通用异常行为检测（暴力破解、异常 IP、通用限流）
     * 适用于所有公网接口，不限于 OAuth2.0
     * 在安全过滤器之后执行
     */
    ANOMALY_DETECTION_FILTER(-799, "通用异常检测过滤器"),
    
    /**
     * Token签发过滤器 - 认证授权层，高优先级
     * 处理Token签发，仅在特定场景下执行
     * 在认证之前执行，为后续认证提供Token
     */
    ISSUE_TOKENS_FILTER(-700, "Token签发过滤器"),
    
    /**
     * 认证过滤器 - 认证授权层，基准点
     * 处理JWT令牌验证、续期等
     * 作为认证授权层的核心过滤器，设置为基准点0
     */
    AUTHENTICATION_FILTER(0, "认证过滤器"),
    
    /**
     * 单点登录过滤器 - 认证授权层，中等优先级
     * 处理单点登录相关逻辑
     * 在认证之后执行，处理SSO相关的Token验证
     */
    SSO_FILTER(100, "单点登录过滤器"),
    
    /**
     * 防重复提交过滤器 - 业务逻辑层，中等优先级
     * 防止用户在短时间内重复提交相同请求
     * 在认证授权之后执行，确保用户身份已确认
     */
    DUPLICATE_SUBMIT_FILTER(200, "防重复提交过滤器"),
    
    /**
     * 租户过滤器 - 业务逻辑层，中低优先级
     * 处理租户隔离、多租户数据隔离
     * 在认证和防重复提交之后执行，确保用户身份和请求有效性
     */
    TENANT_FILTER(300, "租户过滤器"),
    
    /**
     * 接口权限过滤器 - 业务逻辑层，低优先级
     * 处理接口级别的权限验证
     * 在租户隔离之后执行，进行最终的权限验证
     */
    INTERFACE_AUTH_FILTER(400, "接口权限过滤器"),
    
    /**
     * OAuth2.0 专属异常检测过滤器 - 业务逻辑层，低优先级
     * 处理 OAuth2.0 专属的异常行为检测（Token 重放、异常刷新、基于客户端配置的限流）
     * 通用异常检测功能（暴力破解、异常 IP、通用限流）由 {@link #ANOMALY_DETECTION_FILTER} 处理
     * 在接口权限验证之后执行
     */
    OAUTH2_ANOMALY_DETECTION_FILTER(401, "OAuth2.0 专属异常检测过滤器"),
    
    /**
     * OAuth2.0 审计过滤器 - 业务逻辑层，低优先级
     * 处理 OAuth2.0 接口的审计日志记录
     * 在异常检测之后执行，拦截响应体进行审计
     */
    OAUTH2_AUDIT_FILTER(402, "OAuth2.0 审计过滤器"),
    
    /**
     * 灰度ID提取过滤器 - 路由转发层，中低优先级
     * 自动从请求中提取灰度ID（支持自定义字段配置），设置到 X-Canary-Id 请求头
     * 内置默认规则：提取门店ID（storeId/shopCode），适用于门店维度灰度场景
     * 支持通过 CanaryFieldConfig 自定义字段配置，可提取任意类型的ID
     * 在接口权限验证之后、灰度负载均衡之前执行
     */
    CANARY_ID_EXTRACTOR_FILTER(450, "灰度ID提取过滤器"),

    /**
     * 网关身份透传过滤器 - 路由转发层，低优先级
     * <p>
     * 给每个转发请求加 {@code X-Forwarded-From-Gateway: <env>:<cluster>:<instance>} header，
     * 让 web 端（{@code atlas-richie-component-web-core}）识别"已通过 gateway"以跳过 §4.8 B 组防护。
     * <p>
     * 必须在 CanaryIdExtractorFilter 之后执行（order=451），避免被后续 filter 覆盖。
     * gateway-id 在 filter 实例化时计算一次（性能 + id 不变）。
     *
     * @see com.richie.gateway.filter.internal.routing.GatewayIdentityHeaderFilter
     */
    GATEWAY_IDENTITY_HEADER_FILTER(451, "网关身份透传过滤器"),
    
    /**
     * 灰度负载均衡过滤器 - 路由转发层，最低优先级
     * 处理灰度发布、负载均衡
     * 在所有业务逻辑处理完成后执行，进行最终的路由转发
     */
    CANARY_LOAD_BALANCER_FILTER(Integer.MAX_VALUE, "灰度负载均衡过滤器");
    
    /**
     * 过滤器执行顺序值
     */
    private final int order;
    
    /**
     * 过滤器描述
     */
    private final String description;
    
    FilterOrder(int order, String description) {
        this.order = order;
        this.description = description;
    }
    
    /**
     * 获取过滤器执行顺序值
     * 
     * @return 执行顺序值
     */
    public int getOrder() {
        return order;
    }
    
    /**
     * 获取过滤器描述
     * 
     * @return 过滤器描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 获取灰度负载均衡过滤器的实际顺序值
     * 需要基于Spring Cloud Gateway的LOAD_BALANCER_CLIENT_FILTER_ORDER
     * 
     * @param loadBalancerClientFilterOrder Spring Cloud Gateway的负载均衡过滤器顺序
     * @return 实际顺序值
     */
    public static int getCanaryLoadBalancerOrder(int loadBalancerClientFilterOrder) {
        return loadBalancerClientFilterOrder + 2;
    }
    
    /**
     * 获取所有过滤器的执行顺序信息
     * 
     * @return 过滤器执行顺序信息字符串
     */
    public static String getFilterOrderInfo() {
        StringBuilder sb = new StringBuilder("网关过滤器执行顺序（按业务逻辑分层）：\n");
        sb.append("\n【基础设施层】\n");
        sb.append(String.format("  %s: %s (%d)\n", I18N_FILTER.name(), I18N_FILTER.description, I18N_FILTER.order));
        sb.append(String.format("  %s: %s (%d)\n", ECC_CRYPTO_FILTER.name(), ECC_CRYPTO_FILTER.description, ECC_CRYPTO_FILTER.order));
        
        sb.append("\n【安全防护层】\n");
        sb.append(String.format("  %s: %s (%d)\n", SECURITY_FILTER.name(), SECURITY_FILTER.description, SECURITY_FILTER.order));
        sb.append(String.format("  %s: %s (%d)\n", ANOMALY_DETECTION_FILTER.name(), ANOMALY_DETECTION_FILTER.description, ANOMALY_DETECTION_FILTER.order));
        
        sb.append("\n【认证授权层】\n");
        sb.append(String.format("  %s: %s (%d)\n", ISSUE_TOKENS_FILTER.name(), ISSUE_TOKENS_FILTER.description, ISSUE_TOKENS_FILTER.order));
        sb.append(String.format("  %s: %s (%d) - 基准点\n", AUTHENTICATION_FILTER.name(), AUTHENTICATION_FILTER.description, AUTHENTICATION_FILTER.order));
        sb.append(String.format("  %s: %s (%d)\n", SSO_FILTER.name(), SSO_FILTER.description, SSO_FILTER.order));
        
        sb.append("\n【业务逻辑层】\n");
        sb.append(String.format("  %s: %s (%d)\n", DUPLICATE_SUBMIT_FILTER.name(), DUPLICATE_SUBMIT_FILTER.description, DUPLICATE_SUBMIT_FILTER.order));
        sb.append(String.format("  %s: %s (%d)\n", TENANT_FILTER.name(), TENANT_FILTER.description, TENANT_FILTER.order));
        sb.append(String.format("  %s: %s (%d)\n", INTERFACE_AUTH_FILTER.name(), INTERFACE_AUTH_FILTER.description, INTERFACE_AUTH_FILTER.order));
        sb.append(String.format("  %s: %s (%d)\n", OAUTH2_ANOMALY_DETECTION_FILTER.name(), OAUTH2_ANOMALY_DETECTION_FILTER.description, OAUTH2_ANOMALY_DETECTION_FILTER.order));
        sb.append(String.format("  %s: %s (%d)\n", OAUTH2_AUDIT_FILTER.name(), OAUTH2_AUDIT_FILTER.description, OAUTH2_AUDIT_FILTER.order));
        
        sb.append("\n【路由转发层】\n");
        sb.append(String.format("  %s: %s (%d)\n", CANARY_ID_EXTRACTOR_FILTER.name(), CANARY_ID_EXTRACTOR_FILTER.description, CANARY_ID_EXTRACTOR_FILTER.order));
        sb.append(String.format("  %s: %s (LOAD_BALANCER_CLIENT_FILTER_ORDER + 2)\n", CANARY_LOAD_BALANCER_FILTER.name(), CANARY_LOAD_BALANCER_FILTER.description));
        
        return sb.toString();
    }
    
    /**
     * 获取过滤器所属的业务层级
     * 
     * @return 业务层级描述
     */
    public String getBusinessLayer() {
        switch (this) {
            case I18N_FILTER:
            case ECC_CRYPTO_FILTER:
                return "基础设施层";
            case SECURITY_FILTER:
            case ANOMALY_DETECTION_FILTER:
                return "安全防护层";
            case ISSUE_TOKENS_FILTER:
            case AUTHENTICATION_FILTER:
            case SSO_FILTER:
                return "认证授权层";
            case DUPLICATE_SUBMIT_FILTER:
            case TENANT_FILTER:
            case INTERFACE_AUTH_FILTER:
            case OAUTH2_ANOMALY_DETECTION_FILTER:
            case OAUTH2_AUDIT_FILTER:
                return "业务逻辑层";
            case CANARY_ID_EXTRACTOR_FILTER:
            case CANARY_LOAD_BALANCER_FILTER:
            case GATEWAY_IDENTITY_HEADER_FILTER:
                return "路由转发层";
            default:
                return "未知层级";
        }
    }
    
    /**
     * 检查两个过滤器是否在同一业务层级
     * 
     * @param other 另一个过滤器
     * @return 是否在同一层级
     */
    public boolean isInSameLayer(FilterOrder other) {
        return this.getBusinessLayer().equals(other.getBusinessLayer());
    }
    
    /**
     * 获取当前过滤器在所属层级中的位置
     * 
     * @return 层级内位置（从1开始）
     */
    public int getPositionInLayer() {
        switch (this) {
            case I18N_FILTER:
                return 1;
            case ECC_CRYPTO_FILTER:
                return 2;
            case SECURITY_FILTER:
                return 1;
            case ANOMALY_DETECTION_FILTER:
                return 2;
            case ISSUE_TOKENS_FILTER:
                return 1;
            case AUTHENTICATION_FILTER:
                return 2;
            case SSO_FILTER:
                return 3;
            case DUPLICATE_SUBMIT_FILTER:
                return 1;
            case TENANT_FILTER:
                return 2;
            case INTERFACE_AUTH_FILTER:
                return 3;
            case OAUTH2_ANOMALY_DETECTION_FILTER:
                return 4;
            case OAUTH2_AUDIT_FILTER:
                return 5;
            case CANARY_ID_EXTRACTOR_FILTER:
                return 1;
            case GATEWAY_IDENTITY_HEADER_FILTER:
                return 2;
            case CANARY_LOAD_BALANCER_FILTER:
                return 3;
            default:
                return 0;
        }
    }
} 
