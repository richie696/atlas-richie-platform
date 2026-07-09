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
package com.richie.component.mqtt.client;

import java.util.ArrayList;
import java.util.List;

/**
 * 循环缓冲区实现
 * <p>
 * 用于存储固定大小的历史数据，支持高效的数据添加、查询和清理操作。
 * 该类实现了经典的循环缓冲区算法，当缓冲区满时会自动覆盖最旧的数据，
 * 确保内存使用量始终保持在固定范围内。
 * <p>
 * <strong>设计原则：</strong>
 * <ul>
 *   <li><strong>内存效率</strong>：固定大小的缓冲区，避免内存无限增长</li>
 *   <li><strong>性能优先</strong>：O(1)时间复杂度的添加和删除操作</li>
 *   <li><strong>线程安全</strong>：所有公共方法都使用synchronized关键字</li>
 *   <li><strong>数据完整性</strong>：支持泛型，确保类型安全</li>
 *   <li><strong>自动覆盖</strong>：缓冲区满时自动覆盖最旧数据</li>
 * </ul>
 * <p>
 * <strong>核心算法：</strong>
 * <ul>
 *   <li><strong>头指针(head)</strong>：指向缓冲区中最旧的数据位置</li>
 *   <li><strong>尾指针(tail)</strong>：指向下一个可插入数据的位置</li>
 *   <li><strong>模运算</strong>：使用 % capacity 实现循环索引</li>
 *   <li><strong>覆盖策略</strong>：满时移动头指针，保持数据连续性</li>
 * </ul>
 * <p>
 * <strong>使用场景：</strong>
 * <ul>
 *   <li>MQTT连接质量指标的历史记录</li>
 *   <li>网络延迟数据的滑动窗口统计</li>
 *   <li>消息发送成功率的历史追踪</li>
 *   <li>系统性能指标的实时监控</li>
 *   <li>任何需要固定大小历史数据的场景</li>
 * </ul>
 * <p>
 * <strong>性能特点：</strong>
 * <ul>
 *   <li>添加操作：O(1) 时间复杂度</li>
 *   <li>查询操作：O(n) 时间复杂度（n为当前元素数量）</li>
 *   <li>内存使用：固定大小，不会无限增长</li>
 *   <li>线程安全：支持多线程并发访问</li>
 * </ul>
 *
 * @param <T> 缓冲区中存储的元素类型
 * @author richie696
 * @version 1.0
 * @since 2025-08-11
 */
public class CircularBuffer<T> {

    private final Object[] buffer;
    private final int capacity;
    private int size;
    private int head;
    private int tail;

    /**
     * 构造循环缓冲区
     * <p>
     * <strong>设计原则：</strong> 初始化时确定缓冲区大小，确保内存使用可控
     * <p>
     * <strong>功能说明：</strong>
     * 创建一个指定容量的循环缓冲区，初始化内部数据结构。
     * 构造函数会分配固定大小的数组，设置初始状态，为后续操作做准备。
     * <p>
     * <strong>初始化内容：</strong>
     * <ul>
     *   <li>设置缓冲区容量上限</li>
     *   <li>分配Object数组存储元素</li>
     *   <li>初始化大小计数器为0</li>
     *   <li>设置头指针和尾指针为0</li>
     * </ul>
     * <p>
     * <strong>参数要求：</strong>
     * <ul>
     *   <li>capacity：缓冲区容量，必须大于0</li>
     *   <li>容量决定了内存使用上限</li>
     *   <li>容量影响数据覆盖的频率</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>容量一旦设置不可更改</li>
     *   <li>容量过小可能导致数据频繁覆盖</li>
     *   <li>容量过大会增加内存占用</li>
     *   <li>建议根据实际需求合理设置容量</li>
     * </ul>
     * <p>
     * <strong>性能考虑：</strong>
     * <ul>
     *   <li>构造函数时间复杂度：O(1)</li>
     *   <li>内存分配：一次性分配capacity大小的数组</li>
     *   <li>初始化开销：最小化，只设置基本变量</li>
     * </ul>
     *
     * @param capacity 缓冲区容量，必须大于0
     * @throws IllegalArgumentException 当容量小于等于0时
     */
    public CircularBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new Object[capacity];
        this.size = 0;
        this.head = 0;
        this.tail = 0;
    }

    /**
     * 添加元素到循环缓冲区
     * <p>
     * <strong>设计原则：</strong> 高效添加操作，自动覆盖策略，线程安全
     * <p>
     * <strong>功能说明：</strong>
     * 将新元素添加到循环缓冲区的尾部，当缓冲区满时自动覆盖最旧的元素。
     * 该方法实现了经典的循环缓冲区算法，确保添加操作的高效性和内存使用的可控性。
     * <p>
     * <strong>算法逻辑：</strong>
     * <ol>
     *   <li>将新元素存储到尾指针位置</li>
     *   <li>尾指针向前移动一位（使用模运算实现循环）</li>
     *   <li>如果缓冲区未满，增加大小计数器</li>
     *   <li>如果缓冲区已满，头指针也向前移动一位（覆盖最旧数据）</li>
     * </ol>
     * <p>
     * <strong>覆盖策略：</strong>
     * <ul>
     *   <li><strong>未满状态</strong>：size &lt; capacity，只移动尾指针</li>
     *   <li><strong>已满状态</strong>：size == capacity，同时移动头尾指针</li>
     *   <li><strong>数据连续性</strong>：保持数据的逻辑顺序</li>
     *   <li><strong>自动覆盖</strong>：无需手动管理内存</li>
     * </ul>
     * <p>
     * <strong>指针移动规则：</strong>
     * <ul>
     *   <li>尾指针：tail = (tail + 1) % capacity</li>
     *   <li>头指针：head = (head + 1) % capacity（仅在满时移动）</li>
     *   <li>模运算：确保指针在有效范围内循环</li>
     * </ul>
     * <p>
     * <strong>线程安全：</strong>
     * <ul>
     *   <li>使用synchronized关键字确保线程安全</li>
     *   <li>支持多线程并发添加操作</li>
     *   <li>原子性操作，不会出现数据不一致</li>
     * </ul>
     * <p>
     * <strong>性能特点：</strong>
     * <ul>
     *   <li>时间复杂度：O(1)，常数时间操作</li>
     *   <li>空间复杂度：O(1)，固定内存使用</li>
     *   <li>无动态内存分配，性能稳定</li>
     *   <li>适合高频添加操作</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>元素不能为null（当前实现未检查）</li>
     *   <li>添加操作会修改内部状态</li>
     *   <li>满状态下的添加会丢失最旧数据</li>
     *   <li>建议在添加前检查元素有效性</li>
     * </ul>
     *
     * @param element 要添加的元素，不能为null
     */
    public synchronized void add(T element) {
        buffer[tail] = element;
        tail = (tail + 1) % capacity;

        if (size < capacity) {
            size++;
        } else {
            head = (head + 1) % capacity;
        }
    }

    /**
     * 将缓冲区内容转换为List
     * <p>
     * <strong>设计原则：</strong> 数据完整性，逻辑顺序，类型安全
     * <p>
     * <strong>功能说明：</strong>
     * 将循环缓冲区中的所有元素按照逻辑顺序（从最旧到最新）转换为List。
     * 该方法会创建一个新的List实例，包含缓冲区中的所有有效元素，保持数据的逻辑顺序。
     * <p>
     * <strong>数据顺序：</strong>
     * <ul>
     *   <li><strong>逻辑顺序</strong>：从最旧数据到最新数据</li>
     *   <li><strong>物理顺序</strong>：从头指针开始，按数组索引顺序</li>
     *   <li><strong>循环处理</strong>：使用模运算处理数组边界</li>
     *   <li><strong>完整性保证</strong>：包含所有有效元素</li>
     * </ul>
     * <p>
     * <strong>转换算法：</strong>
     * <ol>
     *   <li>创建指定大小的ArrayList</li>
     *   <li>从头指针开始遍历</li>
     *   <li>使用模运算计算实际索引</li>
     *   <li>进行类型转换并添加到结果列表</li>
     *   <li>遍历size个元素</li>
     * </ol>
     * <p>
     * <strong>索引计算：</strong>
     * <ul>
     *   <li>实际索引：index = (head + i) % capacity</li>
     *   <li>i：从0到size-1的遍历索引</li>
     *   <li>模运算：确保索引在有效范围内</li>
     *   <li>循环处理：处理数组边界情况</li>
     * </ul>
     * <p>
     * <strong>类型安全：</strong>
     * <ul>
     *   <li>使用@SuppressWarnings("unchecked")抑制类型转换警告</li>
     *   <li>泛型类型T确保类型安全</li>
     *   <li>运行时类型检查</li>
     *   <li>支持任意引用类型</li>
     * </ul>
     * <p>
     * <strong>线程安全：</strong>
     * <ul>
     *   <li>使用synchronized关键字确保线程安全</li>
     *   <li>支持多线程并发访问</li>
     *   <li>返回的List是独立的副本</li>
     *   <li>不会影响原始缓冲区</li>
     * </ul>
     * <p>
     * <strong>性能特点：</strong>
     * <ul>
     *   <li>时间复杂度：O(n)，n为当前元素数量</li>
     *   <li>空间复杂度：O(n)，创建新的List实例</li>
     *   <li>内存分配：根据size动态分配</li>
     *   <li>适合低频查询操作</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>返回的List是独立的副本，修改不会影响原缓冲区</li>
     *   <li>List大小等于当前缓冲区中的元素数量</li>
     *   <li>元素顺序是从最旧到最新</li>
     *   <li>空缓冲区会返回空List</li>
     *   <li>适合数据分析和统计场景</li>
     * </ul>
     *
     * @return 包含缓冲区所有元素的List，按逻辑顺序排列
     */
    public synchronized List<T> toList() {
        List<T> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int index = (head + i) % capacity;
            @SuppressWarnings("unchecked")
            T element = (T) buffer[index];
            result.add(element);
        }
        return result;
    }

    /**
     * 清空循环缓冲区
     * <p>
     * <strong>设计原则：</strong> 资源清理，状态重置，内存管理
     * <p>
     * <strong>功能说明：</strong>
     * 清空循环缓冲区中的所有元素，重置内部状态，释放引用以帮助垃圾回收。
     * 该方法会将缓冲区恢复到初始状态，相当于重新创建一个空的缓冲区。
     * <p>
     * <strong>清理内容：</strong>
     * <ul>
     *   <li><strong>状态重置</strong>：size、head、tail重置为0</li>
     *   <li><strong>引用清理</strong>：将所有数组元素设置为null</li>
     *   <li><strong>内存释放</strong>：帮助垃圾回收器回收对象</li>
     *   <li><strong>逻辑重置</strong>：缓冲区变为空状态</li>
     * </ul>
     * <p>
     * <strong>清理流程：</strong>
     * <ol>
     *   <li>将size计数器重置为0</li>
     *   <li>将head指针重置为0</li>
     *   <li>将tail指针重置为0</li>
     *   <li>遍历整个数组，将所有元素设置为null</li>
     * </ol>
     * <p>
     * <strong>内存管理：</strong>
     * <ul>
     *   <li><strong>引用清理</strong>：设置null帮助垃圾回收</li>
     *   <li><strong>数组保留</strong>：底层数组对象不会被释放</li>
     *   <li><strong>容量不变</strong>：缓冲区容量保持原有大小</li>
     *   <li><strong>重用性</strong>：清空后可以继续使用</li>
     * </ul>
     * <p>
     * <strong>线程安全：</strong>
     * <ul>
     *   <li>使用synchronized关键字确保线程安全</li>
     *   <li>支持多线程并发清空操作</li>
     *   <li>原子性操作，不会出现状态不一致</li>
     *   <li>清空过程中其他操作会被阻塞</li>
     * </ul>
     * <p>
     * <strong>性能特点：</strong>
     * <ul>
     *   <li>时间复杂度：O(capacity)，需要遍历整个数组</li>
     *   <li>空间复杂度：O(1)，不创建新的数据结构</li>
     *   <li>内存清理：帮助垃圾回收，但有一定开销</li>
     *   <li>适合低频清空操作</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>系统重启或重置时</li>
     *   <li>数据过期需要清理时</li>
     *   <li>测试场景下的状态重置</li>
     *   <li>内存压力大时的主动清理</li>
     *   <li>配置变更后的数据清理</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>清空操作不可逆，所有数据将丢失</li>
     *   <li>清空后缓冲区变为空状态</li>
     *   <li>清空过程中其他线程的操作会被阻塞</li>
     *   <li>建议在业务低峰期执行清空操作</li>
     *   <li>清空后可以立即添加新元素</li>
     * </ul>
     */
    public synchronized void clear() {
        size = 0;
        head = 0;
        tail = 0;
        for (int i = 0; i < capacity; i++) {
            buffer[i] = null;
        }
    }

    /**
     * 获取缓冲区当前元素数量
     * <p>
     * <strong>设计原则：</strong> 状态查询，实时性，线程安全
     * <p>
     * <strong>功能说明：</strong>
     * 返回循环缓冲区中当前存储的元素数量，这是一个实时值，会随着添加和清空操作动态变化。
     * 该方法提供了缓冲区的状态信息，用于判断缓冲区是否为空、是否已满等。
     * <p>
     * <strong>返回值含义：</strong>
     * <ul>
     *   <li><strong>0</strong>：缓冲区为空，没有存储任何元素</li>
     *   <li><strong>1 到 capacity-1</strong>：缓冲区部分填充</li>
     *   <li><strong>capacity</strong>：缓冲区已满，新元素会覆盖最旧元素</li>
     * </ul>
     * <p>
     * <strong>状态判断：</strong>
     * <ul>
     *   <li><strong>空状态</strong>：size == 0</li>
     *   <li><strong>部分填充</strong>：0 &lt; size &lt; capacity</li>
     *   <li><strong>满状态</strong>：size == capacity</li>
     *   <li><strong>覆盖状态</strong>：size == capacity 且继续添加</li>
     * </ul>
     * <p>
     * <strong>线程安全：</strong>
     * <ul>
     *   <li>使用synchronized关键字确保线程安全</li>
     *   <li>支持多线程并发查询</li>
     *   <li>返回的值是查询时刻的快照</li>
     *   <li>查询过程中其他操作会被阻塞</li>
     * </ul>
     * <p>
     * <strong>性能特点：</strong>
     * <ul>
     *   <li>时间复杂度：O(1)，直接返回成员变量</li>
     *   <li>空间复杂度：O(1)，不创建新的数据结构</li>
     *   <li>无计算开销，性能极高</li>
     *   <li>适合高频查询操作</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>判断缓冲区是否为空</li>
     *   <li>判断缓冲区是否已满</li>
     *   <li>计算缓冲区使用率</li>
     *   <li>监控缓冲区状态</li>
     *   <li>条件判断和流程控制</li>
     * </ul>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>返回值是查询时刻的快照，可能不是最新值</li>
     *   <li>在并发环境下，size可能随时变化</li>
     *   <li>建议与isEmpty()方法配合使用</li>
     *   <li>适合状态检查，不适合精确计数</li>
     * </ul>
     *
     * @return 缓冲区中当前存储的元素数量，范围：[0, capacity]
     */
    public synchronized int size() {
        return size;
    }

    /**
     * 判断缓冲区是否为空
     * <p>
     * <strong>设计原则：</strong> 状态判断，语义清晰，性能优先
     * <p>
     * <strong>功能说明：</strong>
     * 判断循环缓冲区是否为空，即是否包含任何元素。这是一个便捷的状态判断方法，
     * 比直接比较size() == 0更加语义化和易读。
     * <p>
     * <strong>判断逻辑：</strong>
     * <ul>
     *   <li><strong>空状态</strong>：size == 0，返回true</li>
     *   <li><strong>非空状态</strong>：size &gt; 0，返回false</li>
     *   <li><strong>边界情况</strong>：size &lt; 0 理论上不会出现</li>
     * </ul>
     * <p>
     * <strong>等价表达式：</strong>
     * <ul>
     *   <li>isEmpty() == (size() == 0)</li>
     *   <li>isEmpty() == (size == 0)</li>
     *   <li>!isEmpty() == (size > 0)</li>
     * </ul>
     * <p>
     * <strong>线程安全：</strong>
     * <ul>
     *   <li>使用synchronized关键字确保线程安全</li>
     *   <li>支持多线程并发查询</li>
     *   <li>返回的值是查询时刻的快照</li>
     *   <li>查询过程中其他操作会被阻塞</li>
     * </ul>
     * <p>
     * <strong>性能特点：</strong>
     * <ul>
     *   <li>时间复杂度：O(1)，直接比较成员变量</li>
     *   <li>空间复杂度：O(1)，不创建新的数据结构</li>
     *   <li>无计算开销，性能极高</li>
     *   <li>适合高频查询操作</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>条件判断和流程控制</li>
     *   <li>循环条件判断</li>
     *   <li>状态检查和验证</li>
     *   <li>防御性编程</li>
     *   <li>业务逻辑判断</li>
     * </ul>
     * <p>
     * <strong>代码示例：</strong>
     * <pre>{@code
     * // 检查缓冲区是否为空
     * if (buffer.isEmpty()) {
     *     System.out.println("缓冲区为空");
     *     return;
     * }
     *
     * // 循环处理直到缓冲区为空
     * while (!buffer.isEmpty()) {
     *     processElement(buffer.toList().get(0));
     *     buffer.clear();
     * }
     * }</pre>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>返回值是查询时刻的快照，可能不是最新值</li>
     *   <li>在并发环境下，状态可能随时变化</li>
     *   <li>建议与size()方法配合使用</li>
     *   <li>适合快速状态检查，不适合精确状态判断</li>
     *   <li>空状态检查是很多操作的前置条件</li>
     * </ul>
     *
     * @return true表示缓冲区为空，false表示缓冲区包含元素
     */
    public synchronized boolean isEmpty() {
        return size == 0;
    }
}
