/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap.refresh;

import cn.richie696.component.secret.api.SecretSnapshotChangedEvent;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Secret 组件与业务组件运行期状态之间的内部两阶段刷新契约。
 *
 * <p>参与者只从已经临时安装的 Spring Environment 绑定自己的公开配置，不接收 Secret
 * Bundle、Provider 或物理密钥信息。</p>
 */
@FunctionalInterface
public interface SecretRefreshParticipant {

    PreparedSecretRefresh prepare(
            ConfigurableEnvironment environment,
            SecretSnapshotChangedEvent candidate);
}
