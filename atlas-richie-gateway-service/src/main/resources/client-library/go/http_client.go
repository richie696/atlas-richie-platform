// Package httpclient 提供HTTP客户端，支持ECC加密和防重复提交
//
// Author: richie696
// Version: 1.0
// Since: 2025-11-01
package httpclient

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"crypto/ecdh"
	"crypto/md5"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync"
	"time"
)

// Method HTTP方法枚举
type Method string

const (
	GET       Method = "GET"
	POST      Method = "POST"
	PUT       Method = "PUT"
	DELETE    Method = "DELETE"
	PATCH     Method = "PATCH"
	NAVIGATOR Method = "NAVIGATOR"
	LOCATION  Method = "LOCATION"
)

// 注意：Url结构体已移除，请使用UrlInterface接口
// 业务代码应定义自己的类型并实现UrlInterface接口

// HttpClientConfig 客户端配置
type HttpClientConfig struct {
	BaseUrl                   string
	ClientId                  string
	DuplicateSubmitTimeWindow int64 // 毫秒
	Timeout                   time.Duration
	// EnableHeaderAutoManagement 是否启用请求头自动管理（从内存存储读取和保存），默认true
	EnableHeaderAutoManagement bool
	// HeaderStorageKey 内存存储中存储请求头的键名，默认"http_headers"
	HeaderStorageKey string
}

// RequestInfo 请求信息
type requestInfo struct {
	Timestamp int64
	Url       string
}

// HttpClient HTTP客户端
type HttpClient struct {
	config           HttpClientConfig
	eccModule        *eccCryptoModule
	duplicateModule  *duplicateSubmitModule
	httpClient       *http.Client
	cachedHeaders    map[string]string // 缓存的请求头
	mu               sync.RWMutex     // 保护cachedHeaders的锁
}

// 系统内置的请求头，这些不应该被保存
var systemHeaders = map[string]bool{
	"content-type": true, "content-length": true, "cache-control": true, "expires": true,
	"date": true, "server": true, "connection": true, "transfer-encoding": true,
	"accept": true, "accept-encoding": true, "accept-language": true, "referer": true,
	"user-agent": true, "origin": true,
	"x-client-id": true, "x-client-timestamp": true, "x-client-public-key": true,
	"x-gateway-keyid": true, "x-encrypted-data": true, "x-response-encrypted": true,
	"access-control-allow-origin": true, "access-control-allow-methods": true,
	"access-control-allow-headers": true, "access-control-expose-headers": true,
	"access-control-max-age": true, "access-control-allow-credentials": true,
}

// NewHttpClient 创建HTTP客户端
func NewHttpClient(config HttpClientConfig) *HttpClient {
	if config.BaseUrl == "" {
		config.BaseUrl = "https://your-gateway.com"
	}
	if config.ClientId == "" {
		config.ClientId = fmt.Sprintf("go-client-%d", time.Now().UnixNano())
	}
	if config.DuplicateSubmitTimeWindow == 0 {
		config.DuplicateSubmitTimeWindow = 3000
	}
	if config.Timeout == 0 {
		config.Timeout = 30 * time.Second
	}
	if !config.EnableHeaderAutoManagement {
		config.EnableHeaderAutoManagement = true
	}
	if config.HeaderStorageKey == "" {
		config.HeaderStorageKey = "http_headers"
	}

	return &HttpClient{
		config:          config,
		eccModule:       newEccCryptoModule(),
		duplicateModule: newDuplicateSubmitModule(config.DuplicateSubmitTimeWindow),
		httpClient: &http.Client{
			Timeout: config.Timeout,
		},
		cachedHeaders: make(map[string]string),
	}
}

// RequestOptions 请求选项
type RequestOptions struct {
	Body      interface{}
	Headers   map[string]string
	RequestId string
}

// Request 发送HTTP请求（泛型版本）
//
// url: 实现UrlInterface接口的类型（业务代码定义）
// options: 请求选项（可选）
// 返回完整的ApiResult[T]对象
func Request[T any](c *HttpClient, url UrlInterface, options *RequestOptions) (*ApiResult[T], error) {
	fullUrl := GetFullUrl(url, c.config.BaseUrl)
	
	// 1. 防重复提交检查
	var requestId string
	if url.NeedDuplicateCheck() {
		if options != nil && options.RequestId != "" {
			requestId = options.RequestId
		} else {
			requestId = c.duplicateModule.GenerateRequestId(fullUrl, string(url.Method()), options.Body)
		}

		if c.duplicateModule.IsDuplicateRequest(requestId) {
			return nil, errors.New("DUPLICATE_REQUEST")
		}
		c.duplicateModule.RecordRequest(requestId, fullUrl)
	}

	// 2. 发送请求
	var resp *http.Response
	var err error

	if url.NeedEncryption() {
		resp, err = c.sendEncryptedRequest(fullUrl, string(url.Method()), options)
	} else {
		resp, err = c.sendPlainRequest(fullUrl, string(url.Method()), options)
	}

	// 3. 清理防重复提交记录
	defer func() {
		if requestId != "" && err == nil {
			c.duplicateModule.ClearRequest(requestId)
		}
	}()

	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	// 4. 自动保存响应头到内存存储（在解析响应体之前，确保响应头可用）
	if c.config.EnableHeaderAutoManagement {
		c.saveResponseHeaders(resp)
	}

	// 5. 处理响应（完整透传ApiResult[T]，不做任何处理）
	return handleResponse[T](c, resp, url.NeedEncryption())
}

// RequestLegacy 发送HTTP请求（向后兼容的非泛型版本）
//
// url: 实现UrlInterface接口的类型（业务代码定义）
// options: 请求选项（可选）
// 返回interface{}，需要手动转换为ApiResult
// @deprecated 推荐使用Request[T]泛型版本
func (c *HttpClient) RequestLegacy(url UrlInterface, options *RequestOptions) (interface{}, error) {
	result, err := Request[interface{}](c, url, options)
	if err != nil {
		return nil, err
	}
	return result, nil
}

// sendEncryptedRequest 发送加密请求
func (c *HttpClient) sendEncryptedRequest(url, method string, options *RequestOptions) (*http.Response, error) {
	// 确保已初始化
	if !c.eccModule.IsInitialized() {
		if err := c.eccModule.ExchangeKeys(c.config.BaseUrl, c.config.ClientId); err != nil {
			return nil, err
		}
	}

	req, err := http.NewRequest(method, url, nil)
	if err != nil {
		return nil, err
	}

	// 读取缓存的请求头（包括token等业务请求头）
	cachedHeaders := c.loadCachedHeaders()

	// 设置请求头
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Client-Id", c.config.ClientId)
	req.Header.Set("X-Gateway-KeyId", c.eccModule.gatewayKeyId)
	req.Header.Set("X-Client-Timestamp", fmt.Sprintf("%d", time.Now().UnixMilli()))

	// 添加缓存的请求头（包括token）
	for k, v := range cachedHeaders {
		req.Header.Set(k, v)
	}

	if options != nil && options.Headers != nil {
		for k, v := range options.Headers {
			req.Header.Set(k, v)
		}
	}

	// 加密请求体
	if options != nil && options.Body != nil && method != "GET" {
		jsonData, err := json.Marshal(options.Body)
		if err != nil {
			return nil, err
		}

		encryptedData, err := c.eccModule.Encrypt(string(jsonData))
		if err != nil {
			return nil, err
		}

		req.Header.Set("X-Encrypted-Data", encryptedData)
		req.Body = io.NopCloser(bytes.NewBuffer(jsonData))
		req.ContentLength = int64(len(jsonData))
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}

	// 处理423状态码
	if resp.StatusCode == 423 {
		var result map[string]interface{}
		json.NewDecoder(resp.Body).Decode(&result)
		resp.Body.Close()

		if needReHandshake, ok := result["needReHandshake"].(bool); ok && needReHandshake {
			keyId := result["keyId"].(string)
			gatewayPublicKey := result["gatewayPublicKey"].(string)
			
			if err := c.eccModule.ReHandshake(keyId, gatewayPublicKey); err != nil {
				return nil, err
			}

			// 重试
			return c.sendEncryptedRequest(url, method, options)
		}
	}

	return resp, nil
}

// sendPlainRequest 发送普通请求
func (c *HttpClient) sendPlainRequest(url, method string, options *RequestOptions) (*http.Response, error) {
	var bodyReader io.Reader
	if options != nil && options.Body != nil && method != "GET" {
		jsonData, err := json.Marshal(options.Body)
		if err != nil {
			return nil, err
		}
		bodyReader = bytes.NewBuffer(jsonData)
	}

	req, err := http.NewRequest(method, url, bodyReader)
	if err != nil {
		return nil, err
	}

	// 读取缓存的请求头（包括token等业务请求头）
	cachedHeaders := c.loadCachedHeaders()

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Client-Timestamp", fmt.Sprintf("%d", time.Now().UnixMilli()))

	// 添加缓存的请求头（包括token）
	for k, v := range cachedHeaders {
		req.Header.Set(k, v)
	}

	if options != nil && options.Headers != nil {
		for k, v := range options.Headers {
			req.Header.Set(k, v)
		}
	}

	return c.httpClient.Do(req)
}

// handleResponse 处理响应（完整透传ApiResult[T]，不做任何处理）
func handleResponse[T any](c *HttpClient, resp *http.Response, needDecryption bool) (*ApiResult[T], error) {
	// 先读取响应体（只能读取一次）
	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	// 解密响应
	if needDecryption && resp.Header.Get("X-Response-Encrypted") == "true" {
		decrypted, err := c.eccModule.Decrypt(string(bodyBytes))
		if err != nil {
			return nil, err
		}
		bodyBytes = []byte(decrypted)
	}

	if resp.StatusCode != http.StatusOK {
		if resp.StatusCode == 429 {
			var errorData map[string]interface{}
			json.Unmarshal(bodyBytes, &errorData)
			if code, ok := errorData["code"].(string); ok && code == "DUPLICATE_SUBMIT" {
				return nil, errors.New("DUPLICATE_SUBMIT")
			}
		}
		return nil, fmt.Errorf("HTTP %d: %s", resp.StatusCode, resp.Status)
	}

	// 完整透传ApiResult[T]，不做任何处理
	var result ApiResult[T]
	if len(bodyBytes) > 0 {
		if err := json.Unmarshal(bodyBytes, &result); err != nil {
			return nil, fmt.Errorf("响应解析失败: %v", err)
		}
	}
	return &result, nil
}

// loadCachedHeaders 从内存存储读取缓存的请求头
func (c *HttpClient) loadCachedHeaders() map[string]string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	
	result := make(map[string]string)
	for k, v := range c.cachedHeaders {
		result[k] = v
	}
	return result
}

// saveResponseHeaders 保存响应头到内存存储
// 只保存业务相关的请求头，排除系统内置的header
// 网关签发的token（x-rd-request-apitoken）等业务请求头会自动保存
func (c *HttpClient) saveResponseHeaders(resp *http.Response) {
	businessHeaders := make(map[string]string)
	
	// 遍历所有响应头
	for key, values := range resp.Header {
		lowerKey := strings.ToLower(key)
		
		// 排除系统内置的header，其他所有业务请求头都保存（包括x-rd-request-apitoken等）
		if !systemHeaders[lowerKey] && len(values) > 0 {
			businessHeaders[key] = values[0] // 取第一个值
		}
	}
	
	// 如果有业务请求头，保存到内存存储
	if len(businessHeaders) > 0 {
		c.mu.Lock()
		defer c.mu.Unlock()
		
		// 合并新的响应头（响应头的值会覆盖现有的值）
		for k, v := range businessHeaders {
			c.cachedHeaders[k] = v
		}
		fmt.Printf("[HttpClient] 已保存响应头到内存存储: %v\n", businessHeaders)
	}
}

// InitializeEncryption 手动初始化加密
func (c *HttpClient) InitializeEncryption() error {
	return c.eccModule.ExchangeKeys(c.config.BaseUrl, c.config.ClientId)
}

// Cleanup 清理资源
func (c *HttpClient) Cleanup() {
	c.eccModule.Cleanup()
	c.duplicateModule.ClearAll()
}

// ==================== ECC加密模块 ====================

type eccCryptoModule struct {
	privateKey       *ecdh.PrivateKey
	sharedSecret     []byte
	gatewayPublicKey []byte
	gatewayKeyId     string
	mu               sync.RWMutex
}

func newEccCryptoModule() *eccCryptoModule {
	return &eccCryptoModule{}
}

// ExchangeKeys 密钥交换
func (e *eccCryptoModule) ExchangeKeys(baseUrl, clientId string) error {
	e.mu.Lock()
	defer e.mu.Unlock()

	// 生成客户端密钥对
	curve := ecdh.P256()
	privateKey, err := curve.GenerateKey(rand.Reader)
	if err != nil {
		return err
	}
	e.privateKey = privateKey

	publicKeyBytes := privateKey.PublicKey().Bytes()
	clientPublicKey := base64.StdEncoding.EncodeToString(publicKeyBytes)

	// 发送密钥交换请求
	req, _ := http.NewRequest("POST", baseUrl+"/api/crypto/exchange", nil)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Client-Public-Key", clientPublicKey)
	req.Header.Set("X-Client-Id", clientId)

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	var result map[string]string
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return err
	}

	e.gatewayKeyId = result["keyId"]
	gatewayPublicKeyB64 := result["gatewayPublicKey"]

	// 导入网关公钥
	gatewayPublicKeyBytes, err := base64.StdEncoding.DecodeString(gatewayPublicKeyB64)
	if err != nil {
		return err
	}

	gatewayPublicKey, err := curve.NewPublicKey(gatewayPublicKeyBytes)
	if err != nil {
		return err
	}

	// 计算共享密钥
	e.sharedSecret, err = privateKey.ECDH(gatewayPublicKey)
	if err != nil {
		return err
	}

	fmt.Println("[ECC] 密钥交换成功, KeyId:", e.gatewayKeyId)
	return nil
}

// ReHandshake 重新握手
func (e *eccCryptoModule) ReHandshake(keyId, gatewayPublicKeyB64 string) error {
	e.mu.Lock()
	defer e.mu.Unlock()

	e.gatewayKeyId = keyId

	curve := ecdh.P256()
	gatewayPublicKeyBytes, err := base64.StdEncoding.DecodeString(gatewayPublicKeyB64)
	if err != nil {
		return err
	}

	gatewayPublicKey, err := curve.NewPublicKey(gatewayPublicKeyBytes)
	if err != nil {
		return err
	}

	e.sharedSecret, err = e.privateKey.ECDH(gatewayPublicKey)
	return err
}

// Encrypt AES-GCM加密
func (e *eccCryptoModule) Encrypt(plaintext string) (string, error) {
	e.mu.RLock()
	defer e.mu.RUnlock()

	if e.sharedSecret == nil {
		return "", errors.New("共享密钥未初始化")
	}

	block, err := aes.NewCipher(e.sharedSecret[:32])
	if err != nil {
		return "", err
	}

	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}

	nonce := make([]byte, gcm.NonceSize())
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return "", err
	}

	ciphertext := gcm.Seal(nonce, nonce, []byte(plaintext), nil)
	return base64.StdEncoding.EncodeToString(ciphertext), nil
}

// Decrypt AES-GCM解密
func (e *eccCryptoModule) Decrypt(ciphertext string) (string, error) {
	e.mu.RLock()
	defer e.mu.RUnlock()

	if e.sharedSecret == nil {
		return "", errors.New("共享密钥未初始化")
	}

	data, err := base64.StdEncoding.DecodeString(ciphertext)
	if err != nil {
		return "", err
	}

	block, err := aes.NewCipher(e.sharedSecret[:32])
	if err != nil {
		return "", err
	}

	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}

	nonceSize := gcm.NonceSize()
	if len(data) < nonceSize {
		return "", errors.New("密文数据太短")
	}

	nonce, ciphertextBytes := data[:nonceSize], data[nonceSize:]
	plaintext, err := gcm.Open(nil, nonce, ciphertextBytes, nil)
	if err != nil {
		return "", err
	}

	return string(plaintext), nil
}

// IsInitialized 检查是否已初始化
func (e *eccCryptoModule) IsInitialized() bool {
	e.mu.RLock()
	defer e.mu.RUnlock()
	return e.sharedSecret != nil && e.gatewayKeyId != ""
}

// Cleanup 清理资源
func (e *eccCryptoModule) Cleanup() {
	e.mu.Lock()
	defer e.mu.Unlock()
	e.privateKey = nil
	e.sharedSecret = nil
	e.gatewayPublicKey = nil
	e.gatewayKeyId = ""
}

// ==================== 防重复提交模块 ====================

type duplicateSubmitModule struct {
	requestQueue map[string]*requestInfo
	timeWindow   int64
	mu           sync.RWMutex
}

func newDuplicateSubmitModule(timeWindow int64) *duplicateSubmitModule {
	return &duplicateSubmitModule{
		requestQueue: make(map[string]*requestInfo),
		timeWindow:   timeWindow,
	}
}

// GenerateRequestId 生成请求ID
func (d *duplicateSubmitModule) GenerateRequestId(url, method string, body interface{}) string {
	d.mu.Lock()
	defer d.mu.Unlock()

	timestamp := time.Now().UnixMilli() / d.timeWindow * d.timeWindow
	
	bodyStr := ""
	if body != nil {
		bodyBytes, _ := json.Marshal(body)
		bodyStr = string(bodyBytes)
	}

	data := fmt.Sprintf("%s%s%d%s", url, method, timestamp, bodyStr)
	hash := md5.Sum([]byte(data))
	return fmt.Sprintf("%x", hash)
}

// IsDuplicateRequest 检查是否重复
func (d *duplicateSubmitModule) IsDuplicateRequest(requestId string) bool {
	d.mu.RLock()
	defer d.mu.RUnlock()

	info, exists := d.requestQueue[requestId]
	if !exists {
		return false
	}

	return time.Now().UnixMilli()-info.Timestamp < d.timeWindow
}

// RecordRequest 记录请求
func (d *duplicateSubmitModule) RecordRequest(requestId, url string) {
	d.mu.Lock()
	defer d.mu.Unlock()

	d.requestQueue[requestId] = &requestInfo{
		Timestamp: time.Now().UnixMilli(),
		Url:       url,
	}
	d.cleanupExpired()
}

// ClearRequest 清理请求
func (d *duplicateSubmitModule) ClearRequest(requestId string) {
	d.mu.Lock()
	defer d.mu.Unlock()
	delete(d.requestQueue, requestId)
}

// ClearAll 清理所有
func (d *duplicateSubmitModule) ClearAll() {
	d.mu.Lock()
	defer d.mu.Unlock()
	d.requestQueue = make(map[string]*requestInfo)
}

func (d *duplicateSubmitModule) cleanupExpired() {
	now := time.Now().UnixMilli()
	expiredTime := now - d.timeWindow

	for id, info := range d.requestQueue {
		if info.Timestamp < expiredTime {
			delete(d.requestQueue, id)
		}
	}
}

