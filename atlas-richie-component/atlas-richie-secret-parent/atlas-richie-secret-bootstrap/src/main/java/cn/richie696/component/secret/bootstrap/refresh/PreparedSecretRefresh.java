/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap.refresh;

/**
 * 业务组件为一次 Secret 快照切换准备的可回滚变更。
 *
 * <p>{@link #commit()} 只能发布已经完整构建并校验过的不可变候选状态，不得执行网络
 * I/O；资源释放放到 {@link #complete()}，确保其它参与者提交失败时仍可回滚。</p>
 */
public interface PreparedSecretRefresh {

    void commit();

    void rollback();

    default void complete() {
    }
}
