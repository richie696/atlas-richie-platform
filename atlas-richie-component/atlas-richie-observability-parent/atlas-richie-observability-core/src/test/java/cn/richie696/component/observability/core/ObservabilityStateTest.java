package cn.richie696.component.observability.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityStateTest {

    @Test
    void enabledRequiresApplicationSwitchAndSdkToBeEnabled() {
        assertThat(new ObservabilityState(true, false).enabled()).isTrue();
        assertThat(new ObservabilityState(false, false).enabled()).isFalse();
        assertThat(new ObservabilityState(true, true).enabled()).isFalse();
    }

    @Test
    void disabledFactoryIsExplicitlyDisabled() {
        assertThat(ObservabilityState.disabledState().disabled()).isTrue();
        assertThat(ObservabilityState.disabledState().configuredEnabled()).isFalse();
        assertThat(ObservabilityState.disabledState().sdkDisabled()).isTrue();
    }

    @Test
    void stateHasValueSemanticsAndUsefulDiagnostics() {
        ObservabilityState enabled = ObservabilityState.enabledState();
        ObservabilityState same = new ObservabilityState(true, false);
        assertThat(enabled).isEqualTo(same);
        assertThat(enabled).hasSameHashCodeAs(same);
        assertThat(enabled.toString()).contains("enabled=true");
        assertThat(enabled).isNotEqualTo(ObservabilityState.disabledState());
        assertThat(enabled).isNotEqualTo("enabled");
    }
}
