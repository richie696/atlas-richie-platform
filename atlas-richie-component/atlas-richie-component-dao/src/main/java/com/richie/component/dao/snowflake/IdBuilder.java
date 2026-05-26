package com.richie.component.dao.snowflake;

import com.richie.context.utils.data.Collections;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局分布式ID生成器
 * <p style="color: green">（请注意：本分布式ID生成器需要依赖Redis中间件）
 *
 * @author 于跃
 * @version 1.0
 * @since 2023-09-17 23:36:01
 */
public class IdBuilder {

    /**
     * Start time cut (2020-05-03)
     */
    private final long twepoch = 1588435200000L;

    /**
     * The number of bits occupied by workerId
     */
    private final int workerIdBits = 10;

    /**
     * The number of bits occupied by timestamp
     */
    private final int timestampBits = 41;

    /**
     * The number of bits occupied by sequence
     */
    private final int sequenceBits = 12;

    /**
     * Maximum supported machine id, the result is 1023
     */
    private final int maxWorkerId = ~(-1 << workerIdBits);

    /**
     * business meaning: machine ID (0 ~ 1023)
     * actual layout in memory:
     * highest 1 bit: 0
     * middle 10 bit: workerId
     * lowest 53 bit: all 0
     */
    private long workerId;

    /**
     * timestamp and sequence mix in one Long
     * highest 11 bit: not used
     * middle  41 bit: timestamp
     * lowest  12 bit: sequence
     */
    private AtomicLong timestampAndSequence;

    /**
     * mask that help to extract timestamp and sequence from a long
     */
    private final long timestampAndSequenceMask = ~(-1L << (timestampBits + sequenceBits));

    /**
     * instantiate an IDBuilder using given workerId
     */
    public IdBuilder() {
        initTimestampAndSequence();
        initWorkerId(null);
    }

    /**
     * instantiate an IdBuilder using given workerId
     *
     * @param workerId if null, then will auto assign one
     */
    public IdBuilder(Long workerId) {
        initTimestampAndSequence();
        initWorkerId(workerId);
    }

    /**
     * init first timestamp and sequence immediately
     */
    private void initTimestampAndSequence() {
        var timestamp = getNewestTimestamp();
        var timestampWithSequence = timestamp << sequenceBits;
        this.timestampAndSequence = new AtomicLong(timestampWithSequence);
    }

    /**
     * init workerId
     *
     * @param workerId if null, then auto generate one
     */
    private void initWorkerId(Long workerId) {
        if (workerId == null) {
            workerId = generateWorkerId();
        }
        if (workerId > maxWorkerId || workerId < 0) {
            var message = String.format("worker Id can't be greater than %d or less than 0", maxWorkerId);
            throw new IllegalArgumentException(message);
        }
        this.workerId = workerId << (timestampBits + sequenceBits);
    }

    /**
     * 获取下一个分布式ID（基于雪花算法），看起来像：
     * 最高 1 位：始终为 0
     * 接下来的 10 位：workerId
     * 下一个 41 位：时间戳
     * 最低 12 位：序列
     *
     * @return 返回分布式ID值
     */
    public long nextId() {
        waitIfNecessary();
        var next = timestampAndSequence.incrementAndGet();
        var timestampWithSequence = next & timestampAndSequenceMask;
        return workerId | timestampWithSequence;
    }

    /**
     * block current thread if the QPS of acquiring UUID is too high
     * that current sequence space is exhausted
     */
    private void waitIfNecessary() {
        var currentWithSequence = timestampAndSequence.get();
        var current = currentWithSequence >>> sequenceBits;
        var newest = getNewestTimestamp();
        if (current >= newest) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException ignore) {
                // don't care
            }
        }
    }

    /**
     * 获取相对于 twepoch 的最新时间戳（毫秒）
     *
     * @return 相对时间戳
     */
    private long getNewestTimestamp() {
        return System.currentTimeMillis() - twepoch;
    }

    /**
     * auto generate workerId, try using mac first, if failed, then randomly generate one
     *
     * @return workerId
     */
    private long generateWorkerId() {
        try {
            return generateWorkerIdBaseOnMac();
        } catch (Exception e) {
            return generateRandomWorkerId();
        }
    }

    /**
     * use lowest 10 bit of available MAC as workerId
     *
     * @return workerId
     * @throws Exception when there is no available mac found
     */
    private long generateWorkerIdBaseOnMac() throws Exception {
        NetworkInterface networkInterface = Collections.streamOf(NetworkInterface.getNetworkInterfaces())
                .filter(network -> {
                    try {
                        return !network.isLoopback() && !network.isVirtual();
                    } catch (SocketException e) {
                        return false;
                    }
                }).findFirst().orElse(null);
        if (networkInterface == null) {
            throw new RuntimeException("no available mac found");
        }
        byte[] mac = networkInterface.getHardwareAddress();
        return ((mac[4] & 0B11) << 8) | (mac[5] & 0xFF);
    }

    /**
     * randomly generate one as workerId
     *
     * @return workerId
     */
    private long generateRandomWorkerId() {
        return new Random().nextInt(maxWorkerId + 1);
    }
}
