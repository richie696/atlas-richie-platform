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
package cn.richie696.component.ocr.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OcrGeometryTest {

    // ---------- Point ----------

    @Test
    void point_accessors() {
        Point p = new Point(10, 20);
        assertEquals(10, p.x());
        assertEquals(20, p.y());
        assertEquals(new Point(10, 20), p);
        assertNotEquals(new Point(10, 21), p);
        assertEquals(new Point(10, 20).hashCode(), p.hashCode());
    }

    @Test
    void point_negativeCoordinatesThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new Point(-1, 0));
        assertTrue(ex.getMessage().contains("non-negative"));
        assertThrows(IllegalArgumentException.class, () -> new Point(0, -1));
    }

    // ---------- OcrLine ----------

    @Test
    void ocrLine_accessors() {
        List<Point> box = List.of(new Point(0, 0), new Point(4, 0), new Point(4, 1), new Point(0, 1));
        OcrLine line = new OcrLine("hello", box, 0.85f);
        assertEquals("hello", line.text());
        assertEquals(box, line.box());
        assertEquals(0.85f, line.confidence());
    }

    @Test
    void ocrLine_nullTextThrows() {
        assertThrows(NullPointerException.class, () -> new OcrLine(null, List.of(), 0.5f));
    }

    @Test
    void ocrLine_nullBoxBecomesEmptyList() {
        OcrLine line = new OcrLine("text", null, 0.5f);
        assertEquals(List.of(), line.box());
    }

    @Test
    void ocrLine_boxDefensivelyCopied() {
        List<Point> box = new java.util.ArrayList<>(List.of(new Point(1, 1)));
        OcrLine line = new OcrLine("t", box, 0.5f);
        box.add(new Point(9, 9));
        assertEquals(1, line.box().size());
    }

    @Test
    void ocrLine_equalsByFields() {
        OcrLine a = new OcrLine("t", List.of(new Point(0, 0)), 0.5f);
        OcrLine b = new OcrLine("t", List.of(new Point(0, 0)), 0.5f);
        assertEquals(a, b);
        assertNotEquals(a, new OcrLine("x", List.of(new Point(0, 0)), 0.5f));
        assertNotEquals(a, new OcrLine("t", List.of(new Point(1, 1)), 0.5f));
        assertNotEquals(a, new OcrLine("t", List.of(new Point(0, 0)), 0.6f));
        assertNotEquals(a, "not-a-line");
        assertEquals(a.hashCode(), b.hashCode());
    }

    // ---------- OcrBlock ----------

    @Test
    void ocrBlock_accessors() {
        OcrLine line = new OcrLine("hi", null, 0.9f);
        List<Point> box = List.of(new Point(0, 0), new Point(2, 0), new Point(2, 1), new Point(0, 1));
        OcrBlock block = new OcrBlock("hi", box, 0.95f, List.of(line));
        assertEquals("hi", block.text());
        assertEquals(box, block.box());
        assertEquals(0.95f, block.confidence());
        assertEquals(List.of(line), block.lines());
    }

    @Test
    void ocrBlock_twoArgConstructorCreatesEmptyLines() {
        OcrBlock block = new OcrBlock("text", null, 0.7f);
        assertEquals(List.of(), block.lines());
        assertEquals(List.of(), block.box());
    }

    @Test
    void ocrBlock_nullTextThrows() {
        assertThrows(NullPointerException.class, () -> new OcrBlock(null, null, 0.5f));
    }

    @Test
    void ocrBlock_nullLinesBecomesEmptyList() {
        OcrBlock block = new OcrBlock("t", null, 0.5f, null);
        assertEquals(List.of(), block.lines());
    }

    @Test
    void ocrBlock_isConfidentDefaultThreshold() {
        assertTrue(new OcrBlock("a", null, 0.6f).isConfident());
        assertTrue(new OcrBlock("a", null, 0.95f).isConfident());
        assertFalse(new OcrBlock("a", null, 0.59f).isConfident());
    }

    @Test
    void ocrBlock_isConfidentCustomThreshold() {
        OcrBlock block = new OcrBlock("a", null, 0.5f);
        assertTrue(block.isConfident(0.5f));
        assertFalse(block.isConfident(0.51f));
    }

    @Test
    void ocrBlock_equalsByFields() {
        OcrBlock a = new OcrBlock("t", null, 0.5f, List.of(new OcrLine("l", null, 0.4f)));
        OcrBlock b = new OcrBlock("t", null, 0.5f, List.of(new OcrLine("l", null, 0.4f)));
        assertEquals(a, b);
        assertNotEquals(a, new OcrBlock("x", null, 0.5f, List.of(new OcrLine("l", null, 0.4f))));
        assertNotEquals(a, "not-a-block");
        assertEquals(a.hashCode(), b.hashCode());
    }

    // ---------- OcrResult ----------

    @Test
    void ocrResult_accessorsAndNullFallbacks() {
        OcrResult result = new OcrResult("text", null, 0.9f, null, 42L);
        assertEquals("text", result.text());
        assertEquals(List.of(), result.blocks());
        assertEquals(0.9f, result.overallConfidence());
        assertEquals(Map.of(), result.pageMetadata());
        assertEquals(42L, result.latencyMs());
    }

    @Test
    void ocrResult_nullTextThrows() {
        assertThrows(NullPointerException.class, () -> new OcrResult(null, null, 0.5f, null, 0L));
    }

    @Test
    void ocrResult_pageMetadataDefensivelyCopied() {
        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("page", 1);
        OcrResult result = new OcrResult("t", null, 0.5f, meta, 0L);
        meta.put("page", 2);
        assertEquals(1, result.pageMetadata().get("page"));
    }

    @Test
    void ocrResult_highConfidenceTextFiltersByThreshold() {
        OcrBlock confident = new OcrBlock("good", null, 0.9f);
        OcrBlock borderline = new OcrBlock("meh", null, 0.6f);
        OcrBlock low = new OcrBlock("bad", null, 0.2f);
        OcrResult result = new OcrResult("", List.of(confident, borderline, low), 0.9f, null, 0L);

        String filtered = result.highConfidenceText(0.6f);
        assertEquals("good\n\nmeh", filtered);
    }

    @Test
    void ocrResult_highConfidenceText_defaultThreshold() {
        OcrBlock confident = new OcrBlock("ok", null, 0.61f);
        OcrBlock low = new OcrBlock("nope", null, 0.59f);
        OcrResult result = new OcrResult("", List.of(confident, low), 0.61f, null, 0L);

        assertEquals("ok", result.highConfidenceText());
    }

    @Test
    void ocrResult_highConfidenceText_emptyResult() {
        OcrResult result = new OcrResult("", List.of(), 0.5f, null, 0L);
        assertEquals("", result.highConfidenceText());
    }

    @Test
    void ocrResult_equalsExcludesPageMetadata() {
        OcrResult a = new OcrResult("t", List.of(), 0.5f, Map.of("page", 1), 5L);
        OcrResult b = new OcrResult("t", List.of(), 0.5f, Map.of("page", 2), 5L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new OcrResult("x", List.of(), 0.5f, Map.of(), 5L));
        assertNotEquals(a, new OcrResult("t", List.of(), 0.4f, Map.of(), 5L));
        assertNotEquals(a, new OcrResult("t", List.of(), 0.5f, Map.of(), 6L));
        assertNotEquals(a, "not-a-result");
    }

    @Test
    void ocrResult_toStringFormat() {
        OcrResult result = new OcrResult("hello", List.of(new OcrBlock("hello", null, 0.9f)), 0.9f, null, 7L);
        assertEquals("OcrResult{text.length=5, blocks=1, confidence=0.9, latencyMs=7}", result.toString());
    }
}
