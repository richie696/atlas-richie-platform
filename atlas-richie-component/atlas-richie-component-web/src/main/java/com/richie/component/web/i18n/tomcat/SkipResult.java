package com.richie.component.web.i18n.tomcat;

/**
 * HTTP 头解析时“跳过/查找”的结果：找到常量、未找到、或读到结尾。
 */
enum SkipResult {
    /** 找到目标 */
    FOUND,
    /** 未找到目标 */
    NOT_FOUND,
    /** 已到输入结尾 */
    EOF
}