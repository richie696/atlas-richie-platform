package com.richie.component.web.core.protection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptTrackerTest {

    @Test
    void isLocked_unknownKey_returnsFalse() {
        LoginAttemptTracker t = new LoginAttemptTracker(60, 5, 900);
        assertThat(t.isLocked("nobody")).isFalse();
    }

    @Test
    void recordFailure_belowThreshold_notLocked() {
        LoginAttemptTracker t = new LoginAttemptTracker(60, 5, 900);
        for (int i = 0; i < 4; i++) {
            assertThat(t.recordFailure("k")).isEqualTo(i + 1);
        }
        assertThat(t.isLocked("k")).isFalse();
    }

    @Test
    void recordFailure_hitsThreshold_locksKey() {
        LoginAttemptTracker t = new LoginAttemptTracker(60, 5, 900);
        for (int i = 0; i < 5; i++) {
            t.recordFailure("k");
        }
        assertThat(t.isLocked("k")).isTrue();
    }

    @Test
    void recordFailure_exceedsThreshold_stillLocked() {
        LoginAttemptTracker t = new LoginAttemptTracker(60, 3, 900);
        for (int i = 0; i < 10; i++) {
            t.recordFailure("k");
        }
        assertThat(t.isLocked("k")).isTrue();
    }

    @Test
    void recordSuccess_clearsKey() {
        LoginAttemptTracker t = new LoginAttemptTracker(60, 3, 900);
        t.recordFailure("k");
        t.recordFailure("k");
        t.recordFailure("k");
        assertThat(t.isLocked("k")).isTrue();
        t.recordSuccess("k");
        assertThat(t.isLocked("k")).isFalse();
        assertThat(t.recordFailure("k")).isEqualTo(1);
    }

    @Test
    void recordFailure_oldEntriesEvicted() throws Exception {
        LoginAttemptTracker t = new LoginAttemptTracker(1, 3, 900);
        t.recordFailure("k");
        t.recordFailure("k");
        Thread.sleep(1100);
        assertThat(t.recordFailure("k")).isEqualTo(1);
    }

    @Test
    void differentKeys_areIndependent() {
        LoginAttemptTracker t = new LoginAttemptTracker(60, 3, 900);
        t.recordFailure("k1");
        t.recordFailure("k1");
        t.recordFailure("k1");
        assertThat(t.isLocked("k1")).isTrue();
        assertThat(t.isLocked("k2")).isFalse();
    }

    @Test
    void size_tracksDistinctKeys() {
        LoginAttemptTracker t = new LoginAttemptTracker(60, 5, 900);
        assertThat(t.size()).isZero();
        t.recordFailure("k1");
        t.recordFailure("k2");
        assertThat(t.size()).isEqualTo(2);
        t.recordFailure("k1");
        assertThat(t.size()).isEqualTo(2);
    }

    @Test
    void lockoutExpires_afterLockoutSeconds() throws Exception {
        LoginAttemptTracker t = new LoginAttemptTracker(60, 3, 1);
        t.recordFailure("k");
        t.recordFailure("k");
        t.recordFailure("k");
        assertThat(t.isLocked("k")).isTrue();
        Thread.sleep(1100);
        assertThat(t.isLocked("k")).isFalse();
    }

    @Test
    void constructor_rejectsInvalidArgs() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new LoginAttemptTracker(0, 5, 900))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new LoginAttemptTracker(60, 0, 900))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new LoginAttemptTracker(60, 5, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}