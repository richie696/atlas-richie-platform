package cn.richie696.component.oauth.resource;

/** DPoP jti 一次性消费存储；生产环境应接入分布式 cache。 */
@FunctionalInterface
public interface DpopReplayStore {

    /** 返回 true 表示 jti 首次出现，false 表示已被使用。 */
    boolean markIfUnseen(String jti, long ttlMillis);
}
