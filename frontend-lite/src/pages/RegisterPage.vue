<template>
  <div class="auth-page">
    <div class="auth-overlay" />
    <div class="auth-card">
      <div class="auth-brand">
        <div class="brand-mark">咸鱼之王</div>
      </div>

      <form class="auth-form" @submit.prevent="handleRegister">
        <div class="input-row">
          <input v-model="form.username" autocomplete="username" placeholder="用户名" />
          <span class="icon">👤</span>
        </div>

        <div class="input-row">
          <input v-model="form.email" autocomplete="email" placeholder="邮箱" />
          <span class="icon">✉️</span>
        </div>

        <div class="input-row">
          <input v-model="form.password" type="password" autocomplete="new-password" placeholder="密码" />
          <span class="icon">🔒</span>
        </div>

        <div class="captcha-row">
          <input v-model="form.captchaAnswer" placeholder="答案" />
          <button type="button" class="captcha-chip" @click="loadCaptcha">
            {{ form.captchaQuestion || "点击生成" }}
          </button>
        </div>

        <button class="primary" :disabled="loading">
          确认注册
        </button>
      </form>

      <div class="footer">
        <button class="link" @click="router.push('/login')">返回登录</button>
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
  email: "",
  password: "",
  captchaId: "",
  captchaAnswer: "",
  captchaQuestion: "",
});
const loading = ref(false);

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

const handleRegister = async () => {
  if (!form.username || !form.email || !form.password) {
    alert("请填写用户名、邮箱和密码。");
    return;
  }
  if (!form.captchaId || !form.captchaAnswer) {
    alert("请完成验证码。");
    return;
  }
  loading.value = true;
  try {
    const response = await fetch("/api/v1/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username: form.username,
        email: form.email,
        password: form.password,
        captchaId: form.captchaId,
        captchaAnswer: Number(form.captchaAnswer),
      }),
    });
    const data = await response.json();
    if (!response.ok || !data.success) {
      await loadCaptcha();
      throw new Error(data.message || "注册失败");
    }
    alert("注册成功，即将跳转登录...");
    setTimeout(() => router.push("/login"), 800);
  } catch (error) {
    alert(`注册失败: ${error.message}`);
  } finally {
    loading.value = false;
  }
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
  background: linear-gradient(160deg, rgba(30, 64, 175, 0.22), rgba(15, 23, 42, 0.84));
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
