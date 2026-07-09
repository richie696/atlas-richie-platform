/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.http.core;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * SSE 协议文本流解析器（行级别，状态机）。
 * <p>
 * 用于把底层流式响应按行喂入，解析出完整的 {@link SseEvent}。
 * 该解析器由不具备原生 SSE 支持的 Provider（HttpClient5、JDK、RestClient）
 * 复用，保证各 Provider 输出的事件结构完全一致。
 *
 * <h2>SSE 协议要点</h2>
 * <ul>
 *   <li>以空行（{@code \n} 或 {@code \r\n}）作为事件边界。</li>
 *   <li>以 {@code :} 开头的行为注释，整行忽略。</li>
 *   <li>{@code data:} 后允许多行：每个 {@code data:} 行会被 {@code \n} 拼接。</li>
 *   <li>{@code retry:} 仅接受正整数（毫秒），非法值丢弃。</li>
 *   <li>{@code id} / {@code event} / {@code retry} 在一个事件内多次出现时取最后一个非空值。</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 *   SseLineParser parser = new SseLineParser();
 *   try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, UTF_8))) {
 *       String line;
 *       while ((line = reader.readLine()) != null) {
 *           SseEvent event = parser.feed(line);
 *           if (event != null) listener.onEvent(conn, event);
 *       }
 *   }
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public final class SseLineParser {

    private static final Pattern RETRY_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final StringBuilder data = new StringBuilder();
    private String id;
    private String event;
    private Duration retry;

    /**
     * 喂入一行 SSE 协议文本，返回是否解析出一个完整事件。
     *
     * @param line 协议中读取到的一行（不含行尾的换行符；空行直接传 {@code ""}）
     * @return 当遇到事件边界（空行）且累积到非空内容时返回对应事件；否则返回 {@code null}
     */
    public SseEvent feed(String line) {
        // 空行 = 事件边界
        if (line == null || line.isEmpty()) {
            return dispatch();
        }

        // 注释行：以 ":" 开头
        if (line.charAt(0) == ':') {
            return null;
        }

        int colonIdx = line.indexOf(':');
        String field;
        String value;
        if (colonIdx == -1) {
            field = line;
            value = "";
        } else {
            field = line.substring(0, colonIdx);
            // 协议规定：冒号后第一个字符如果是空格则去掉
            value = (colonIdx + 1 < line.length() && line.charAt(colonIdx + 1) == ' ')
                    ? line.substring(colonIdx + 2)
                    : line.substring(colonIdx + 1);
        }

        switch (field) {
            case "id" -> id = value;
            case "event" -> event = value;
            case "data" -> {
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(value);
            }
            case "retry" -> {
                if (RETRY_PATTERN.matcher(value).matches()) {
                    retry = Duration.ofMillis(Long.parseLong(value));
                }
            }
            default -> {
                // 未知字段按协议应忽略
            }
        }
        return null;
    }

    /**
     * 冲刷残留状态，事件流结束前调用一次以输出可能仍在累积的事件。
     *
     * @return 若缓冲中已累积事件则返回；否则返回 {@code null}
     */
    public SseEvent flush() {
        return dispatch();
    }

    private SseEvent dispatch() {
        if (data.length() == 0 && id == null && event == null && retry == null) {
            return null;
        }
        String resolvedEvent = (event == null || event.isEmpty()) ? SseEvent.DEFAULT_EVENT_NAME : event;
        SseEvent ev = new SseEvent(id, resolvedEvent, data.length() == 0 ? null : data.toString(), retry);
        reset();
        return ev;
    }

    private void reset() {
        data.setLength(0);
        id = null;
        event = null;
        retry = null;
    }

}