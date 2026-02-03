<template>
  <div class="auth-page">
    <div class="auth-overlay" />
    <div class="auth-card">
      <div class="auth-brand">
        <div class="brand-mark">咸鱼之王</div>
      </div>

      <form class="auth-form" @submit.prevent="handleLogin">
        <div class="input-row">
          <input v-model="form.username" autocomplete="username" placeholder="用户名" />
          <span class="icon">👤</span>
        </div>

        <div class="input-row">
          <input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            placeholder="密码"
          />
          <span class="icon">🔒</span>
        </div>

        <div class="captcha-row">
          <input v-model="form.captchaAnswer" placeholder="答案" />
          <button type="button" class="captcha-chip" @click="loadCaptcha">
            {{ form.captchaQuestion || "点击生成" }}
          </button>
        </div>

        <div class="options">
          <label class="remember">
            <input type="checkbox" v-model="form.remember" />
            记住我
          </label>
          <span class="link" @click="handleForgot">忘记密码？</span>
        </div>

        <button class="primary" :disabled="loading">
          确认登录
        </button>
      </form>

      <div v-if="notice" class="notice">{{ notice }}</div>

      <div class="footer">
        <button class="link" @click="router.push('/register')">立即注册</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();
const form = reactive({
  username: "",
  password: "",
  remember: false,
  captchaId: "",
  captchaAnswer: "",
  captchaQuestion: "",
});
const loading = ref(false);
const notice = ref("");

const loadCaptcha = async () => {
  try {
    const response = await fetch("/api/v1/auth/captcha");
    const data = await response.json();
    if (response.ok && data.success && data.data?.captchaId) {
      form.captchaId = data.data.captchaId;
      form.captchaQuestion = data.data.question;
      form.captchaAnswer = "";
    }
  } catch (error) {
    // ignore
  }
};

const handleLogin = async () => {
  if (!form.username || !form.password) {
    notice.value = "请输入用户名和密码。";
    return;
  }
  if (!form.captchaId || !form.captchaAnswer) {
    notice.value = "请完成验证码。";
    return;
  }
  loading.value = true;
  try {
    const response = await fetch("/api/v1/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username: form.username,
        password: form.password,
        captchaId: form.captchaId,
        captchaAnswer: Number(form.captchaAnswer),
      }),
    });
    const data = await response.json();
    if (!response.ok || !data.success || !data.data?.token) {
      await loadCaptcha();
      throw new Error(data.message || "登录失败");
    }
    localStorage.setItem("authToken", data.data.token);
    localStorage.setItem(
      "authUser",
      JSON.stringify({
        id: data.data.id,
        username: data.data.username,
        email: data.data.email,
      }),
    );
    notice.value = `欢迎回来，${data.data.username}`;
    router.push("/dashboard");
  } catch (error) {
    const raw = String(error.message || "").toLowerCase();
    if (raw.includes("invalid credentials") || raw.includes("账号") || raw.includes("密码")) {
      notice.value = "账号或密码不正确，请重试。";
    } else if (raw.includes("captcha") || raw.includes("验证码")) {
      notice.value = "验证码不正确，请重新输入。";
    } else {
      notice.value = "登录失败，请稍后再试。";
    }
  } finally {
    loading.value = false;
  }
};

const handleForgot = () => {
  notice.value = "找回密码功能暂未开放。";
};

loadCaptcha();
</script>

<style scoped>
.auth-page {
  font-family: "ZCOOL XiaoWei", "Noto Serif SC", "STKaiti", serif;
}

.auth-page {
  min-height: 100dvh;
  display: grid;
  place-items: center;
  background:
    radial-gradient(520px 320px at 20% 20%, rgba(56, 189, 248, 0.25), transparent 70%),
    radial-gradient(560px 360px at 80% 10%, rgba(167, 139, 250, 0.28), transparent 70%),
    radial-gradient(680px 420px at 70% 80%, rgba(14, 165, 233, 0.22), transparent 70%),
    linear-gradient(135deg, #0b1026 0%, #121a3a 45%, #0b1026 100%);
  position: relative;
  color: #f8fafc;
  padding: 32px;
  width: 100vw;
  overflow: hidden;
}

.auth-overlay {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.06), transparent 55%),
    radial-gradient(circle at 75% 25%, rgba(56, 189, 248, 0.18), transparent 50%);
  backdrop-filter: blur(2px);
}

.auth-page::before,
.auth-page::after {
  content: "";
  position: absolute;
  inset: -20%;
  background-image:
    radial-gradient(circle, rgba(255, 255, 255, 0.5) 0 2px, transparent 3px),
    radial-gradient(circle, rgba(56, 189, 248, 0.6) 0 1.5px, transparent 3px),
    radial-gradient(circle, rgba(167, 139, 250, 0.5) 0 2px, transparent 3px);
  background-size: 140px 140px, 220px 220px, 180px 180px;
  background-position: 0 0, 60px 80px, 120px 20px;
  opacity: 0.5;
  animation: ion-drift 14s linear infinite;
  pointer-events: none;
}

.auth-page::after {
  background-size: 200px 200px, 260px 260px, 160px 160px;
  background-position: 40px 120px, 120px 40px, 10px 200px;
  opacity: 0.35;
  animation-duration: 22s;
  animation-direction: reverse;
}

.auth-card {
  position: relative;
  z-index: 1;
  width: min(460px, 92vw);
  background: linear-gradient(160deg, rgba(30, 64, 175, 0.25), rgba(15, 23, 42, 0.82));
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 20px;
  padding: 30px 28px;
  box-shadow: 0 40px 120px rgba(5, 8, 18, 0.55);
  backdrop-filter: blur(20px);
  animation: card-rise 0.6s ease-out;
}

@media (min-width: 1024px) {
  .auth-card {
    width: 520px;
    padding: 36px 34px;
  }

  .auth-brand h1 {
    font-size: 24px;
  }

  .auth-brand p {
    font-size: 14px;
  }

  .auth-form label {
    font-size: 13px;
  }

  .input-row input {
    padding: 12px 44px 12px 14px;
    border-radius: 12px;
  }

  .primary {
    padding: 12px 16px;
  }
}

.auth-brand {
  display: flex;
  align-items: center;
  margin-bottom: 18px;
  justify-content: center;
}

.brand-mark {
  width: 76px;
  height: 76px;
  border-radius: 50%;
  background: conic-gradient(from 120deg, #38bdf8, #2563eb, #7c3aed, #38bdf8);
  display: grid;
  place-items: center;
  font-weight: 700;
  letter-spacing: 1px;
  color: #0f172a;
  box-shadow: 0 10px 30px rgba(56, 189, 248, 0.35);
}

.auth-brand h1 {
  margin: 0;
  font-size: 22px;
}

.auth-brand p {
  margin: 4px 0 0;
  color: #cbd5f5;
  font-size: 13px;
}

.auth-form {
  display: grid;
  gap: 14px;
}

.input-row {
  position: relative;
}

.input-row input {
  width: 100%;
  padding: 10px 38px 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: rgba(2, 6, 23, 0.65);
  color: #f8fafc;
  transition: border 0.2s ease, box-shadow 0.2s ease;
}

.input-row input:focus {
  outline: none;
  border-color: rgba(56, 189, 248, 0.7);
  box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.18);
}

.input-row .icon {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0.7;
}

.captcha-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
}

.captcha-row input {
  width: 100%;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: rgba(2, 6, 23, 0.65);
  color: #f8fafc;
}

.captcha-chip {
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: rgba(15, 23, 42, 0.7);
  color: #e2e8f0;
  border-radius: 10px;
  padding: 0 12px;
  cursor: pointer;
  white-space: nowrap;
}

.options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #94a3b8;
}

.remember {
  display: flex;
  gap: 6px;
  align-items: center;
}

.primary {
  padding: 10px 14px;
  border-radius: 999px;
  border: none;
  background: linear-gradient(135deg, #38bdf8, #2563eb);
  color: #0f172a;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 30px rgba(56, 189, 248, 0.35);
}

.notice {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(2, 6, 23, 0.6);
  border: 1px solid rgba(56, 189, 248, 0.3);
  color: #e2e8f0;
  font-size: 12px;
}

.footer {
  display: flex;
  justify-content: center;
  margin-top: 16px;
  font-size: 12px;
}

.link {
  background: none;
  border: none;
  color: #38bdf8;
  cursor: pointer;
}

.log {
  margin-top: 14px;
  background: rgba(2, 6, 23, 0.5);
  border-radius: 10px;
  padding: 10px;
  min-height: 48px;
}

.log p {
  margin: 0 0 6px;
  color: #cbd5f5;
}

@keyframes card-rise {
  from {
    transform: translateY(12px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

@keyframes ion-drift {
  0% {
    transform: translate3d(0, 0, 0);
  }
  50% {
    transform: translate3d(-40px, -30px, 0);
  }
  100% {
    transform: translate3d(0, 0, 0);
  }
}
</style>
