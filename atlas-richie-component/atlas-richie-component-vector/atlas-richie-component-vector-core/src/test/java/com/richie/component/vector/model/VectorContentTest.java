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
package com.richie.component.vector.model;

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
    void imageContent_exposesAccessorsAndModality() {
        VectorContent.ImageContent image = new VectorContent.ImageContent(new byte[]{1, 2}, "image/jpeg");

        assertThat(image.modality()).isEqualTo(Modality.IMAGE);
        assertThat(image.data()).containsExactly(1, 2);
        assertThat(image.mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    void imageContent_rejectsEmptyData() {
        assertThatThrownBy(() -> new VectorContent.ImageContent(new byte[0], "image/png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ImageContent.data");
    }

    @Test
    void imageContent_rejectsNonImageMimeType() {
        assertThatThrownBy(() -> new VectorContent.ImageContent(new byte[]{1}, "video/mp4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("image/*");
    }

    @Test
    void exhaustiveSwitch_coversBothPermits() {
        VectorContent[] samples = {
                new VectorContent.TextContent("a", "text/plain"),
                new VectorContent.ImageContent(new byte[]{1}, "image/png")
        };
        int textCount = 0;
        int imageCount = 0;
        for (VectorContent c : samples) {
            if (c instanceof VectorContent.TextContent) textCount++;
            else if (c instanceof VectorContent.ImageContent) imageCount++;
        }
        assertThat(textCount).isEqualTo(1);
        assertThat(imageCount).isEqualTo(1);
    }
}