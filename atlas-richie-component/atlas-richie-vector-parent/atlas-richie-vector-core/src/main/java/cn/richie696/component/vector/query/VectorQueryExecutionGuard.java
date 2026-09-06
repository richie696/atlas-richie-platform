/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Per-Store concurrency and caller-visible timeout guard for optional advanced operations. */
public final class VectorQueryExecutionGuard {

    public static final int DEFAULT_MAX_CONCURRENT = 16;
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "atlas-vector-advanced-" + THREAD_SEQUENCE.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private final Semaphore permits;

    public VectorQueryExecutionGuard() {
        this(DEFAULT_MAX_CONCURRENT);
    }

    public VectorQueryExecutionGuard(int maxConcurrent) {
        if (maxConcurrent < 1 || maxConcurrent > 1_024) {
            throw new IllegalArgumentException("maxConcurrent must be within [1,1024]");
        }
        this.permits = new Semaphore(maxConcurrent);
    }

    public <T> T execute(Duration timeout, Supplier<T> action) {
        if (!permits.tryAcquire()) {
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.CONCURRENCY_LIMIT, "advanced vector query concurrency limit reached");
        }
        FutureTask<T> task = new FutureTask<>(action::get) {
            @Override
            protected void done() {
                permits.release();
            }
        };
        try {
            EXECUTOR.execute(task);
        } catch (RejectedExecutionException error) {
            task.cancel(false);
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.CONCURRENCY_LIMIT, "advanced vector query executor is unavailable");
        }
        try {
            return task.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException error) {
            task.cancel(true);
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.TIMEOUT, "advanced vector query timed out");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            task.cancel(true);
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.TIMEOUT, "advanced vector query was interrupted");
        } catch (ExecutionException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("advanced vector query failed", error.getCause());
        }
    }
}
