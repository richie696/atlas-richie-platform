package cn.richie696.component.observability.testkit;

import cn.richie696.component.observability.core.CorrelationIds;
import cn.richie696.component.observability.core.ObservabilityState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservabilityTestkitTest {

    @Test
    void validatesIndependentCorrelationIds() {
        assertThatCode(() -> ObservabilityTestkit.assertIndependentIds(
                new CorrelationIds("trace", "span", "request"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> ObservabilityTestkit.assertIndependentIds(
                new CorrelationIds("trace", "span", "trace")))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void validatesSwitchContract() {
        assertThatCode(() -> ObservabilityTestkit.assertEnabled(ObservabilityState.enabledState()))
                .doesNotThrowAnyException();
        assertThatCode(() -> ObservabilityTestkit.assertDisabled(ObservabilityState.disabledState()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNullAndInconsistentStates() {
        assertThatThrownBy(() -> ObservabilityTestkit.assertEnabled(null))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> ObservabilityTestkit.assertDisabled(null))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> ObservabilityTestkit.assertEnabled(ObservabilityState.disabledState()))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> ObservabilityTestkit.assertDisabled(ObservabilityState.enabledState()))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> ObservabilityTestkit.assertIndependentIds(null))
                .isInstanceOf(AssertionError.class);
    }
}
