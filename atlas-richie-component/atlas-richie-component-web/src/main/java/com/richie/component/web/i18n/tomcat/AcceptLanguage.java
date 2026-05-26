package com.richie.component.web.i18n.tomcat;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Accept-Language 头中单个语言项：Locale + 质量值（q）。
 */
public class AcceptLanguage {

    private final Locale locale;
    private final double quality;

    protected AcceptLanguage(Locale locale, double quality) {
        this.locale = locale;
        this.quality = quality;
    }

    /**
     * 返回语言地区。
     *
     * @return Locale
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * 返回质量值（0–1）。
     *
     * @return quality
     */
    public double getQuality() {
        return quality;
    }

    /**
     * 解析 Accept-Language 头内容（如 zh-CN,zh;q=0.9,en;q=0.8）。
     *
     * @param input 已包装为 StringReader 的头值
     * @return 按出现顺序的 AcceptLanguage 列表（仅 quality &gt; 0）
     * @throws IOException 读取失败时抛出
     */
    public static List<AcceptLanguage> parse(StringReader input) throws IOException {

        List<AcceptLanguage> result = new ArrayList<>();

        do {
            // Token is broader than what is permitted in a language tag
            // (alphanumeric + '-') but any invalid values that slip through
            // will be caught later
            String languageTag = HttpParser.readToken(input);
            if (languageTag == null) {
                // Invalid tag, skip to the next one
                HttpParser.skipUntil(input, 0, ',');
                continue;
            }

            if (languageTag.length() == 0) {
                // No more data to read
                break;
            }

            // See if a quality has been provided
            double quality = 1;
            SkipResult lookForSemiColon = HttpParser.skipConstant(input, ";");
            if (lookForSemiColon == SkipResult.FOUND) {
                quality = HttpParser.readWeight(input, ',');
            }

            if (quality > 0) {
                result.add(new AcceptLanguage(Locale.forLanguageTag(languageTag), quality));
            }
        } while (true);

        return result;
    }
}
