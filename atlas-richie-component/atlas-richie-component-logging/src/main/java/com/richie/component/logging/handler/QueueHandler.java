/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.logging.handler;

import org.springframework.util.MimeType;

/**
 * MQ消息队列服务接口
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-27 11:15:18
 */
interface QueueHandler {

    /**
     * 采用通道名称作为默认消息键的队列发送方法
     *
     * @param topicAlias 发送消息的Topic别名（建议使用枚举进行定义，不要硬编码字符串）
     * @param message    发送的具体消息
     * @return 返回发送结果（true：发送成功，false：发送失败）
     */
    boolean sendMessage(String topicAlias, Object message);

    /**
     * 采用通道名称作为默认消息键的队列发送方法（多MQ服务时使用）
     *
     * @param topicAlias 发送消息的Topic别名（建议使用枚举进行定义，不要硬编码字符串）
     * @param binderName 绑定器名称（多MQ服务时使用）
     * @param message    发送的具体消息
     * @return 返回发送结果（true：发送成功，false：发送失败）
     */
    boolean sendMessage(String topicAlias, String binderName, Object message);

    /**
     * 采用通道名称作为默认消息键的队列发送方法（可自定义OUTPUT消息的类型）
     *
     * @param topicAlias        发送消息的Topic别名（建议使用枚举进行定义，不要硬编码字符串）
     * @param message           发送的具体消息
     * @param outputContentType 输出的消息体类型
     * @see org.springframework.util.MimeTypeUtils 输出类型可使用“MimeTypeUtils”中的常量类型
     * @return 返回发送结果（true：发送成功，false：发送失败）
     */
    boolean sendMessage(String topicAlias, Object message, MimeType outputContentType);

    /**
     * 可自定义消息键的消息队列发送方法（多MQ服务时使用）
     *
     * @param topicAlias        发送消息的Topic别名（建议使用枚举进行定义，不要硬编码字符串）
     * @param binderName    绑定器名称（多MQ服务时使用）
     * @param message           发送的具体消息
     * @param outputContentType 输出的消息体类型
     * @see org.springframework.util.MimeTypeUtils 输出类型可使用“MimeTypeUtils”中的常量类型
     * @return 返回发送结果（true：发送成功，false：发送失败）
     */
    boolean sendMessage(String topicAlias, String binderName, Object message, MimeType outputContentType);

    /**
     * 采用通道名称作为默认消息键的队列发送方法
     *
     * @param topicAlias 发送消息的Topic别名（建议使用枚举进行定义，不要硬编码字符串）
     * @param message    发送的具体消息
     * @param delayTime 延迟时间（单位：毫秒）
     * @return 返回发送结果（true：发送成功，false：发送失败）
     */
    boolean sendMessage(String topicAlias, Object message, long delayTime);

    /**
     * 采用通道名称作为默认消息键的队列发送方法（多MQ服务时使用）
     *
     * @param topicAlias 发送消息的Topic别名（建议使用枚举进行定义，不要硬编码字符串）
     * @param binderName 绑定器名称（多MQ服务时使用）
     * @param message    发送的具体消息
     * @param delayTime 延迟时间（单位：毫秒）
     * @return 返回发送结果（true：发送成功，false：发送失败）
     */
    boolean sendMessage(String topicAlias, String binderName, Object message, long delayTime);

    /**
     * 采用通道名称作为默认消息键的队列发送方法（可自定义OUTPUT消息的类型）
     *
     * @param topicAlias        发送消息的Topic别名（建议使用枚举进行定义，不要硬编码字符串）
     * @param message           发送的具体消息
     * @param outputContentType 输出的消息体类型
     * @param delayTime 延迟时间（单位：毫秒）
     * @see org.springframework.util.MimeTypeUtils 输出类型可使用“MimeTypeUtils”中的常量类型
     * @return 返回发送结果（true：发送成功，false：发送失败）
     */
    boolean sendMessage(String topicAlias, Object message, MimeType outputContentType, long delayTime);

    /**
     * 可自定义消息键的消息队列发送方法（多MQ服务时使用）
     *
     * @param topicAlias        发送消息的Topic别名（建议使用枚举进行定义，不要硬编码字符串）
     * @param binderName    绑定器名称（多MQ服务时使用）
     * @param body           发送的具体消息
     * @param outputContentType 输出的消息体类型
     * @param delayTime 延迟时间（单位：毫秒）
     * @see org.springframework.util.MimeTypeUtils 输出类型可使用“MimeTypeUtils”中的常量类型
     * @return 返回发送结果（true：发送成功，false：发送失败）
     */
    boolean sendMessage(String topicAlias, String binderName, Object body, MimeType outputContentType, long delayTime);
}
