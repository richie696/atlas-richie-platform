package com.richie.component.ai.support.sign;

import com.richie.component.ai.api.voicechat.StsTicket;

import java.time.Instant;

/**
 * 厂商 STS 凭证签发 SPI。
 *
 * <p>本接口遵循 R-N 设计 §14.3.3 原则 J — WS + STS 统一接口原则:
 * 任何 vendor WebSocket 接入与 STS 凭证签发必须收敛到统一 SPI。
 *
 * <h2>业务约束</h2>
 * <ul>
 *   <li>业务代码 (api/voicechat/, service/) 仅依赖 {@code StsSigner} 抽象,不引用任何 vendor 包</li>
 *   <li>配置文件切换 vendor = application.yml 一行,业务代码零改动</li>
 *   <li>新增 vendor 仅需实现本接口 + 注册为 Spring Bean,不动现有业务代码</li>
 * </ul>
 *
 * <h2>实现模式</h2>
 * 每个 vendor / 凭证域对应一个独立实现类:
 * <ul>
 *   <li>{@link com.richie.component.ai.support.sign.BearerStsSigner} — DashScope / Zhipu / Hunyuan-TokenHub / MiniMax</li>
 *   <li>{@link com.richie.component.ai.support.sign.Tc3StsSigner} — 腾讯云 TC3-HMAC-SHA256 (Hunyuan TTS 1073 / STT 1093)</li>
 *   <li>{@link com.richie.component.ai.support.sign.AppCodeStsSigner} — 阿里 APIG / 华为 APIG (Pangu / 华为 SIS)</li>
 *   <li>{@link com.richie.component.ai.support.sign.AkSkHmacStsSigner} — 火山引擎 OpenAPI 签名 (Doubao VikingDB)</li>
 *   <li>{@link com.richie.component.ai.support.sign.XApiKeyStsSigner} — Doubao openspeech (X-Api-Key + X-Api-Resource-Id)</li>
 * </ul>
 *
 * <h2>线程安全</h2>
 * 实现类应保证无状态或线程安全,Spring 默认 singleton bean 即可。
 *
 * @author Sisyphus
 * @see StsTicket
 * @see VendorStsContext
 * @see com.richie.component.ai.service.VoiceStsService
 * @since 1.0.0
 */
public interface StsSigner {

    /**
     * 当前实现所属厂商标识。
     *
     * <p>取值见 {@code StsTicket#VENDOR_*} 系列常量。
     * 业务侧按 {@link VendorStsContext#getVendor()} 路由到匹配的 signer。
     *
     * @return 厂商标识字符串
     */
    String vendor();

    /**
     * 当前实现支持的能力子集。
     *
     * <p>一个 signer 通常只支持单一域 (Bearer / TC3 / AK-SK 等),通过此方法实现细粒度路由。
     *
     * <p>特殊场景: 若实现同时支持 Bearer 和 TC3 (例如某些多认证域厂商),
     * 可重写 {@link #sign(VendorStsContext)} 时按 {@link VendorStsContext#getCapability()} 分支。
     *
     * @return 支持的认证域标识,建议使用 {@link StsTicket} 中的命名常量
     */
    String[] supportedAuthDomains();

    /**
     * 判断当前实现是否能处理给定上下文。
     *
     * <p>默认规则: {@link #vendor()} 等于 {@link VendorStsContext#getVendor()} 且
     * {@link #supportedAuthDomains()} 包含 {@link VendorStsContext#getAuthDomain()}。
     *
     * <p>子类可重写以支持额外规则 (例如能力级别匹配、白名单等)。
     *
     * @param ctx 上下文 (vendor / capability / model / ttl / attributes)
     * @return true 表示能处理
     */
    default boolean supports(VendorStsContext ctx) {
        if (ctx == null || !vendor().equals(ctx.getVendor())) {
            return false;
        }
        for (String domain : supportedAuthDomains()) {
            if (domain.equals(ctx.getAuthDomain())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 签发 STS 凭证票面。
     *
     * <p>实现必须返回一个新的 {@link StsTicket},不修改入参。
     * 若签发失败,抛出运行时异常 (例如 {@link IllegalStateException}),
     * 由 {@code VoiceStsService} 统一捕获并转换为业务异常。
     *
     * @param ctx 上下文 (vendor / capability / model / ttl / attributes)
     * @return 不可变 STS 票面,包含 endpoint / credentials / ttl 等
     * @throws IllegalStateException 凭证获取失败 (例如长时凭证未配置、网络失败)
     */
    StsTicket sign(VendorStsContext ctx);

    /**
     * 默认 TTL (秒),当上下文未指定时使用。
     *
     * <p>R-N 设计 §14.3.3 推荐默认 3600s,与厂商短时凭证默认有效期一致。
     *
     * @return 默认 ttl (秒)
     */
    default int defaultTtlSeconds() {
        return 3600;
    }

    /**
     * 创建默认上下文 (vendor + authDomain(取第一个 supportedAuthDomain) + capability + model, ttl 用 {@link #defaultTtlSeconds()})。
     *
     * @param vendor    厂商标识
     * @param capability 能力 (VOICE_CHAT / TTS_STREAM / STT_STREAM)
     * @param model     模型名
     * @return 上下文
     */
    default VendorStsContext defaultContext(String vendor, String capability, String model) {
        String[] domains = supportedAuthDomains();
        String authDomain = (domains != null && domains.length > 0) ? domains[0] : "unknown";
        return VendorStsContext.builder()
                .vendor(vendor)
                .authDomain(authDomain)
                .capability(capability)
                .model(model)
                .ttlSeconds(defaultTtlSeconds())
                .issuedAt(Instant.now())
                .build();
    }
}