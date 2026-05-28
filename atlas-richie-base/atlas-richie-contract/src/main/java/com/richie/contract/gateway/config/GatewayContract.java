package com.richie.contract.gateway.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.io.Serializable;

/**
 * 网关跨服务共享契约
 * <p>
 * 职责：承载 gateway 与其它服务（业务服务、MFA/Messaging/MQTT 组件、general-service 审计消费端等）之间
 * 共享的配置数据结构。网关签发 / 其它服务校验或消费时使用同一份结构，避免不同服务对同一配置
 * 存在数据结构漂移的风险。
 * <p>
 * 该类只包含真正需要跨服务使用的字段（token 黑白名单、租户开关、灰度策略、审计总开关）。
 * gateway 内部专属的配置（ECC 加密、SSO、硬件指纹、异常检测、熔断策略等）不在本契约范围内，
 * 对应配置类位于 {@code richie-gateway-service} 工程内部，不对外暴露。
 * <p>
 * 绑定前缀与 {@code GatewayConfig} 相同（{@code platform.gateway}），Spring Boot 支持同一
 * 前缀被多个 {@link ConfigurationProperties} 绑定，各自只取自己声明的字段，因此已上线
 * 业务服务的 application.yml 和 Nacos 配置无需任何调整。
 *
 * @author richie696
 * @version 1.0
 * @since 1.0.0
 */
@Data
@Configuration
@RefreshScope
@NoArgsConstructor
@ConfigurationProperties(prefix = "platform.gateway")
public class GatewayContract implements Serializable {

    /**
     * 是否启用审计日志功能（true：启用[默认]，false：禁用）
     * <p>
     * 跨服务共享语义：
     * <ul>
     *   <li>gateway 侧控制是否发布审计事件到 Redis Stream</li>
     *   <li>general-service（或独立审计服务）侧控制是否消费并持久化这些事件</li>
     * </ul>
     * <p>
     * 两侧必须使用同一开关，否则会出现"发了没人消费"或"消费端在处理已关闭的事件"的边界问题。
     */
    private boolean auditEnabled = true;

    /**
     * 令牌过滤器配置
     * <p>
     * gateway 使用：签发 token、校验 token、判断忽略 / 登录路径。
     * <p>
     * 业务服务使用：Spring MVC 拦截器按同一份 URL 黑白名单判断是否需要校验 token，
     * 从 token 拿到 userId 后填充 {@code LoginUserContextHolder}。
     */
    private TokenFilterConfig token = new TokenFilterConfig();

    /**
     * 租户过滤器配置
     * <p>
     * gateway 使用：透传 / 校验租户请求头。
     * <p>
     * MFA 等组件使用：判断是否启用多租户，决定后续租户隔离逻辑。
     */
    private TenantFilterConfig tenant = new TenantFilterConfig();

    /**
     * 金丝雀（灰度）发布配置
     * <p>
     * gateway 使用：灰度负载均衡选择实例。
     * <p>
     * Messaging / MQTT 等组件使用：消息生产 / 消费时携带灰度标识，保证灰度流量在异步链路
     * 中不丢失。
     */
    private DeployConfig deploy = new DeployConfig();
}
