package com.richie.component.vector.config.support;

public final class VectorIntegrationTestSupport {

    private VectorIntegrationTestSupport() {
    }

    /** JUnit {@code @EnabledIf} 入口（配置绑定 IT 无需 Docker）。 */
    public static boolean integrationTestsEnabled() {
        return true;
    }
}
