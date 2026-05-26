/**
 * HTTP请求方法枚举
 * 
 * @author richie696
 * @version 2.0
 * @since 2025-11-01
 */
export enum Method {
    /** HTTP GET请求 */
    GET = 'GET',
    /** HTTP POST请求 */
    POST = 'POST',
    /** HTTP PUT请求 */
    PUT = 'PUT',
    /** HTTP DELETE请求 */
    DELETE = 'DELETE',
    /** HTTP PATCH请求 */
    PATCH = 'PATCH',
    /** 本地页面导航 */
    NAVIGATOR = 'NAVIGATOR',
    /** Mock数据或Location */
    LOCATION = 'LOCATION'
}

