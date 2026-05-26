package com.richie.gateway.service;

import com.richie.gateway.vo.ThirdPartyClientConfigVO;

/**
 * 第三方客户端服务接口（网关侧）
 * <p>
 * 负责从 Redis 读取客户端配置，用于网关认证
 * <p>
 * 同时提供测试场景下的客户端快速注册能力。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-16
 */
public interface OAuth2ClientService {

    /**
     * 根据 clientId 与字段枚举从 Redis Hash 读取指定字段值
     *
     * @param clientId 客户端ID
     * @param field    字段枚举（支持多个）
     * @param <T>      字段值类型
     * @return 字段值，如果不存在则返回 null
     */
    <T> T getClientConfig(String clientId, ThirdPartyClientConfigVO.Field... field);

    /**
     * 验证客户端是否存在且已启用
     *
     * @param clientId 客户端ID
     * @return 是否存在且已启用
     */
    boolean isClientValid(String clientId);

    /**
     * 验证客户端密钥
     *
     * @param clientId     客户端ID
     * @param clientSecret 客户端密钥
     * @return 是否验证通过
     */
    boolean verifyClientSecret(String clientId, String clientSecret);

    /**
     * 测试环境下快速注册一个第三方客户端，并写入 Redis。
     *
     * @param clientName 客户端名称
     * @return 注册后的客户端配置（包含 clientId/clientSecret）
     */
    ThirdPartyClientConfigVO registerTestClient(String clientName);
}
