<!--
  Vue登录表单组件
  
  @author richie696
  @version 1.0
  @since 2025-11-01
-->

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuth } from '../composables/useAuth';

const router = useRouter();
const { login, loading, error } = useAuth();

const username = ref('');
const password = ref('');

const handleSubmit = async () => {
    try {
        await login(username.value, password.value);
        console.log('登录成功');
        router.push('/dashboard');
    } catch (err) {
        console.error('登录失败:', err);
    }
};
</script>

<template>
    <div class="login-form">
        <h2>用户登录</h2>

        <form @submit.prevent="handleSubmit">
            <div class="form-group">
                <label for="username">用户名</label>
                <input
                    id="username"
                    v-model="username"
                    type="text"
                    placeholder="请输入用户名"
                    :disabled="loading"
                    required
                />
            </div>

            <div class="form-group">
                <label for="password">密码</label>
                <input
                    id="password"
                    v-model="password"
                    type="password"
                    placeholder="请输入密码"
                    :disabled="loading"
                    required
                />
            </div>

            <div v-if="error" class="error-message">
                {{ error }}
            </div>

            <button type="submit" :disabled="loading">
                {{ loading ? '登录中...' : '登录' }}
            </button>
        </form>
    </div>
</template>

<style scoped>
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
    background-color: #42b983;
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
    color: #f56c6c;
    margin-bottom: 10px;
}
</style>

