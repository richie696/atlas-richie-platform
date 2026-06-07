/**
 * LoginActivity.kt
 * Android登录Activity示例
 *
 * @author richie696
 * @version 2.0
 * @since 2025-11-01
 */

package com.richie.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.richie.httpclient.*
import kotlinx.coroutines.launch

// 注意：导入业务代码定义的Url枚举（示例中使用example目录下的Url）
// 在实际项目中，应该是：import com.your.app.Url

class LoginActivity : AppCompatActivity() {
    
    private lateinit var client: HttpClient
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 创建HTTP客户端
        client = HttpClient(HttpClientConfig(
            baseUrl = "https://your-gateway.com",
            clientId = "android-app"
        ))
    }
    
    // 登录示例
    private fun login(username: String, password: String) {
        lifecycleScope.launch {
            try {
                data class User(
                    val userId: String,
                    val username: String,
                    val email: String
                )
                
                // 直接使用enum，简洁优雅！
                val result: ApiResult<User> = client.request(
                    Url.UserLogin,  // 业务代码定义的enum
                    body = mapOf(
                        "username" to username,
                        "password" to password
                    )
                )
                
                if (result.isSuccess) {
                    val userData = result.extractData()
                    println("登录成功: $userData")
                    // 注意：token现在从响应头自动获取并保存到SharedPreferences，无需手动处理
                    // 跳转到主页
                } else {
                    showToast("登录失败: ${result.msg ?: "未知错误"}")
                }
                
            } catch (e: DuplicateRequestException) {
                showToast("请求正在处理中，请稍候")
            } catch (e: ApiResultException) {
                showToast("登录失败: ${e.message}")
            } catch (e: Exception) {
                showToast("登录失败: ${e.message}")
            }
        }
    }
    
    // 订单提交示例
    private fun submitOrder(orderData: Map<String, Any>) {
        lifecycleScope.launch {
            try {
                data class OrderResult(
                    val orderId: String,
                    val totalAmount: Double
                )
                
                val result: ApiResult<OrderResult> = client.request(
                    Url.OrderSubmit,  // 业务代码定义的enum
                    body = orderData
                )
                
                if (result.isSuccess) {
                    val orderData = result.extractData()
                    println("订单提交成功: $orderData")
                    showToast("订单提交成功")
                } else {
                    showToast("订单提交失败: ${result.msg ?: "未知错误"}")
                }
                
            } catch (e: DuplicateRequestException) {
                showToast("订单正在处理中，请勿重复提交")
            } catch (e: DuplicateSubmitException) {
                showToast("服务器检测到重复提交")
            } catch (e: ApiResultException) {
                showToast("订单提交失败: ${e.message}")
            } catch (e: Exception) {
                showToast("订单提交失败: ${e.message}")
            }
        }
    }
    
    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        client.cleanup()
    }
}

