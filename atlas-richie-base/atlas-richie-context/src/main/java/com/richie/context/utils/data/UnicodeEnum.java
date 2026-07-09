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
package com.richie.context.utils.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Unicode编码字符区间定义
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-22 17:52:53
 */
@Getter
@RequiredArgsConstructor
public enum UnicodeEnum {

    /**
     * 基本拉丁字母
     */
    BASIC_LATIN(0x0020, 0x007F),
    /**
     * 拉丁字母补充-1
     */
    LATIN_1_SUPPLEMENT(0x00A0, 0x00FF),
    /**
     * 拉丁字母扩充-A
     */
    LATIN_EXTENDED_A(0x0100, 0x017F),
    /**
     * 拉丁字母扩充-B
     */
    LATIN_EXTENDED_B(0x0180, 0x024F),
    /**
     * 国际⾳标扩充
     */
    IPA_EXTENSIONS(0x0250, 0x02AF),
    /**
     * 进格修饰字符
     */
    SPACING_MODIFIER_LETTERS(0x02B0, 0x02EF),
    /**
     * 组合⾳标附加符号
     */
    COMBINING_DIACRITICAL_MARKS(0x0300, 0x036F),
    /**
     * 希腊字母
     */
    GREEK_AND_COPTIC(0x0370, 0x03FF),
    /**
     * 西⾥尔字母
     */
    CYRILLIC(0x0400, 0x04FF),
    /**
     * 西⾥尔字母补充
     */
    CYRILLIC_SUPPLEMENT(0x0500, 0x052F),
    /**
     * 亚美尼亚⽂
     */
    ARMENIAN(0x0530, 0x058F),
    /**
     * 希伯来⽂
     */
    HEBREW(0x0590, 0x05FF),
    /**
     * 基本阿拉伯⽂
     */
    ARABIC(0x0600, 0x06FF),
    /**
     * 叙利亚⽂
     */
    SYRIAC(0x0700, 0x074F),
    /**
     * 阿拉伯⽂补充
     */
    ARABIC_SUPPLEMENT(0x0750, 0x077F),
    /**
     * 塔纳⽂
     */
    THAANA(0x0780, 0x07BF),
    /**
     * N'Ko
     */
    N_KO(0x07C0, 0x07FF),
    /**
     * 天城体梵⽂字母
     */
    DEVANAGARI(0x0900, 0x097F),
    /**
     * 孟加拉国⽂
     */
    BENGALI(0x0980, 0x09FF),
    /**
     * 古尔穆基⽂
     */
    GURMUKHI(0x0A00, 0x0A7F),
    /**
     * 古吉拉特⽂
     */
    GUJARATI(0x0A80, 0x0AFF),
    /**
     * 奥⾥亚⽂
     */
    ORIYA(0x0B00, 0x0B7F),
    /**
     * 泰⽶尔⽂
     */
    TAMIL(0x0B80, 0x0BFF),
    /**
     * 泰卢固⽂
     */
    TELUGU(0x0C00, 0x0C7F),
    /**
     * 卡纳达⽂
     */
    KANNADA(0x0C80, 0x0CFF),
    /**
     * 马拉亚拉姆⽂
     */
    MALAYALAM(0x0D00, 0x0D7F),
    /**
     * 僧伽罗⽂
     */
    SINHALA(0x0D80, 0x0DFF),
    /**
     * 泰⽂
     */
    THAI(0x0E00, 0x0E7F),
    /**
     * ⽼挝⽂；寮国⽂
     */
    LAO(0x0E80, 0x0EFF),
    /**
     * 藏⽂
     */
    TIBETAN(0x0F00, 0x0FFF),
    /**
     * 缅甸⽂
     */
    MYANMAR(0x1000, 0x109F),
    /**
     * 格鲁吉亚⽂
     */
    GEORGIAN(0x10A0, 0x10FF),
    /**
     * 谚⽂字母
     */
    HANGUL_JAMO(0x1100, 0x11FF),
    /**
     * 埃塞俄⽐亚⽂
     */
    ETHIOPIC(0x1200, 0x137F),
    /**
     * 埃塞俄⽐亚⽂补充
     */
    ETHIOPIC_SUPPLEMENT(0x1380, 0x139F),
    /**
     * 切罗基⽂
     */
    CHEROKEE(0x13A0, 0x13FF),
    /**
     * 加拿⼤⼟著统⼀⾳节⽂字
     */
    UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS(0x1400, 0x167F),
    /**
     * 欧⽢⽂
     */
    OGHAM(0x1680, 0x169F),
    /**
     * 北欧古⽂
     */
    RUNIC(0x16A0, 0x16FF),
    /**
     * 他加禄⽂
     */
    TAGALOG(0x1700, 0x171F),
    /**
     * 哈努诺⽂
     */
    HANUNOO(0x1720, 0x173F),
    /**
     * 布什德⽂
     */
    BUHID(0x1740, 0x175F),
    /**
     * 塔格巴努亚⽂
     */
    TAGBANWA(0x1760, 0x177F),
    /**
     * ⾼棉⽂
     */
    KHMER(0x1780, 0x17FF),
    /**
     * 蒙古⽂
     */
    MONGOLIAN(0x1800, 0x18AF),
    /**
     * 林布⽂
     */
    LIMBU(0x1900, 0x194F),
    /**
     * 傣哪⽂；德宏傣⽂
     */
    TAI_LE(0x1950, 0x197F),
    /**
     * 新傣仂⽂
     */
    NEW_TAI_LUE(0x1980, 0x19DF),
    /**
     * ⾼棉符号
     */
    KHMER_SYMBOLS(0x19E0, 0x19FF),
    /**
     * 布吉⽂
     */
    BUGINESE(0x1A00, 0x1A1F),
    /**
     * 巴利⽂
     */
    BALINESE(0x1B00, 0x1B7F),
    /**
     * ⾳标扩充
     */
    PHONETIC_EXTENSIONS(0x1D00, 0x1D7F),
    /**
     * ⾳标扩充补充
     */
    PHONETIC_EXTENSIONS_SUPPLEMENT(0x1D80, 0x1DBF),
    /**
     * 组合⾳标附加符号
     */
    COMBINING_DIACRITICAL_MARKS_SUPPLEMENT(0x1DC0, 0x1DFF),
    /**
     * 拉丁字母扩充附加
     */
    LATIN_EXTENDED_ADDITIONAL(0x1E00, 0x1EFF),
    /**
     * 希腊⽂扩充
     */
    GREEK_EXTENDED(0x1F00, 0x1FFF),
    /**
     * ⼀般标点符号
     */
    GENERAL_PUNCTUATION(0x2000, 0x206F),
    /**
     * 下标及上标
     */
    SUPERSCRIPTS_AND_SUBSCRIPTS(0x2070, 0x209F),
    /**
     * 货币符号
     */
    CURRENCY_SYMBOLS(0x20A0, 0x20CF),
    /**
     * 符号⽤组合附加符号
     */
    COMBINING_DIACRITICAL_MARKS_FOR_SYMBOLS(0x20D0, 0x20FF),
    /**
     * 似字母符号
     */
    LETTERLIKE_SYMBOLS(0x2100, 0x214F),
    /**
     * 数字形式
     */
    NUMBER_FORMS(0x2150, 0x218F),
    /**
     * 箭头符号
     */
    ARROWS(0x2190, 0x21FF),
    /**
     * 数学运算符号
     */
    MATHEMATICAL_OPERATORS(0x2200, 0x22FF),
    /**
     * 混合专门符号
     */
    MISCELLANEOUS_TECHNICAL(0x2300, 0x23FF),
    /**
     * 控制图像
     */
    CONTROL_PICTURES(0x2400, 0x243F),
    /**
     * 光学字符识别
     */
    OPTICAL_CHARACTER_RECOGNITION(0x2440, 0x245F),
    /**
     * 括号字母数字
     */
    ENCLOSED_ALPHANUMERICS(0x2460, 0x24FF),
    /**
     * 制表符
     */
    BOX_DRAWING(0x2500, 0x257F),
    /**
     * 区块组件
     */
    BLOCK_ELEMENTS(0x2580, 0x259F),
    /**
     * ⼏何形状
     */
    GEOMETRIC_SHAPES(0x25A0, 0x25FF),
    /**
     * 混合什锦符号
     */
    MISCELLANEOUS_SYMBOLS(0x2600, 0x26FF),
    /**
     * 什锦符号
     */
    DINGBATS(0x2700, 0x27BF),
    /**
     * 混合数学符号-A
     */
    MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A(0x27C0, 0x27EF),
    /**
     * 补充性箭头符号-A
     */
    SUPPLEMENTAL_ARROWS_A(0x27F0, 0x27FF),
    /**
     * 盲⽂；盲⼈点字
     */
    BRAILLE_PATTERNS(0x2800, 0x28FF),
    /**
     * 补充性箭头符号-B
     */
    SUPPLEMENTAL_ARROWS_B(0x2900, 0x297F),
    /**
     * 混合数学符号-B
     */
    MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B(0x2980, 0x29FF),
    /**
     * 补充性数学运算符号
     */
    SUPPLEMENTAL_MATHEMATICAL_OPERATORS(0x2A00, 0x2AFF),
    /**
     * 混合什锦符号和箭头符号
     */
    MISCELLANEOUS_SYMBOLS_AND_ARROWS(0x2B00, 0x2BFF),
    /**
     * 格拉⼽尔字母
     */
    GLAGOLITIC(0x2c00, 0x2c5f),
    /**
     * 拉丁字母扩充-c
     */
    LATIN_EXTENDED_C(0x2C60, 0x2C7F),
    /**
     * 科普特⽂
     */
    COPTIC(0x2C80, 0x2CFF),
    /**
     * 格鲁吉亚⽂补充
     */
    GEORGIAN_SUPPLEMENT(0x2D00, 0x2D2F),
    /**
     * 提⾮纳格字母
     */
    TIFINAGH(0x2D30, 0x2D7F),
    /**
     * 埃塞俄⽐亚⽂扩充
     */
    ETHIOPIC_EXTENDED(0x2D80, 0x2DDF),
    /**
     * 补充性标点符号
     */
    SUPPLEMENTAL_PUNCTUATION(0x2E00, 0x2E7F),
    /**
     * 中⽇韩部⾸补充
     */
    CJK_RADICALS_SUPPLEMENT(0x2E80, 0x2EFF),
    /**
     * 康熙部⾸
     */
    KANGXI_RADICALS(0x2F00, 0x2FDF),
    /**
     * 汉字结构描述字符
     */
    IDEOGRAPHIC_DESCRIPTION_CHARACTERS(0x2FF0, 0x2FFF),
    /**
     * 中⽇韩符号和标点
     */
    CJK_SYMBOLS_AND_PUNCTUATION(0x3000, 0x303F),
    /**
     * 平假名
     */
    HIRAGANA(0x3040, 0x309F),
    /**
     * ⽚假名
     */
    KATAKANA(0x30A0, 0x30FF),
    /**
     * 注⾳符号
     */
    BOPOMOFO(0x3100, 0x312F),
    /**
     * 谚⽂兼容字母
     */
    HANGUL_COMPATIBILITY_JAMO(0x3130, 0x318F),
    /**
     * 汉⽂标注号
     */
    KANBUN(0x3190, 0x319F),
    /**
     * 注⾳符号扩充
     */
    BOPOMOFO_EXTENDED(0x31A0, 0x31BF),
    /**
     * 中⽇韩笔画部件
     */
    CJK_STROKES(0x31C0, 0x31EF),
    /**
     * ⽚假名⾳标扩充
     */
    KATAKANA_PHONETIC_EXTENSIONS(0x31F0, 0x31FF),
    /**
     * 中⽇韩括号字母及⽉份
     */
    ENCLOSED_CJK_LETTERS_AND_MONTHS(0x3200, 0x32FF),
    /**
     * 中⽇韩兼容字符
     */
    CJK_COMPATIBILITY(0x3300, 0x33FF),
    /**
     * 中⽇韩统⼀表意⽂字扩充A
     */
    CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A(0x3400, 0x4DBF),
    /**
     * 易经六⼗四卦象
     */
    YIJING_HEXAGRAM_SYMBOLS(0x4DC0, 0x4DFF),
    /**
     * 中⽇韩统⼀表意⽂字
     */
    CJK_UNIFIED_IDEOGRAPHS(0x4E00, 0x9FFF),
    /**
     * 彝⽂⾳节
     */
    YI_SYLLABLES(0xA000, 0xA48F),
    /**
     * 彝⽂字母
     */
    YI_RADICALS(0xA490, 0xA4CF),
    /**
     * 声调符号
     */
    MODIFIER_TONE_LETTERS(0xA700, 0xA71F),
    /**
     * 拉丁字母扩充-D
     */
    LATIN_EXTENDED_D(0xA720, 0xA7FF),
    /**
     * Syloti Nagri
     */
    SYLOTI_NAGRI(0xA800, 0xA82F),
    /**
     * ⼋思巴字母
     */
    PHAGS_PA(0xA840, 0xA87F),
    /**
     * 谚⽂⾳节
     */
    HANGUL_SYLLABLES(0xAC00, 0xD7AF),
    /**
     * ⾼半代⽤区
     */
    HIGH_SURROGATES(0xD800, 0xDB7F),
    /**
     * ⾼半专⽤代⽤区
     */
    HIGH_PRIVATE_USE_SURROGATES(0xDB80, 0xDBFF),
    /**
     * 低半代⽤区
     */
    LOW_SURROGATES(0xDC00, 0xDFFF),
    /**
     * 专⽤区
     */
    PRIVATE_USE_AREA(0xE000, 0xF8FF),
    /**
     * 中⽇韩兼容表意⽂字
     */
    CJK_COMPATIBILITY_IDEOGRAPHS(0xF900, 0xFAFF),
    /**
     * 字母变体显现形式
     */
    ALPHABETIC_PRESENTATION_FORMS(0xFB00, 0xFB4F),
    /**
     * 阿拉伯⽂变体显现形式_A
     */
    ARABIC_PRESENTATION_FORMS_A(0xFB50, 0xFDFF),
    /**
     * 字型变换选取器
     */
    VARIATION_SELECTORS(0xFE00, 0xFE0F),
    /**
     * 竖式标点
     */
    VERTICAL_FORMS(0xFE10, 0xFE1F),
    /**
     * 组合半⾓标⽰
     */
    COMBINING_HALF_MARKS(0xFE20, 0xFE2F),
    /**
     * 中⽇韩相容形式
     */
    CJK_COMPATIBILITY_FORMS(0xFE30, 0xFE4F),
    /**
     * ⼩写变体
     */
    SMALL_FORM_VARIANTS(0xFE50, 0xFE6F),
    /**
     * 阿拉伯⽂变体显现形式-B
     */
    ARABIC_PRESENTATION_FORMS_B(0xFE70, 0xFEFF),
    /**
     * 半⾓及全⾓字符
     */
    HALFWIDTH_AND_FULLWIDTH_FORMS(0xFF00, 0xFFEF),
    /**
     * 特殊区域
     */
    SPECIALS(0xFFF0, 0xFFFF),
    /**
     * 线形⽂字B⾳节⽂字
     */
    LINEAR_B_SYLLABARY(0x10000, 0x1007F),
    /**
     * 线形⽂字B表意⽂字
     */
    LINEAR_B_IDEOGRAMS(0x10080, 0x100FF),
    /**
     * 爱琴数字
     */
    AEGEAN_NUMBERS(0x10100, 0x1013F),
    /**
     * 古希腊数字
     */
    ANCIENT_GREEK_NUMBERS(0x10140, 0x1018F),
    /**
     * 古意⼤利⽂
     */
    OLD_ITALIC(0x10300, 0x1032F),
    /**
     * 哥特⽂
     */
    GOTHIC(0x10330, 0x1034F),
    /**
     * 乌加⾥特楔形⽂字
     */
    UGARITIC(0x10380, 0x1039F),
    /**
     * 古波斯⽂
     */
    OLD_PERSIAN(0x103A0, 0x103DF),
    /**
     * 犹他⼤学⾳标
     */
    DESERET(0x10400, 0x1044F),
    /**
     * 肃伯纳字母
     */
    SHAVIAN(0x10450, 0x1047F),
    /**
     * Osmanya
     */
    OSMANYA(0x10480, 0x104AF),
    /**
     * 塞浦路斯⾳节⽂字
     */
    CYPRIOT_SYLLABARY(0x10800, 0x1083F),
    /**
     * 腓尼基字母
     */
    PHOENICIAN(0x10900, 0x1091F),
    /**
     * 佉卢字母
     */
    KHAROSHTHI(0x10A00, 0x10A5F),
    /**
     * 楔形⽂字
     */
    CUNEIFORM(0x12000, 0x123FF),
    /**
     * 楔形⽂字数字及标点
     */
    CUNEIFORM_NUMBERS_AND_PUNCTUATION(0x12400, 0x1247F),
    /**
     * 东正教⾳乐符号
     */
    BYZANTINE_MUSICAL_SYMBOLS(0x1D000, 0x1D0FF),
    /**
     * ⾳乐符号
     */
    MUSICAL_SYMBOLS(0x1D100, 0x1D1FF),
    /**
     * 古希腊⾳乐谱记号
     */
    ANCIENT_GREEK_MUSICAL_NOTATION(0x1D200, 0x1D24F),
    /**
     * 太⽞经符号
     */
    TAI_XUAN_JING_SYMBOLS(0x1D300, 0x1D35F),
    /**
     * 算筹记数式
     */
    COUNTING_ROD_NUMERALS(0x1D360, 0x1D37F),
    /**
     * 数学⽤字母数字符号
     */
    MATHEMATICAL_ALPHANUMERIC_SYMBOLS(0x1D400, 0x1D7FF),
    /**
     * 中⽇韩统⼀表意⽂字扩充B
     */
    CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B(0x20000, 0x2A6DF),
    /**
     * 中⽇韩兼容表意⽂字补充
     */
    CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT(0x2F800, 0x2FA1F),
    /**
     * 语⾔编码卷标
     */
    TAGS(0xE0000, 0xE007F),
    /**
     * 字型变换选取器补充
     */
    VARIATION_SELECTORS_SUPPLEMENT(0xE0100, 0xE01EF),
    /**
     * 补充专⽤区-A
     */
    SUPPLEMENTARY_PRIVATE_USE_AREA_A(0xFFF80, 0xFFFFF),
    /**
     * 补充专⽤区-B
     */
    SUPPLEMENTARY_PRIVATE_USE_AREA_B(0x10FF80, 0x10FFFF);

    /**
     * 字符区间下限
     */
    private final int floor;

    /**
     * 字符区间上限
     */
    private final int ceil;

    /**
     * 判断字符是否在当前范围内
     * @param character 字符码点
     * @return 是否在范围内
     */
    public boolean isCurrentRange(int character) {
        return character >= floor && character <= ceil;
    }

}
