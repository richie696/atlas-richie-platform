// Package main Go风格的客户端使用示例
// 展示如何使用业务代码定义的Url枚举（类型别名+常量+方法集）
//
// Author: richie696
// Version: 2.0
// Since: 2025-11-01
package main

import (
	"fmt"
	"log"
	
	"your-module/httpclient"
	"your-module/httpclient/example"
)

func main() {
	// 创建HTTP客户端
	client := httpclient.NewHttpClient(httpclient.HttpClientConfig{
		BaseUrl:                   "https://your-gateway.com",
		ClientId:                  "go-app-client",
		DuplicateSubmitTimeWindow: 3000,
	})
	defer client.Cleanup()

	// 示例1: 用户登录（加密）
	loginExample(client)

	// 示例2: 订单提交（加密 + 防重复提交）
	orderSubmitExample(client)

	// 示例3: 获取菜单（不加密，不防重复）
	menuExample(client)
	
	// 示例4: 演示Url类型的方法
	urlMethodsExample()
}

// loginExample 登录示例
func loginExample(client *httpclient.HttpClient) {
	fmt.Println("\n=== 示例1: 用户登录 ===")

	type User struct {
		UserId   string `json:"userId"`
		Username string `json:"username"`
		Email    string `json:"email"`
	}

	// 直接使用enum常量，简洁优雅！
	result, err := httpclient.Request[User](client, example.UserLogin, &httpclient.RequestOptions{
		Body: map[string]string{
			"username": "testuser",
			"password": "testpass",
		},
	})

	if err != nil {
		log.Printf("登录失败: %v\n", err)
		return
	}

	if result.IsSuccess() {
		userData, err := result.ExtractData()
		if err != nil {
			log.Printf("数据提取失败: %v\n", err)
			return
		}
		fmt.Printf("登录成功: %+v\n", userData)
		// 注意：token现在从响应头自动获取并保存到内存存储，无需手动处理
	} else {
		fmt.Printf("登录失败: %s\n", result.Msg)
	}
}

// orderSubmitExample 订单提交示例
func orderSubmitExample(client *httpclient.HttpClient) {
	fmt.Println("\n=== 示例2: 订单提交 ===")

	type OrderResult struct {
		OrderId     string  `json:"orderId"`
		TotalAmount float64 `json:"totalAmount"`
	}

	orderData := map[string]interface{}{
		"items": []map[string]interface{}{
			{"productId": "P001", "quantity": 2, "price": 99.99},
		},
		"totalAmount": 199.98,
		"address":     "北京市朝阳区xxx路xxx号",
	}

	result, err := httpclient.Request[OrderResult](client, example.OrderSubmit, &httpclient.RequestOptions{
		Body: orderData,
	})

	if err != nil {
		if err.Error() == "DUPLICATE_REQUEST" {
			fmt.Println("订单正在处理中，请勿重复提交")
		} else if err.Error() == "DUPLICATE_SUBMIT" {
			fmt.Println("服务器检测到重复提交")
		} else {
			log.Printf("订单提交失败: %v\n", err)
		}
		return
	}

	if result.IsSuccess() {
		orderData, err := result.ExtractData()
		if err != nil {
			log.Printf("数据提取失败: %v\n", err)
			return
		}
		fmt.Printf("订单提交成功: %+v\n", orderData)
	} else {
		fmt.Printf("订单提交失败: %s\n", result.Msg)
	}
}

// menuExample 菜单查询示例
func menuExample(client *httpclient.HttpClient) {
	fmt.Println("\n=== 示例3: 获取菜单 ===")

	type Menu struct {
		Id    string `json:"id"`
		Name  string `json:"name"`
		Items []any  `json:"items"`
	}

	result, err := httpclient.Request[[]Menu](client, example.MenuAll, nil)
	if err != nil {
		log.Printf("获取菜单失败: %v\n", err)
		return
	}

	if result.IsSuccess() {
		menuData, err := result.ExtractData()
		if err != nil {
			log.Printf("数据提取失败: %v\n", err)
			return
		}
		fmt.Printf("菜单列表: %+v\n", menuData)
	} else {
		fmt.Printf("获取菜单失败: %s\n", result.Msg)
	}
}

// 示例4: 演示Url类型的方法
func urlMethodsExample() {
	fmt.Println("\n=== 示例4: 演示Url类型的方法 ===")
	
	url := example.UserLogin
	
	fmt.Printf("枚举值: %v\n", url)
	fmt.Printf("枚举名称: %s\n", url.Name())
	fmt.Printf("URL路径: %s\n", url.Path())
	fmt.Printf("HTTP方法: %s\n", url.Method())
	fmt.Printf("需要加密: %v\n", url.NeedEncryption())
	fmt.Printf("需要防重复: %v\n", url.NeedDuplicateCheck())
	fmt.Printf("是否写操作: %v\n", url.IsWriteOperation())
	fmt.Printf("完整URL: %s\n", url.GetFullUrl("https://api.example.com"))
}

