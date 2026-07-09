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
package com.richie.component.parser.internal;

import com.richie.component.parser.ParseEvent;
import com.richie.component.parser.model.ParsedImage;
import com.richie.component.parser.model.ParsedSection;
import com.richie.component.parser.model.ReadEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 内部 ParseEvent → 公开 ReadEvent 转换 — 仅处理 Section / Image / Failed 三个 case。
 * <p>
 * Finished 不在此处理: 业务方需要的 {@code ReadEvent.Finished(result, totalSections, totalImages)}
 * 携带 {@code ReadResult} 汇总, 而 ReadResult 仅由调用方持有的累加器构造, 因此 facade
 * ({@code DocumentReader}) 在收到内部 Finished 后自行包装完成事件。
 */
public final class ReadEventAdapter {

    private ReadEventAdapter() {
    }

    public static ReadEvent.Section toSection(ParseEvent.Streaming s, String fileName) {
        var seg = s.segment();
        return new ReadEvent.Section(
                new ParsedSection(seg.text(), seg.sectionPath(), defensiveCopy(seg.meta())),
                fileName);
    }

    public static ReadEvent.Image toImage(ParseEvent.ImageStreaming img, String fileName) {
        var image = img.image();
        byte[] data = image.data() != null ? image.data() : new byte[0];
        return new ReadEvent.Image(new ParsedImage(
                image.format() != null ? image.format() : "image/unknown",
                data,
                image.name(),
                image.sectionPath(),
                defensiveCopy(image.meta())),
                fileName);
    }

    public static ReadEvent.Failed toFailed(ParseEvent.Failed err) {
        return new ReadEvent.Failed(err.error());
    }

    private static Map<String, Object> defensiveCopy(Map<String, Object> in) {
        return in != null ? new HashMap<>(in) : new HashMap<>();
    }
}
