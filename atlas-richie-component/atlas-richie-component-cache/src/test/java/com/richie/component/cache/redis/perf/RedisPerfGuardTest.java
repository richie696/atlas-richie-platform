package com.richie.component.cache.redis.perf;

import com.richie.component.cache.redis.config.base.AtlasRedisProperties;
import com.richie.component.cache.support.OpsTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisPerfGuardTest {

    @Test
    void execute_whenDisabled_passesThrough() {
        AtlasRedisProperties props = new AtlasRedisProperties();
        props.getPerf().setEnabled(false);
        RedisPerfGuard guard = new RedisPerfGuard(props);

        String result = guard.execute("M", "m", RedisComplexityTier.LINEAR_N, () -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void execute_whenBlockForbiddenTiers_blocksLinearN() {
        AtlasRedisProperties props = new AtlasRedisProperties();
        AtlasRedisProperties.RedisPerf perf = OpsTestSupport.enabledPerf();
        perf.setBlockForbiddenTiers(true);
        props.setPerf(perf);
        RedisPerfGuard guard = new RedisPerfGuard(props);

        assertThatThrownBy(() -> guard.execute("M", "m", RedisComplexityTier.LINEAR_N, () -> "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blocked by policy");
    }

    @Test
    void execute_whenWhitelistRejectsTier_blocksWhenConfigured() {
        AtlasRedisProperties props = new AtlasRedisProperties();
        AtlasRedisProperties.RedisPerf perf = OpsTestSupport.enabledPerf();
        perf.setBlockForbiddenTiers(true);
        perf.setTocAllowedComplexities(List.of("O1"));
        props.setPerf(perf);
        RedisPerfGuard guard = new RedisPerfGuard(props);

        assertThatThrownBy(() -> guard.execute("M", "m", RedisComplexityTier.LOG_N, () -> "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tier not in tocAllowedComplexities");
    }

    @Test
    void checkStringWritePayload_whenBlockViolations_throws() {
        AtlasRedisProperties props = new AtlasRedisProperties();
        AtlasRedisProperties.RedisPerf perf = OpsTestSupport.enabledPerf();
        perf.setBlockStringPayloadViolations(true);
        props.setPerf(perf);
        RedisPerfGuard guard = new RedisPerfGuard(props);

        assertThatThrownBy(() -> guard.checkStringWritePayload("M", "m", "k", List.of("bad")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("String value anti-pattern");
    }

    @Test
    void execute_withCommandMeta_runsSupplier() {
        AtlasRedisProperties props = new AtlasRedisProperties();
        props.setPerf(OpsTestSupport.enabledPerf());
        RedisPerfGuard guard = new RedisPerfGuard(props);

        String result = guard.execute("M", "entries", RedisOperationCatalog.HASH_FULL, () -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void checkStringWritePayload_whenDisabled_isNoOp() {
        AtlasRedisProperties props = new AtlasRedisProperties();
        props.getPerf().setEnabled(false);
        RedisPerfGuard guard = new RedisPerfGuard(props);

        guard.checkStringWritePayload("M", "m", "k", List.of("bad"));
    }

    @Test
    void checkStringWritePayload_whenWarnOnly_logsWithoutThrow() {
        AtlasRedisProperties props = new AtlasRedisProperties();
        props.setPerf(OpsTestSupport.enabledPerf());
        RedisPerfGuard guard = new RedisPerfGuard(props);

        guard.checkStringWritePayload("M", "m", "k", Map.of("k", "v"));
    }

    @Test
    void execute_voidRunnable_delegates() {
        AtlasRedisProperties props = new AtlasRedisProperties();
        props.getPerf().setEnabled(false);
        RedisPerfGuard guard = new RedisPerfGuard(props);

        guard.execute("M", "m", RedisComplexityTier.O1, () -> { });
    }

    @Test
    void execute_withMetaAndBigKeyHints_runsSupplier() {
        AtlasRedisProperties props = new AtlasRedisProperties();
        AtlasRedisProperties.RedisPerf perf = OpsTestSupport.enabledPerf();
        perf.setLogBigKeyProbeHints(true);
        props.setPerf(perf);
        RedisPerfGuard guard = new RedisPerfGuard(props);

        String result = guard.execute("M", "entries", RedisOperationCatalog.HASH_FULL, () -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void execute_whenWarnNonO1_allowsLogN() {
        AtlasRedisProperties props = new AtlasRedisProperties();
        props.setPerf(OpsTestSupport.enabledPerf());
        RedisPerfGuard guard = new RedisPerfGuard(props);

        assertThat(guard.execute("M", "m", RedisComplexityTier.LOG_N, () -> "ok")).isEqualTo("ok");
    }
}
