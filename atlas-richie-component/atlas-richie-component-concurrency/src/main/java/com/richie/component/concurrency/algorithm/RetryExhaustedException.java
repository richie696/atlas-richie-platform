package com.richie.component.concurrency.algorithm;

import java.io.Serial;

/**
 * 重试耗尽异常 —— 当 {@link Retryer} 的所有重试次数用尽、或重试循环被线程中断时抛出。
 *
 * <p>作为 {@link RuntimeException} 的子类，调用方既可显式捕获并做降级处理，
 * 也可让其自然向上传播。异常的 {@link #getCause()} 返回最后一次失败的原始异常，
 * 可用于获取完整的错误堆栈信息以辅助排障。</p>
 *
 * <h3>典型使用</h3>
 * <pre>{@code
 * try {
 *     String result = Retryer.of(Duration.ofMillis(100))
 *             .maxAttempts(3)
 *             .execute(() -> callRemoteService());
 * } catch (RetryExhaustedException e) {
 *     Throwable cause = e.getCause();  // 最后一次失败的原始异常
 *     log.warn("Retry exhausted, last error: {}", cause.getMessage(), e);
 *     throw new BusinessException("remote call failed", e);
 * }
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 */
public final class RetryExhaustedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造一个包含详细消息和原因的重试耗尽异常。
     *
     * @param message 详细消息
     * @param cause   原因（最后一次失败的原始异常），可以为 {@code null}
     */
    public RetryExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}