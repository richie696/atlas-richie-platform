package cn.richie696.component.nats.enums;

/**
 * NATS JetStream advisory 类型枚举。
 *
 * <p>当前实现仅处理 {@link #MAX_DELIVERIES};{@link #MSG_TERMINATED} 由业务侧主动
 * 调用 {@code msg.term()} 触发,本期不接管,留作未来扩展。
 *
 * <p>对应 NATS 官方 advisory subject:
 * <ul>
 *   <li>{@code $JS.EVENT.ADVISORY.CONSUMER.MAX_DELIVERIES.<stream>.<consumer>}</li>
 *   <li>{@code $JS.EVENT.ADVISORY.CONSUMER.MSG_TERMINATED.<stream>.<consumer>}</li>
 * </ul>
 */
public enum AdvisoryType {
    /**
     * 消息达到 consumer 配置的最大投递次数。
     */
    MAX_DELIVERIES,

    /**
     * 消息被 consumer 主动终止继续投递。
     */
    MSG_TERMINATED
}