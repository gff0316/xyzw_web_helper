<template>
  <div class="auth-panel">
    <div class="auth-header">
      <h2>账户登录</h2>
      <p>登录后才能进行 WebSocket 与罐子操作</p>
    </div>

    <div v-if="user" class="auth-user">
      <div class="user-info">
        <div class="avatar">{{ user.username?.slice(0, 1)?.toUpperCase() }}</div>
        <div class="meta">
          <div class="name">{{ user.username }}</div>
          <div class="email">{{ user.email }}</div>
        </div>
      </div>
      <button class="ghost" :disabled="loading" @click="$emit('logout')">
        退出登录
      </button>
    </div>

    <div v-else class="auth-forms">
      <div class="auth-card">
        <h3>登录</h3>
        <input v-model="login.username" placeholder="用户名" />
        <input v-model="login.password" type="password" placeholder="密码" />
        <button :disabled="loading" @click="submitLogin">登录</button>
      </div>

      <div class="auth-card">
        <h3>注册</h3>
        <input v-model="register.username" placeholder="用户名" />
        <input v-model="register.email" placeholder="邮箱" />
        <input v-model="register.password" type="password" placeholder="密码" />
        <button :disabled="loading" @click="submitRegister">注册</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive } from "vue";

const props = defineProps({
  user: { type: Object, default: null },
  loading: { type: Boolean, default: false },
});

const emit = defineEmits(["login", "register", "logout"]);

const login = reactive({
  username: "",
  password: "",
});

const register = reactive({
  username: "",
  email: "",
  password: "",
});

const submitLogin = () => {
  emit("login", { ...login });
};

const submitRegister = () => {
  emit("register", { ...register });
};
</script>

<style scoped>
.auth-panel {
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: linear-gradient(135deg, rgba(30, 41, 59, 0.9), rgba(15, 23, 42, 0.9));
  padding: 20px;
  border-radius: 16px;
  color: #e2e8f0;
  margin-bottom: 20px;
}

.auth-header h2 {
  margin: 0 0 6px;
  font-size: 20px;
}

.auth-header p {
  margin: 0 0 16px;
  color: #94a3b8;
}

.auth-user {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  background: #38bdf8;
  color: #0f172a;
  display: grid;
  place-items: center;
  font-weight: 700;
}

.meta .name {
  font-weight: 600;
}

.meta .email {
  font-size: 12px;
  color: #94a3b8;
}

.auth-forms {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.auth-card {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 12px;
  padding: 14px;
  display: grid;
  gap: 10px;
}

.auth-card h3 {
  margin: 0 0 6px;
  font-size: 16px;
}

.auth-card input {
  width: 100%;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.3);
  background: rgba(2, 6, 23, 0.6);
  color: #e2e8f0;
}

.auth-card button,
.auth-user button {
  padding: 8px 12px;
  border-radius: 8px;
  border: none;
  background: #22d3ee;
  color: #0f172a;
  font-weight: 600;
  cursor: pointer;
}

.auth-user .ghost {
  background: transparent;
  color: #e2e8f0;
  border: 1px solid rgba(148, 163, 184, 0.4);
}

@media (max-width: 720px) {
  .auth-forms {
    grid-template-columns: 1fr;
  }
}
</style>
