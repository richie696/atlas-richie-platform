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
package cn.richie696.component.vector.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorContentTest {

    @Test
    void textContent_exposesAccessorsAndModality() {
        VectorContent.TextContent text = new VectorContent.TextContent("hi", "text/markdown");

        assertThat(text.modality()).isEqualTo(Modality.TEXT);
        assertThat(text.text()).isEqualTo("hi");
        assertThat(text.mimeType()).isEqualTo("text/markdown");
    }

    @Test
    void textContent_defaultsMimeTypeWhenBlank() {
        assertThat(new VectorContent.TextContent("hi", "").mimeType()).isEqualTo("text/plain");
    }

    @Test
    void textContent_rejectsBlankText() {
        assertThatThrownBy(() -> new VectorContent.TextContent(null, "text/plain"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TextContent.text");
    }

    @Test
    void sealedContent_allowsTextAndImage() {
        VectorContent[] samples = {
                new VectorContent.TextContent("a", "text/plain"),
                new VectorContent.ImageContent(new byte[]{1}, "image/png")
        };

        assertThat(samples[0]).isInstanceOf(VectorContent.TextContent.class);
        assertThat(samples[1]).isInstanceOf(VectorContent.ImageContent.class);
    }
}
