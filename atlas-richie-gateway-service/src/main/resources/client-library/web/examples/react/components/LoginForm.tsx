/**
 * React登录表单组件
 * 
 * @author richie696
 * @version 1.0
 * @since 2025-11-01
 */

import React, { useState } from 'react';
import { useAuth } from '../hooks/useAuth';

// 定义用户数据类型（示例）
interface User {
    userId: string;
    username: string;
    email: string;
}

/**
 * 登录表单组件
 * 演示如何使用useAuth Hook进行登录
 */
export function LoginForm() {
    const { login, loading, error } = useAuth<User>();
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        
        try {
            await login(username, password);
            console.log('登录成功');
            // 跳转到首页
            window.location.href = '/dashboard';
        } catch (err) {
            console.error('登录失败:', err);
        }
    };

    return (
        <form onSubmit={handleSubmit} className="login-form">
            <h2>用户登录</h2>
            
            <div className="form-group">
                <label htmlFor="username">用户名</label>
                <input
                    id="username"
                    type="text"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="请输入用户名"
                    disabled={loading}
                    required
                />
            </div>

            <div className="form-group">
                <label htmlFor="password">密码</label>
                <input
                    id="password"
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="请输入密码"
                    disabled={loading}
                    required
                />
            </div>

            {error && (
                <div className="error-message">
                    {error}
                </div>
            )}

            <button type="submit" disabled={loading}>
                {loading ? '登录中...' : '登录'}
            </button>
        </form>
    );
}

export default LoginForm;

