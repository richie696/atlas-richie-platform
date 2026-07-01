package com.richie.component.storage.observability;

import com.richie.component.storage.core.StorageEngine;
import com.richie.component.storage.core.StorageEngineRegistry;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.context.common.api.SpringContextHolder;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Field;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class StorageMetricsBinderTest {

    private AnnotationConfigApplicationContext ctx;
    private StorageEngineRegistry registry;
    private SimpleMeterRegistry meterRegistry;
    private StorageMetricsBinder binder;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext();
        ctx.refresh();
        try {
            Field f = SpringContextHolder.class.getDeclaredField("applicationContext");
            f.setAccessible(true);
            f.set(null, ctx);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        registry = new StorageEngineRegistry();
        meterRegistry = new SimpleMeterRegistry();
        binder = new StorageMetricsBinder(registry);
        binder.bindTo(meterRegistry);
    }

    @AfterEach
    void tearDown() {
        if (ctx != null) ctx.close();
        try {
            Field f = SpringContextHolder.class.getDeclaredField("applicationContext");
            f.setAccessible(true);
            f.set(null, null);
        } catch (ReflectiveOperationException ignored) {}
        meterRegistry.close();
    }

    @Test
    void bindTo_shouldRegisterGaugesForAllEnumTypes() {
        Collection<Gauge> switchGauges = meterRegistry.find("storage.engine.switch.total").gauges();
        Collection<Gauge> registerGauges = meterRegistry.find("storage.engine.register.total").gauges();
        assertThat(switchGauges).hasSize(StorageEngineEnum.values().length);
        assertThat(registerGauges).hasSize(StorageEngineEnum.values().length);
    }

    @Test
    void bindTo_shouldRegisterDefaultTypeAndRegisteredCountGauges() {
        assertThat(meterRegistry.find("storage.engine.default.type").gauges()).hasSize(1);
        assertThat(meterRegistry.find("storage.engine.registered.count").gauges()).hasSize(1);
    }

    @Test
    void registerInitialEngine_shouldBumpRegisterCounterForType() {
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "minio-1", new NoopEngine());
        Gauge minioRegister = meterRegistry.find("storage.engine.register.total")
                .tag("engine", "MINIO").gauge();
        assertThat(minioRegister.value()).isEqualTo(1.0);
    }

    @Test
    void switchEngine_shouldBumpSwitchCounterForType() {
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "minio-1", new NoopEngine());
        registry.getMetrics().incrementSwitch(StorageEngineEnum.MINIO);
        Gauge minioSwitch = meterRegistry.find("storage.engine.switch.total")
                .tag("engine", "MINIO").gauge();
        assertThat(minioSwitch.value()).isEqualTo(1.0);
    }

    @Test
    void registeredCountGauge_shouldReflectSnapshotSize() {
        Gauge gauge = meterRegistry.find("storage.engine.registered.count").gauge();
        assertThat(gauge.value()).isZero();
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "minio", new NoopEngine());
        registry.registerInitialEngine(StorageEngineEnum.FTP, "ftp", new NoopEngine());
        assertThat(gauge.value()).isEqualTo(2.0);
    }

    static class NoopEngine implements StorageEngine {
        @Override public com.richie.component.storage.bean.UploadResponse putData(String k, java.util.Map<?, ?> c) { return null; }
        @Override public com.richie.component.storage.bean.UploadResponse putData(String k, java.util.Collection<?> c) { return null; }
        @Override public com.richie.component.storage.bean.UploadResponse putData(String k, Object o) { return null; }
        @Override public com.richie.component.storage.bean.UploadResponse putObject(String k, java.io.File f) { return null; }
        @Override public com.richie.component.storage.bean.UploadResponse putObject(String k, java.io.InputStream i) { return null; }
        @Override public com.richie.component.storage.bean.UploadResponse putImage(String k, java.io.File f, com.richie.component.storage.bean.image.ImageOptions o) { return null; }
        @Override public com.richie.component.storage.bean.UploadResponse putImage(String k, java.io.InputStream i, com.richie.component.storage.bean.image.ImageOptions o) { return null; }
        @Override public <T> com.richie.component.storage.bean.DownloadResponse<T> getData(String k, tools.jackson.core.type.TypeReference<T> t) { return null; }
        @Override public com.richie.component.storage.bean.DownloadResponse<byte[]> getObject(String k, java.io.File p, boolean r) { return null; }
        @Override public com.richie.component.storage.bean.DownloadResponse<byte[]> getResumableObject(String k, String t, boolean r) { return null; }
        @Override public boolean existsObject(String k) { return true; }
    }
}
