package com.richie.component.cache.redis.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 缓存服务内存释放策略枚举
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-20 11:28:40
 */
@Getter
@RequiredArgsConstructor
public enum MemoryReleasePolicy {

    /**
     * 比例策略（默认）- 按照使用率触发释放
     * <ul>
     *     <li>当缓冲区使用率达到 ratio / (ratio + 1) 时触发内存释放</li>
     *     <li>当值为1时，缓冲区使用率达到50%时触发释放</li>
     *     <li>当值为2时，缓冲区使用率达到65%时触发释放</li>
     *     <li>当值为3时，缓冲区使用率达到75%时触发释放</li>
     * </ul>
     * <p>适用场景：平衡CPU和内存使用，数值越大内存使用越多但CPU负担越低
     * <p>推荐值：1-10之间，对应50%-90%的触发阈值
     */
    USAGE_RADIO,

    /**
     * 总是释放策略 - 每次操作都会触发内存释放
     * <ul>
     *      <li>核心机制：在每个解码阶段后调用ByteBuf.discardReadBytes()</li>
     *      <li>特点：最大限度释放内存，但会增加CPU压力</li>
     *      <li>适用场景：内存紧张，CPU资源充足的环境</li>
     * </ul>
     */
    ALWAYS,

    /**
     * 平衡策略 - 在每个解码阶段后释放一部分内存
     * <ul>
     *      <li>核心机制：在每个解码阶段后调用ByteBuf.discardSomeReadBytes()</li>
     *      <li>特点：可能释放部分、全部或不释放已读字节，具体由策略自己判断处理。此策略优势是减少CPU消耗，同时提供一定程度的内存管理</li>
     *      <li>适用场景：既关注内存也关注CPU性能的平衡场景</li>
     * </ul>
     */
    ALWAYS_SOME;


}
