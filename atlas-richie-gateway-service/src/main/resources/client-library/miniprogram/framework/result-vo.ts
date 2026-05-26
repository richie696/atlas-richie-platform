/**
 * ResultVO 通用响应结构
 * 与服务端 Java ResultVO<T> 对应
 * 
 * @author richie696
 * @version 1.0
 * @since 2025-01-XX
 */

/**
 * 国际化字典类型
 */
export interface I18nDict {
    [key: string]: {
        [key: string]: string;
    };
}

/**
 * 通用响应结果结构
 * 对应服务端 ResultVO<T>
 * 
 * @template T 响应数据的类型
 * 
 * @example
 * ```typescript
 * // 成功响应示例
 * const result: ResultVO<User> = {
 *   code: '200',
 *   msg: '操作成功',
 *   data: { id: 1, name: 'John' },
 *   i18nDict: {},
 *   timestamp: 1704067200000
 * };
 * 
 * // 错误响应示例
 * const errorResult: ResultVO<null> = {
 *   code: '500',
 *   msg: '操作失败',
 *   data: null,
 *   i18nDict: {},
 *   timestamp: 1704067200000
 * };
 * ```
 */
export interface ResultVO<T = any> {
    /** 结果数据 */
    data: T;
    /** 结果代码，成功通常为 '200' 或 EnumResultMsg.SUCCESS.getCode() */
    code: string;
    /** 错误信息或提示信息 */
    msg?: string;
    /** 国际化字典 */
    i18nDict?: I18nDict;
    /** 时间戳（毫秒） */
    timestamp: number;
}

/**
 * 判断ResultVO是否成功
 * 
 * @param result ResultVO对象
 * @returns 返回true表示成功，false表示失败
 * 
 * @example
 * ```typescript
 * const result = await client.request(AppUrl.USER_LOGIN, { body: { username, password } });
 * if (isSuccess(result)) {
 *   console.log('登录成功:', result.data);
 * } else {
 *   console.error('登录失败:', result.msg);
 * }
 * ```
 */
export function isSuccess<T>(result: ResultVO<T>): boolean {
    return result.code === '200' || result.code === 'SUCCESS' || result.code === '0';
}

/**
 * 判断ResultVO是否失败
 * 
 * @param result ResultVO对象
 * @returns 返回true表示失败，false表示成功
 */
export function isError<T>(result: ResultVO<T>): boolean {
    return !isSuccess(result);
}

/**
 * 从ResultVO中提取数据，如果失败则抛出错误
 * 
 * @param result ResultVO对象
 * @returns 返回数据部分
 * @throws 如果结果失败，抛出包含错误信息的Error
 * 
 * @example
 * ```typescript
 * try {
 *   const result = await client.request(AppUrl.USER_LOGIN, { body: { username, password } });
 *   const user = extractData(result); // 如果失败会抛出错误
 *   console.log('用户信息:', user);
 * } catch (error) {
 *   console.error('操作失败:', error.message);
 * }
 * ```
 */
export function extractData<T>(result: ResultVO<T>): T {
    if (isSuccess(result)) {
        return result.data;
    }
    throw new Error(result.msg || `操作失败，错误代码: ${result.code}`);
}

