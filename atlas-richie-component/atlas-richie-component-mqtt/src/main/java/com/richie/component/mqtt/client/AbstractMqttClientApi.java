package com.richie.component.mqtt.client;

import com.richie.component.mqtt.beans.ConsumerListener;
import com.richie.component.mqtt.beans.ConsumerMessage;
import com.richie.component.mqtt.config.MqttClientProperties;
import com.richie.component.mqtt.enums.NetworkTypeEnum;
import com.richie.component.mqtt.generator.IMqttClientDeviceIdGenerator;
import lombok.Getter;
import jakarta.annotation.Nonnull;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * MQTT客户端抽象基类
 * <p>
 * 提供MQTT客户端的基础功能实现，包括消息订阅、取消订阅、消费者注册等。
 * 子类需要实现具体的连接和消息处理逻辑。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-15 12:36:26
 */
public abstract non-sealed class AbstractMqttClientApi extends MqttEventPublisher implements MqttClientApi {

    /**
     * MQTT 客户端配置信息
     */
    protected MqttClientProperties properties;

    /**
     * 当前网络类型（共有网络、私有网络）
     */
    @Getter
    protected NetworkTypeEnum networkType;

    /**
     * 客户端ID
     */
    @Getter
    protected String clientId;

    /**
     * 心跳主题路径
     */
    private static final String HEARTBEAT_TOPIC_PREFIX = "/MQTT_CLIENT_HB";

    /**
     * 设备ID生成器
     */
    protected IMqttClientDeviceIdGenerator deviceIdGenerator;

    /**
     * 普通订阅监听器缓存
     * <p>
     * Key：业务 topic（例：/device/+/status）
     * Value：对应的业务回调函数
     */
    protected static final Map<String, Consumer<ConsumerMessage>> LISTENER_CACHE = new ConcurrentHashMap<>(32);

    /**
     * 共享订阅监听器缓存
     * <p>
     * Key：完整的共享订阅 topic（格式：$share/{groupId}/businessTopic，包含通配符，如：$share/GID_AGENT_DEVICE/device/+/status）
     * Value：对应的业务回调函数
     * <p>
     * 注意：收到消息时，实际 topic 是具体值（如：device/123/status），需要通过通配符匹配找到对应的回调
     */
    protected static final Map<String, Consumer<ConsumerMessage>> SHARED_LISTENER_CACHE = new ConcurrentHashMap<>(32);

    /**
     * 获取心跳主题地址的方法
     *
     * @return 返回心跳主题地址
     */
    protected String getHeartbeatTopic() {
        return getParentTopic() + HEARTBEAT_TOPIC_PREFIX;
    }

    /**
     * 获取当前客户端分组ID的方法
     *
     * @return 返回当前客户端的分组ID
     */
    @Override
    public String getGroupId() {
        return this.properties.getGroupId();
    }

    /**
     * 获取订阅根主题的方法
     *
     * @return 返回订阅根主题
     */
    @Override
    public String getParentTopic() {
        return this.properties.getParentTopic();
    }

    @Override
    public void registerConsumer(@Nonnull String topic, @Nonnull Consumer<ConsumerMessage> callback) {
        if (LISTENER_CACHE.containsKey(topic)) {
            throw new IllegalArgumentException("当前主题已注册回调事件，请勿重复注册。");
        }
        LISTENER_CACHE.put(topic, callback);
        doSubscribe(topic);
    }

    /**
     * 注册共享订阅消费者
     * <p>
     * 共享订阅的物理订阅 topic 形如：$share/{groupId}/{businessTopic}
     * 缓存 key 使用完整的共享订阅 topic，便于收到消息时进行通配符匹配。
     * <p>
     * 必须传入完整的共享订阅 topic（包含 $share/{groupId} 前缀），
     * 方法内部会校验格式。
     *
     * @param sharedTopic 完整的共享订阅 topic（格式：$share/{groupId}/businessTopic，如：$share/GID_AGENT_DEVICE/device/+/status）
     * @param callback    业务回调函数
     * @throws IllegalArgumentException 如果 topic 格式不正确
     */
    public void registerSharedConsumer(@Nonnull String sharedTopic, @Nonnull Consumer<ConsumerMessage> callback) {
        // 校验格式（但不提取业务 topic，直接用完整 topic 作为缓存 key）
        validateSharedTopic(sharedTopic);

        if (SHARED_LISTENER_CACHE.containsKey(sharedTopic)) {
            throw new IllegalArgumentException("当前共享订阅主题已注册回调事件，请勿重复注册。sharedTopic=" + sharedTopic);
        }
        // 使用完整的共享订阅 topic 作为缓存 key
        SHARED_LISTENER_CACHE.put(sharedTopic, callback);
        // 直接使用传入的完整共享订阅 topic 进行订阅
        doSubscribe(sharedTopic);
    }

    @Override
    public void unregisterConsumer(@Nonnull String topic) {
        LISTENER_CACHE.remove(topic);
        doUnsubscribe(topic);
    }

    /**
     * 注销共享订阅消费者
     * <p>
     * 必须传入完整的共享订阅 topic（包含 $share/{groupId} 前缀），
     * 方法内部会校验格式。
     *
     * @param sharedTopic 完整的共享订阅 topic（格式：$share/{groupId}/businessTopic，如：$share/GID_AGENT_DEVICE/device/+/status）
     * @throws IllegalArgumentException 如果 topic 格式不正确
     */
    public void unregisterSharedConsumer(@Nonnull String sharedTopic) {
        // 校验格式（但不提取业务 topic，直接用完整 topic 作为缓存 key）
        validateSharedTopic(sharedTopic);

        // 使用完整的共享订阅 topic 作为缓存 key
        SHARED_LISTENER_CACHE.remove(sharedTopic);
        // 直接使用传入的完整共享订阅 topic 进行取消订阅
        doUnsubscribe(sharedTopic);
    }

    @Override
    public void registerConsumers(@Nonnull Set<ConsumerListener> listeners) {
        for (ConsumerListener listener : listeners) {
            registerConsumer(listener.getTopic(), listener.getCallback());
        }
    }

    @Override
    public void unregisterConsumers(@Nonnull String... topics) {
        for (String topic : topics) {
            unregisterConsumer(topic);
        }
    }

    @Override
    public void unregisterConsumers(@Nonnull Set<String> topics) {
        for (String topic : topics) {
            unregisterConsumer(topic);
        }
    }

    /**
     * 校验共享订阅 topic 格式并提取业务 topic
     * <p>
     * 格式要求：$share/{groupId}/businessTopic
     * <p>
     * 共享订阅 topic 必须分为 3 段：
     * <ol>
     *     <li><strong>第一段：$share</strong> - 共享订阅标识符</li>
     *     <li><strong>第二段：groupId</strong> - 共享订阅组 ID（如：GID_AGENT_DEVICE）</li>
     *     <li><strong>第三段：businessTopic</strong> - 业务 topic，必须包含 /+/ 通配符</li>
     * </ol>
     * <p>
     * 校验规则：
     * <ul>
     *     <li>必须以 $share/ 开头</li>
     *     <li>必须包含 groupId（$share/ 后的第一个 / 之前的部分，不能为空）</li>
     *     <li>必须包含业务 topic（第一个 / 之后的部分，不能为空）</li>
     *     <li>业务 topic 中必须包含 /+/ 通配符（用于通配唯一标识符）</li>
     * </ul>
     *
     * @param sharedTopic 完整的共享订阅 topic（格式：$share/{groupId}/businessTopic）
     * @throws IllegalArgumentException 如果 topic 格式不正确
     */
    protected void validateSharedTopic(@Nonnull String sharedTopic) {
        // 1. 校验第一段：必须以 $share/ 开头
        if (!sharedTopic.startsWith("$share/")) {
            throw new IllegalArgumentException(
                    "共享订阅 topic 必须以 $share/ 开头，格式应为 $share/{groupId}/businessTopic，实际为: %s".formatted(sharedTopic));
        }

        // 2. 找到第二段和第三段的分隔符（$share/ 后的第一个 /）
        int firstSlash = sharedTopic.indexOf('/', "$share/".length());
        if (firstSlash <= 0 || firstSlash + 1 >= sharedTopic.length()) {
            throw new IllegalArgumentException(
                    "共享订阅 topic 格式错误，应为 $share/{groupId}/businessTopic（3 段），实际为: %s".formatted(sharedTopic));
        }

        // 3. 提取并校验第二段：groupId
        String groupId = sharedTopic.substring("$share/".length(), firstSlash);
        if (groupId.isEmpty()) {
            throw new IllegalArgumentException(
                    "共享订阅 topic 的 groupId（第二段）不能为空，格式应为 $share/{groupId}/businessTopic，实际为: %s".formatted(sharedTopic));
        }

        // 4. 提取并校验第三段：businessTopic
        String businessTopic = sharedTopic.substring(firstSlash + 1);
        if (businessTopic.isEmpty()) {
            throw new IllegalArgumentException(
                    "共享订阅 topic 的业务 topic（第三段）不能为空，格式应为 $share/{groupId}/businessTopic，实际为: %s".formatted(sharedTopic));
        }

        // 5. 校验业务 topic 中必须包含 /+/ 通配符
        if (!businessTopic.contains("/+/")) {
            throw new IllegalArgumentException(
                    "共享订阅的业务 topic 必须包含 /+/ 通配符（用于通配唯一标识符），格式应为 $share/{groupId}/.../+/...，实际为: %s".formatted(sharedTopic));
        }
    }

    /**
     * 由子类实现具体的订阅逻辑
     *
     * @param topic 要订阅的主题
     */
    protected abstract void doSubscribe(String topic);

    /**
     * 由子类实现具体的取消订阅逻辑
     *
     * @param topic 要取消订阅的主题
     */
    protected abstract void doUnsubscribe(String topic);

}
