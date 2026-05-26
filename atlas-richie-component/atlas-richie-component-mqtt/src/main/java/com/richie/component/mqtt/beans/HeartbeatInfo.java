package com.richie.component.mqtt.beans;

import com.richie.context.utils.data.JsonUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 心跳数据包
 * <p>
 * 包含客户端ID、时间戳和心跳计数等信息。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-20 10:19:15
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatInfo implements Serializable {

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 心跳时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 心跳计数
     * <p>
     * 记录客户端发送的心跳包总数。
     */
    private Long count;

    /**
     * 将心跳信息序列化为字节数组
     *
     * @return 序列化后的字节数组
     */
    @JsonIgnore
    public byte[] toPayload() {
        return JsonUtils.getInstance().serializeBytes(this);
    }
}
