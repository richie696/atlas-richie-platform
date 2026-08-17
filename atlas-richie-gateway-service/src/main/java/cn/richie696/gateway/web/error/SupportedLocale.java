package cn.richie696.gateway.web.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Locale;

/**
 * 错误码文档页支持的语言清单（注册中心）。
 *
 * <p>该枚举为错误码文档页语言下拉框的唯一定义点；新增语言时只需追加枚举值，运行时
 * 列表与详情页会自动出现在 {@code <select>} 中。</p>
 *
 * <p>下拉框展示文案使用每种语言自己的母语（如 {@code "简体中文"}、{@code "English"}、{@code "日本語"}），
 * 不依赖 i18n 资源，从而避免 35×35 的资源翻译矩阵。同语系的多地区变体（如 zh-CN/zh-TW、es-ES/es-MX、
 * pt-BR/pt-PT）会附加国家/地区用于区分。</p>
 *
 * <p>{@link #tag} 是 BCP 47 语言标签，与 {@link Locale#forLanguageTag(String)} 一致，
 * URL 查询参数 {@code ?lang=xx-XX} 也使用同一格式。</p>
 *
 * @author richie696
 * @since 2026-08-05
 */
@Getter
@RequiredArgsConstructor
public enum SupportedLocale {

    /** 简体中文（中国大陆）。 */
    ZH_CN("zh-CN", "zh", "CN", "简体中文"),

    /** 繁体中文（台湾）。 */
    ZH_TW("zh-TW", "zh", "TW", "繁體中文"),

    /** 英语（美国）。 */
    EN_US("en-US", "en", "US", "English"),

    /** 日语（日本）。 */
    JA_JP("ja-JP", "ja", "JP", "日本語"),

    /** 韩语（韩国）。 */
    KO_KR("ko-KR", "ko", "KR", "한국어"),

    /** 法语（法国）。 */
    FR_FR("fr-FR", "fr", "FR", "Français"),

    /** 德语（德国）。 */
    DE_DE("de-DE", "de", "DE", "Deutsch"),

    /** 西班牙语（西班牙）。 */
    ES_ES("es-ES", "es", "ES", "Español"),

    /** 西班牙语（墨西哥）。 */
    ES_MX("es-MX", "es", "MX", "Español (México)"),

    /** 葡萄牙语（巴西）。 */
    PT_BR("pt-BR", "pt", "BR", "Português (Brasil)"),

    /** 葡萄牙语（葡萄牙）。 */
    PT_PT("pt-PT", "pt", "PT", "Português"),

    /** 意大利语（意大利）。 */
    IT_IT("it-IT", "it", "IT", "Italiano"),

    /** 俄语（俄罗斯）。 */
    RU_RU("ru-RU", "ru", "RU", "Русский"),

    /** 乌克兰语（乌克兰）。 */
    UK_UA("uk-UA", "uk", "UA", "Українська"),

    /** 波兰语（波兰）。 */
    PL_PL("pl-PL", "pl", "PL", "Polski"),

    /** 捷克语（捷克）。 */
    CS_CZ("cs-CZ", "cs", "CZ", "Čeština"),

    /** 荷兰语（荷兰）。 */
    NL_NL("nl-NL", "nl", "NL", "Nederlands"),

    /** 罗马尼亚语（罗马尼亚）。 */
    RO_RO("ro-RO", "ro", "RO", "Română"),

    /** 匈牙利语（匈牙利）。 */
    HU_HU("hu-HU", "hu", "HU", "Magyar"),

    /** 希腊语（希腊）。 */
    EL_GR("el-GR", "el", "GR", "Ελληνικά"),

    /** 土耳其语（土耳其）。 */
    TR_TR("tr-TR", "tr", "TR", "Türkçe"),

    /** 瑞典语（瑞典）。 */
    SV_SE("sv-SE", "sv", "SE", "Svenska"),

    /** 丹麦语（丹麦）。 */
    DA_DK("da-DK", "da", "DK", "Dansk"),

    /** 挪威书面语（挪威）。 */
    NB_NO("nb-NO", "nb", "NO", "Norsk"),

    /** 芬兰语（芬兰）。 */
    FI_FI("fi-FI", "fi", "FI", "Suomi"),

    /** 越南语（越南）。 */
    VI_VN("vi-VN", "vi", "VN", "Tiếng Việt"),

    /** 泰语（泰国）。 */
    TH_TH("th-TH", "th", "TH", "ไทย"),

    /** 印尼语（印度尼西亚）。 */
    ID_ID("id-ID", "id", "ID", "Bahasa Indonesia"),

    /** 马来语（马来西亚）。 */
    MS_MY("ms-MY", "ms", "MY", "Bahasa Melayu"),

    /** 印地语（印度）。 */
    HI_IN("hi-IN", "hi", "IN", "हिन्दी"),

    /** 孟加拉语（孟加拉国）。 */
    BN_BD("bn-BD", "bn", "BD", "বাংলা"),

    /** 乌尔都语（巴基斯坦）。 */
    UR_PK("ur-PK", "ur", "PK", "اردو"),

    /** 波斯语（伊朗）。 */
    FA_IR("fa-IR", "fa", "IR", "فارسی"),

    /** 阿拉伯语（沙特阿拉伯）。 */
    AR_SA("ar-SA", "ar", "SA", "العربية"),

    /** 希伯来语（以色列）。 */
    HE_IL("he-IL", "he", "IL", "עברית");

    /**
     * BCP 47 语言标签（连字符分隔）。
     */
    private final String tag;

    /**
     * ISO-639 语言代码（小写）。
     */
    private final String languageCode;

    /**
     * ISO-3166 国家/地区代码（大写）。
     */
    private final String countryCode;

    /**
     * 该 locale 的母语展示名（如 {@code "简体中文"}、{@code "English"}），
     * 语言下拉框中始终以其母语形式显示，便于母语用户一眼识别。
     */
    private final String nativeLabel;

    /**
     * 获取解析后的 {@link Locale} 实例。
     *
     * @return Locale（每次新建，便于在多线程环境下安全持有）
     */
    public Locale toLocale() {
        return Locale.forLanguageTag(tag);
    }

    /**
     * 在指定显示 locale 下的本地化展示名。
     *
     * <p>由 JDK ICU 按 {@code displayLocale} 自动翻译为该 locale 的母语
     * （例如 {@code zh-CN.getDisplayName(Locale.JAPAN)} = "中文 (中国)"）。</p>
     *
     * @param displayLocale 用于显示的区域
     * @return 展示名；永远非 null
     */
    public String getDisplayName(Locale displayLocale) {
        Locale target = displayLocale == null ? Locale.getDefault() : displayLocale;
        return toLocale().getDisplayName(target);
    }

    /**
     * 按 BCP 47 tag 反查。
     *
     * @param tag 语言标签（如 {@code "zh-CN"} 或 {@code "zh_CN"}，大小写不敏感）
     * @return 匹配项；找不到时返回 {@code null}
     */
    public static SupportedLocale fromTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        String normalized = tag.replace('_', '-');
        return Arrays.stream(values())
                .filter(v -> v.tag.equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null);
    }
}
