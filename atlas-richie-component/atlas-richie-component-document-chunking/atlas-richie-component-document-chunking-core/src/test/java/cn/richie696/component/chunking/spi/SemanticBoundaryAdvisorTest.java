/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.chunking.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SemanticBoundaryAdvisor SPI smoke check")
class SemanticBoundaryAdvisorTest {

    @Test
    @DisplayName("functional interface accepts a lambda producing a list of offsets")
    void semanticBoundaryAdvisor_lambda_returnsConfiguredOffsets() {
        SemanticBoundaryAdvisor advisor = content -> List.of(0, 3, 7);

        List<Integer> boundaries = advisor.boundaries("any content here");

        assertThat(boundaries).containsExactly(0, 3, 7);
    }

    @Test
    @DisplayName("functional interface accepts an empty result")
    void semanticBoundaryAdvisor_lambda_canReturnEmptyList() {
        SemanticBoundaryAdvisor advisor = content -> List.of();

        assertThat(advisor.boundaries("nothing matters")).isEmpty();
    }
}