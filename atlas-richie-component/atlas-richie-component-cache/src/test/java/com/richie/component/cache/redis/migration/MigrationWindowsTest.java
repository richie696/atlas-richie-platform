package com.richie.component.cache.redis.migration;

import com.richie.component.cache.redis.config.base.AtlasRedisProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MigrationWindows} 与 {@link MigrationWindowValidator} 的单测，
 * 覆盖三个状态：<b>在期内</b> / <b>过期未修</b> / <b>过期已修</b>。
 *
 * @author Mavis (on behalf of richie696)
 * @since 1.0.0
 */
class MigrationWindowsTest {

    private static final LocalDate WINDOW_DEADLINE = LocalDate.parse("2026-12-01");

    /**
     * 注入固定"今天"以保证测试可重复。
     *
     * @param today 测试中模拟的"今天"
     * @return 返回固定日期的 supplier
     */
    private static Supplier<LocalDate> clock(LocalDate today) {
        return () -> today;
    }

    private AtlasRedisProperties.RedisPerf newPerf() {
        return new AtlasRedisProperties.RedisPerf();
    }

    @Test
    @DisplayName("在期内（deadline 之后但今天还在之前）→ 无违规，验证器放行")
    void withinWindowPasses() {
        AtlasRedisProperties.RedisPerf perf = newPerf();
        // 默认 3 个 @MigrationWindow 字段都为 false，今天是 deadline 前
        LocalDate today = WINDOW_DEADLINE.minusDays(1);

        List<MigrationViolation> violations = MigrationWindows.check(perf, clock(today));

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("当天等于 deadline → 仍视为在期内（isAfter 严格大于）")
    void onDeadlinePasses() {
        AtlasRedisProperties.RedisPerf perf = newPerf();

        List<MigrationViolation> violations = MigrationWindows.check(perf, clock(WINDOW_DEADLINE));

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("过期且 3 个字段全为 false → 3 条违规")
    void expiredThreeFieldsFalseThreeViolations() {
        AtlasRedisProperties.RedisPerf perf = newPerf();
        LocalDate today = WINDOW_DEADLINE.plusDays(1);

        List<MigrationViolation> violations = MigrationWindows.check(perf, clock(today));

        assertThat(violations)
                .hasSize(3)
                .extracting(v -> v.field().getName())
                .containsExactlyInAnyOrder(
                        "enabled",
                        "blockForbiddenTiers",
                        "blockStringPayloadViolations");
    }

    @Test
    @DisplayName("过期但业务已迁移（3 个字段全 true）→ 无违规，验证器放行")
    void expiredAllMigratedPasses() {
        AtlasRedisProperties.RedisPerf perf = newPerf();
        perf.setEnabled(true);
        perf.setBlockForbiddenTiers(true);
        perf.setBlockStringPayloadViolations(true);
        LocalDate today = WINDOW_DEADLINE.plusDays(365);

        List<MigrationViolation> violations = MigrationWindows.check(perf, clock(today));

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("过期且仅迁移部分字段 → 只报未迁移的")
    void expiredPartialMigrationReportsOnlyPending() {
        AtlasRedisProperties.RedisPerf perf = newPerf();
        perf.setEnabled(true);
        // blockForbiddenTiers 仍 false
        // blockStringPayloadViolations 仍 false
        LocalDate today = WINDOW_DEADLINE.plusDays(1);

        List<MigrationViolation> violations = MigrationWindows.check(perf, clock(today));

        assertThat(violations)
                .hasSize(2)
                .extracting(v -> v.field().getName())
                .containsExactlyInAnyOrder(
                        "blockForbiddenTiers",
                        "blockStringPayloadViolations");
    }

    @Test
    @DisplayName("Spring 验证器：过期未迁移 → 启动失败（IllegalStateException）")
    void validatorExpiredThrowsAtBoot() {
        AtlasRedisProperties.RedisPerf perf = newPerf();
        AtlasRedisProperties props = new AtlasRedisProperties();
        props.setPerf(perf);
        MigrationWindowValidator validator = new MigrationWindowValidator(props);

        assertThatThrownBy(() -> validator.runValidation(clock(WINDOW_DEADLINE.plusDays(1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Migration windows expired")
                .hasMessageContaining("enabled")
                .hasMessageContaining("blockForbiddenTiers")
                .hasMessageContaining("blockStringPayloadViolations");
    }

    @Test
    @DisplayName("Spring 验证器：期内 / 已迁移 → 启动放行")
    void validatorHealthyPassesSilently() {
        AtlasRedisProperties props = new AtlasRedisProperties();
        // 默认字段全 false，但在期内，应放行
        MigrationWindowValidator validator = new MigrationWindowValidator(props);

        assertThatCode(() -> validator.runValidation(clock(WINDOW_DEADLINE.minusDays(1))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("违规描述包含 owner、until、reason、removedIn 等关键信息")
    void violationDescribeCarriesFullContext() {
        AtlasRedisProperties.RedisPerf perf = newPerf();
        LocalDate today = WINDOW_DEADLINE.plusDays(1);

        List<MigrationViolation> violations = MigrationWindows.check(perf, clock(today));

        assertThat(violations).hasSize(3);
        for (MigrationViolation v : violations) {
            String desc = v.describe();
            assertThat(desc)
                    .contains("RedisPerf")
                    .contains(v.field().getName())
                    .contains("owner=richie696")
                    .contains("until=" + WINDOW_DEADLINE)
                    .contains("now=" + today)
                    .contains("removedIn=2.0.0")
                    .contains("value=false");
        }
    }
}
