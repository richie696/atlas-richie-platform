/**
 * Angular登录表单组件
 * 
 * @author richie696
 * @version 1.0
 * @since 2025-11-01
 */

import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * 登录表单组件
 * Angular 17+ Standalone Component with Signals
 */
@Component({
    selector: 'app-login-form',
    standalone: true,
    imports: [CommonModule, FormsModule],
    template: `
        <div class="login-form">
            <h2>用户登录</h2>

            <form (ngSubmit)="handleSubmit()" #loginForm="ngForm">
                <div class="form-group">
                    <label for="username">用户名</label>
                    <input
                        id="username"
                        type="text"
                        name="username"
                        [(ngModel)]="username"
                        placeholder="请输入用户名"
                        [disabled]="authService.loading()"
                        required
                    />
                </div>

                <div class="form-group">
                    <label for="password">密码</label>
                    <input
                        id="password"
                        type="password"
                        name="password"
                        [(ngModel)]="password"
                        placeholder="请输入密码"
                        [disabled]="authService.loading()"
                        required
                    />
                </div>

                @if (authService.error()) {
                    <div class="error-message">
                        {{ authService.error() }}
                    </div>
                }

                <button 
                    type="submit" 
                    [disabled]="authService.loading() || !loginForm.valid"
                >
                    {{ authService.loading() ? '登录中...' : '登录' }}
                </button>
            </form>
        </div>
    `,
    styles: [`
        .login-form {
            max-width: 400px;
            margin: 50px auto;
            padding: 20px;
        }

        .form-group {
            margin-bottom: 15px;
        }

        label {
            display: block;
            margin-bottom: 5px;
        }

        input {
            width: 100%;
            padding: 8px;
            border: 1px solid #ddd;
            border-radius: 4px;
        }

        button {
            width: 100%;
            padding: 10px;
            background-color: #007bff;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
        }

        button:disabled {
            background-color: #ccc;
            cursor: not-allowed;
        }

        .error-message {
            color: red;
            margin-bottom: 10px;
        }
    `]
})
export class LoginFormComponent {
    authService = inject(AuthService);
    private router = inject(Router);

    username = signal('');
    password = signal('');

    async handleSubmit() {
        try {
            await this.authService.login(this.username(), this.password());
            console.log('登录成功');
            this.router.navigate(['/dashboard']);
        } catch (err) {
            console.error('登录失败:', err);
        }
    }
}

