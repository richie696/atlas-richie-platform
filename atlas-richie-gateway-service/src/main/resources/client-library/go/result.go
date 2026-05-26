// Package httpclient 提供Result通用响应结构
// 对应服务端 Java ResultVO<T>
//
// Author: richie696
// Version: 1.0
// Since: 2025-11-11
package httpclient

import (
	"encoding/json"
	"errors"
	"fmt"
)

// I18nDict 国际化字典类型
type I18nDict map[string]map[string]string

// ApiResult 通用响应结果结构
// 对应服务端 ResultVO<T>
//
// 使用示例：
// ```go
// type User struct {
//     ID   int    `json:"id"`
//     Name string `json:"name"`
// }
//
// result := ApiResult[User]{}
// if result.IsSuccess() {
//     fmt.Printf("用户信息: %+v\n", result.Data)
// } else {
//     fmt.Printf("错误: %s\n", result.Msg)
// }
// ```
type ApiResult[T any] struct {
	// Data 结果数据
	Data interface{} `json:"data"`
	// Code 结果代码，成功通常为 "200" 或 "SUCCESS"
	Code string `json:"code"`
	// Msg 错误信息或提示信息
	Msg *string `json:"msg,omitempty"`
	// I18nDict 国际化字典
	I18nDict *I18nDict `json:"i18nDict,omitempty"`
	// Timestamp 时间戳（毫秒）
	Timestamp int64 `json:"timestamp"`
	
	// 内部字段，用于存储解析后的数据
	dataValue T
}

// IsSuccess 判断是否成功
func (r *ApiResult[T]) IsSuccess() bool {
	return r.Code == "200" || r.Code == "SUCCESS" || r.Code == "0"
}

// IsError 判断是否失败
func (r *ApiResult[T]) IsError() bool {
	return !r.IsSuccess()
}

// ExtractData 提取数据，如果失败则返回错误
func (r *ApiResult[T]) ExtractData() (T, error) {
	if !r.IsSuccess() {
		msg := "操作失败"
		if r.Msg != nil {
			msg = *r.Msg
		}
		var zero T
		return zero, fmt.Errorf("操作失败，错误代码: %s, 错误信息: %s", r.Code, msg)
	}
	
	// 如果Data是nil，返回零值
	if r.Data == nil {
		var zero T
		return zero, nil
	}
	
	// 如果dataValue已经设置，直接返回
	var zero T
	if fmt.Sprintf("%v", r.dataValue) != fmt.Sprintf("%v", zero) {
		return r.dataValue, nil
	}
	
	// 尝试将Data转换为T类型
	dataBytes, err := json.Marshal(r.Data)
	if err != nil {
		var zero T
		return zero, fmt.Errorf("数据解析失败: %v", err)
	}
	
	var result T
	if err := json.Unmarshal(dataBytes, &result); err != nil {
		var zero T
		return zero, fmt.Errorf("数据转换失败: %v", err)
	}
	
	r.dataValue = result
	return result, nil
}

// UnmarshalJSON 自定义JSON反序列化
func (r *ApiResult[T]) UnmarshalJSON(data []byte) error {
	// 先解析为通用结构
	var raw struct {
		Data      interface{} `json:"data"`
		Code      string      `json:"code"`
		Msg       *string     `json:"msg,omitempty"`
		I18nDict  *I18nDict   `json:"i18nDict,omitempty"`
		Timestamp int64       `json:"timestamp"`
	}
	
	if err := json.Unmarshal(data, &raw); err != nil {
		return err
	}
	
	r.Data = raw.Data
	r.Code = raw.Code
	r.Msg = raw.Msg
	r.I18nDict = raw.I18nDict
	r.Timestamp = raw.Timestamp
	
	// 尝试解析Data为T类型
	if raw.Data != nil {
		dataBytes, err := json.Marshal(raw.Data)
		if err == nil {
			json.Unmarshal(dataBytes, &r.dataValue)
		}
	}
	
	return nil
}

// IsSuccess 判断ApiResult是否成功（辅助函数）
func IsSuccess[T any](result *ApiResult[T]) bool {
	return result.IsSuccess()
}

// IsError 判断ApiResult是否失败（辅助函数）
func IsError[T any](result *ApiResult[T]) bool {
	return result.IsError()
}

// ExtractData 从ApiResult中提取数据（辅助函数）
func ExtractData[T any](result *ApiResult[T]) (T, error) {
	return result.ExtractData()
}

