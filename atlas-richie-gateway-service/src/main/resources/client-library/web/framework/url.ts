/**
 * URL枚举类 - 改进版
 * 支持加密和防重复提交配置
 * 
 * @author richie696
 * @version 2.0
 * @since 2025-11-01
 */

import { Enum } from './abstract.enums';
import { Method } from './method.enum';

/**
 * URL配置选项
 */
export interface UrlOptions {
    /** 是否需要加密（默认false） */
    needEncryption?: boolean;
    /** 是否需要防重复提交检查（默认false） */
    needDuplicateCheck?: boolean;
}

/**
 * URL枚举类
 * 用于定义API端点及其安全配置
 * 
 * @example
 * ```typescript
 * // 基础用法（向后兼容）
 * public static readonly MENU_ALL = new Url('MENU_ALL', '/api/menu/all', Method.GET);
 * 
 * // 带安全配置
 * public static readonly USER_LOGIN = new Url(
 *   'USER_LOGIN', 
 *   '/api/auth/login', 
 *   Method.POST,
 *   { needEncryption: true, needDuplicateCheck: false }
 * );
 * 
 * // 简化写法
 * public static readonly PAYMENT_CREATE = new Url(
 *   'PAYMENT_CREATE',
 *   '/api/payment/create',
 *   Method.POST,
 *   true,  // needEncryption
 *   true   // needDuplicateCheck
 * );
 * 
 * // 页面导航
 * public static readonly PAGE_HOME = new Url('PAGE_HOME', 'home', Method.NAVIGATOR);
 * ```
 */
export class Url extends Enum<Url> {
    /** 基础URL（可以通过 setBaseUrl 动态设置） */
    private static _baseUrl: string = '';

    private readonly _value: string;
    private readonly _method: Method;
    private readonly _needEncryption: boolean;
    private readonly _needDuplicateCheck: boolean;

    /**
     * 构造函数
     * @param objectName 枚举对象名称
     * @param value URL路径
     * @param method HTTP方法
     * @param needEncryptionOrOptions 是否需要加密 或 完整配置选项（可选，默认false）
     * @param needDuplicateCheck 是否需要防重复提交（可选，默认false）
     */
    constructor(
        objectName: string,
        value: string,
        method: Method,
        needEncryptionOrOptions?: boolean | UrlOptions,
        needDuplicateCheck?: boolean
    ) {
        super(objectName);
        this._value = value;
        this._method = method;

        // 处理参数
        if (typeof needEncryptionOrOptions === 'object') {
            // 使用配置对象
            this._needEncryption = needEncryptionOrOptions.needEncryption || false;
            this._needDuplicateCheck = needEncryptionOrOptions.needDuplicateCheck || false;
        } else {
            // 使用简化参数
            this._needEncryption = needEncryptionOrOptions || false;
            this._needDuplicateCheck = needDuplicateCheck || false;
        }
    }

    /**
     * 设置基础URL
     * @param baseUrl 基础URL（如 'https://api.example.com'）
     */
    public static setBaseUrl(baseUrl: string): void {
        Url._baseUrl = baseUrl;
    }

    /**
     * 获取基础URL
     */
    public static getBaseUrl(): string {
        return Url._baseUrl;
    }

    /**
     * 根据URL地址查找枚举对象
     * @param url URL地址
     * @return 返回URL枚举对象
     */
    public static urlOf(url: string): Url {
        const values: ReadonlyArray<Url> = Url.values();
        for (const obj of values) {
            if (obj.path === url || obj.value() === url) {
                return obj;
            }
        }
        throw new Error(`您访问的页面地址无效，url = ${url}`);
    }

    /**
     * 获取完整URL
     * @param args 路径参数（用于替换URL中的占位符）
     * @return 返回完整的URL字符串
     * 
     * @example
     * ```typescript
     * // 基础用法
     * const url = AppUrl.MENU_ALL.value();
     * // => 'https://api.example.com/api/menu/all'
     * 
     * // 带参数的GET请求
     * const url = AppUrl.USER_DETAIL.value(['123']);
     * // => 'https://api.example.com/api/user/123'
     * 
     * // 页面导航
     * const url = AppUrl.PAGE_HOME.value();
     * // => 'home'
     * ```
     */
    public value(args?: any[]): string {
        switch (this._method) {
            case Method.POST:
            case Method.PUT:
            case Method.DELETE:
            case Method.PATCH:
                return this.buildApiUrl(this._value);

            case Method.GET:
                return this.buildApiUrl(this.replacePathParams(this._value, args));

            case Method.NAVIGATOR:
            case Method.LOCATION:
            default:
                // 页面导航或Mock数据，不添加baseUrl
                return this._value;
        }
    }

    /**
     * 构建API URL
     */
    private buildApiUrl(path: string): string {
        if (!Url._baseUrl) {
            return path;
        }
        // 确保baseUrl不以/结尾，path以/开头
        const base = Url._baseUrl.replace(/\/$/, '');
        const cleanPath = path.startsWith('/') ? path : '/' + path;
        return base + cleanPath;
    }

    /**
     * 替换路径参数
     * 支持两种格式：
     * 1. {} - 按顺序替换
     * 2. {name} - 按名称替换（从args[0]对象中取值）
     */
    private replacePathParams(path: string, args?: any[]): string {
        if (!args || args.length === 0) {
            return path;
        }

        // 如果第一个参数是对象，支持命名参数
        if (typeof args[0] === 'object' && !Array.isArray(args[0])) {
            const params = args[0];
            return path.replace(/\{(\w+)\}/g, (match, key) => {
                return params[key] !== undefined ? String(params[key]) : match;
            });
        }

        // 否则按顺序替换 {}
        let index = 0;
        return path.replace(/\{}/g, () => {
            return args && index < args.length ? String(args[index++]) : '{}';
        });
    }

    /**
     * 获取原始路径（不包含baseUrl）
     */
    public get path(): string {
        return this._value;
    }

    /**
     * 获取HTTP方法
     */
    public get method(): Method {
        return this._method;
    }

    /**
     * 是否需要加密
     */
    public get needEncryption(): boolean {
        return this._needEncryption;
    }

    /**
     * 是否需要防重复提交检查
     */
    public get needDuplicateCheck(): boolean {
        return this._needDuplicateCheck;
    }

    /**
     * 获取完整URL（别名方法）
     */
    public getFullUrl(baseUrl?: string): string {
        if (baseUrl) {
            const oldBaseUrl = Url._baseUrl;
            Url._baseUrl = baseUrl;
            const result = this.value();
            Url._baseUrl = oldBaseUrl;
            return result;
        }
        return this.value();
    }

    /**
     * 判断是否为API请求
     */
    public isApiRequest(): boolean {
        return this._method !== Method.NAVIGATOR && this._method !== Method.LOCATION;
    }

    /**
     * 判断是否为写操作
     */
    public isWriteOperation(): boolean {
        return this._method === Method.POST 
            || this._method === Method.PUT 
            || this._method === Method.DELETE 
            || this._method === Method.PATCH;
    }

    /**
     * 创建带参数的URL实例（用于链式调用）
     */
    public withParams(...args: any[]): string {
        return this.value(args);
    }

    /**
     * 输出详细信息（用于调试）
     */
    public toDebugString(): string {
        return `Url{name='${this.name}', path='${this._value}', method=${this._method}, ` +
               `encryption=${this._needEncryption}, duplicateCheck=${this._needDuplicateCheck}}`;
    }
}

