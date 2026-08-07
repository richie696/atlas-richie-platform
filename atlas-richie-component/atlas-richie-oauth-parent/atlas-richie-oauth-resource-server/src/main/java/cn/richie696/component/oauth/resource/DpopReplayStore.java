package cn.richie696.component.oauth.resource;

/**
 * DPoP {@code jti} 一次性消费存储的 SPI，决定 proof 是否被重放。
 *
 * <p>处于 {@link DpopProofValidator} 与底层存储之间：上游校验器拿到 jti 与期望 TTL，
 * 下游实现负责"首次出现即记录、命中即拒绝"。接口设计为 functional interface，方便
 * 单测用 lambda 直接模拟，生产则需要接入分布式存储（Redis、Tair 等）保证多实例下
 * 的 jti 全局唯一。
 *
 * <p>解决"DPoP 抗重放机制依赖进程内 Map、单实例或灰度滚动时会出现 jti 重复放行"的
 * 协议失效问题，把 jti 持久化抽象为可替换端口，让 Resource Server 部署在 Kubernetes
 * 或多节点集群时也能严格遵守 RFC 9449 的单次消费语义。
 *
 * @author richie696
 * @since 2026-08-07
 */
@FunctionalInterface
public interface DpopReplayStore {

    /** 返回 true 表示 jti 首次出现，false 表示已被使用。 */
    boolean markIfUnseen(String jti, long ttlMillis);
}
