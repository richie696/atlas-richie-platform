/**
 * Result 通用响应结构
 * 对应服务端 Java ResultVO<T>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-11-11
 */

package com.richie.httpclient

import org.json.JSONObject

/**
 * 国际化字典类型
 */
typealias I18nDict = Map<String, Map<String, String>>

/**
 * 通用响应结果数据类
 * 对应服务端 ResultVO<T>
 *
 * @param T 响应数据的类型
 *
 * 使用示例：
 * ```kotlin
 * data class User(val id: Int, val name: String)
 *
 * val result: ApiResult<User> = client.request(Url.UserInfo)
 * if (result.isSuccess) {
 *     println("用户信息: ${result.data}")
 * } else {
 *     println("错误: ${result.msg}")
 * }
 * ```
 */
data class ApiResult<T>(
    /** 结果数据 */
    val data: T?,
    /** 结果代码，成功通常为 "200" 或 "SUCCESS" */
    val code: String,
    /** 错误信息或提示信息 */
    val msg: String? = null,
    /** 国际化字典 */
    val i18nDict: I18nDict? = null,
    /** 时间戳（毫秒） */
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 判断是否成功
     */
    val isSuccess: Boolean
        get() = code == "200" || code == "SUCCESS" || code == "0"
    
    /**
     * 判断是否失败
     */
    val isError: Boolean
        get() = !isSuccess
    
    /**
     * 提取数据，如果失败则抛出错误
     * @return 数据部分
     * @throws ApiResultException 如果结果失败
     */
    fun extractData(): T {
        if (!isSuccess || data == null) {
            throw ApiResultException(code, msg ?: "操作失败")
        }
        return data
    }
}

/**
 * Result异常类
 */
class ApiResultException(
    val errorCode: String,
    message: String
) : Exception("操作失败，错误代码: $errorCode, 错误信息: $message")

/**
 * 判断ApiResult是否成功
 */
fun <T> isSuccess(result: ApiResult<T>): Boolean = result.isSuccess

/**
 * 判断ApiResult是否失败
 */
fun <T> isError(result: ApiResult<T>): Boolean = result.isError

/**
 * 从ApiResult中提取数据，如果失败则抛出错误
 */
fun <T> extractData(result: ApiResult<T>): T = result.extractData()

