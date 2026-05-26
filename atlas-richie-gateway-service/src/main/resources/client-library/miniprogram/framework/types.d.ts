/**
 * 微信小程序类型声明
 * 用于TypeScript类型检查
 */

declare namespace wx {
    function request(options: RequestOptions): void;
    function getStorageSync(key: string): any;
    function setStorageSync(key: string, value: any): void;
    function removeStorageSync(key: string): void;
    function showLoading(options: LoadingOptions): void;
    function hideLoading(): void;
    function showToast(options: ToastOptions): void;
    function arrayBufferToBase64(buffer: ArrayBuffer): string;
    function base64ToArrayBuffer(base64: string): ArrayBuffer;
    function switchTab(options: NavigateOptions): void;
    function navigateTo(options: NavigateOptions): void;
    function redirectTo(options: NavigateOptions): void;
    function reLaunch(options: NavigateOptions): void;
}

interface RequestOptions {
    url: string;
    method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
    header?: Record<string, string>;
    data?: any;
    timeout?: number;
    success?: (res: RequestSuccessCallbackResult) => void;
    fail?: (err: any) => void;
    complete?: () => void;
}

interface RequestSuccessCallbackResult {
    data: any;
    statusCode: number;
    header: Record<string, string>;
    cookies?: string[];
}

interface LoadingOptions {
    title: string;
    mask?: boolean;
}

interface ToastOptions {
    title: string;
    icon?: 'success' | 'error' | 'loading' | 'none';
    duration?: number;
    mask?: boolean;
}

interface NavigateOptions {
    url: string;
    success?: () => void;
    fail?: () => void;
    complete?: () => void;
}

