<template>
  <div class="dashboard-page">
    <button
      v-if="authUser"
      class="logout-fab"
      type="button"
      @click="handleLogout"
    >
      退出登录
    </button>
    <div class="content">
      <div class="header">
        <div>
          <h1>上传 Bin</h1>
          <p v-if="authUser">
            欢迎，{{ authUser.username }}，在这里上传你的 Bin 文件。
          </p>
          <p v-else>请先登录。</p>
        </div>
        <div class="header-actions">
          <button class="ghost" type="button" @click="router.push('/profile')">
            个人信息管理
          </button>
          <button class="ghost" type="button" @click="router.push('/wx-login')">
            微信扫码登录
          </button>
        </div>
      </div>

      <div class="section">
        <div class="grid">
          <label class="field">
            名称
            <input v-model="form.name" placeholder="例如：主号" />
          </label>
          <label class="field">
            Bin 文件
            <input type="file" accept=".bin" @change="handleFileChange" />
          </label>
        </div>
        <div class="actions">
          <button :disabled="loading" @click="handleUploadBin">保存 Bin</button>
        </div>
        <p
          v-if="statusMessage"
          class="status-message"
          :class="{ error: statusIsError }"
        >
          {{ statusMessage }}
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();
const form = ref({
  name: "",
});

const authToken = ref(localStorage.getItem("authToken") || "");
const authUser = ref(
  localStorage.getItem("authUser")
    ? JSON.parse(localStorage.getItem("authUser"))
    : null
);

const binFile = ref(null);
const loading = ref(false);
const statusMessage = ref("");
const statusIsError = ref(false);

const authHeaders = () =>
  authToken.value ? { Authorization: `Bearer ${authToken.value}` } : {};

const ensureAuth = async () => {
  if (!authToken.value) {
    router.push("/login");
    return false;
  }
  return true;
};

const handleFileChange = (event) => {
  const file = event.target.files?.[0];
  binFile.value = file || null;
};

const handleUploadBin = async () => {
  if (!(await ensureAuth())) return;
  if (!binFile.value) {
    statusMessage.value = "请选择 bin 文件。";
    statusIsError.value = true;
    return;
  }
  loading.value = true;
  statusMessage.value = "";
  statusIsError.value = false;
  try {
    const formData = new FormData();
    formData.append("file", binFile.value);
    formData.append("name", form.value.name || "未命名账号");

    const response = await fetch("/api/v1/xyzw/bins", {
      method: "POST",
      headers: { ...authHeaders() },
      body: formData,
    });
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || "保存 Bin 失败");
    }
    statusMessage.value = "Bin 已保存，正在返回个人信息管理。";
    statusIsError.value = false;
    binFile.value = null;
    form.value = { name: "" };
    setTimeout(() => router.push("/profile"), 600);
  } catch (error) {
    statusMessage.value = `保存 Bin 失败: ${error.message}`;
    statusIsError.value = true;
  } finally {
    loading.value = false;
  }
};

const handleLogout = async () => {
  try {
    if (authToken.value) {
      await fetch("/api/v1/auth/logout", {
        method: "POST",
        headers: { ...authHeaders() },
      });
    }
  } catch (error) {
    // ignore
  }
  authToken.value = "";
  authUser.value = null;
  localStorage.removeItem("authToken");
  localStorage.removeItem("authUser");
  router.push("/login");
};
</script>

<style scoped>
.dashboard-page {
  min-height: 100dvh;
  padding: 32px 22px 72px;
  position: relative;
  overflow: hidden;
  background: radial-gradient(
      420px 320px at 12% 12%,
      rgba(125, 211, 252, 0.45),
      transparent 70%
    ),
    radial-gradient(
      520px 360px at 88% 8%,
      rgba(199, 210, 254, 0.5),
      transparent 70%
    ),
    radial-gradient(
      640px 420px at 70% 80%,
      rgba(224, 231, 255, 0.6),
      transparent 70%
    ),
    linear-gradient(140deg, #f8fafc 0%, #eef2ff 45%, #f1f5f9 100%);
  color: #0f172a;
  font-family: "Manrope", "Noto Sans SC", "PingFang SC", sans-serif;
}

.dashboard-page::before,
.dashboard-page::after {
  content: "";
  position: absolute;
  inset: -20%;
  background-image: radial-gradient(
      circle,
      rgba(59, 130, 246, 0.25) 0 2px,
      transparent 3px
    ),
    radial-gradient(circle, rgba(14, 116, 144, 0.2) 0 1.5px, transparent 3px),
    radial-gradient(circle, rgba(99, 102, 241, 0.18) 0 2px, transparent 3px);
  background-size: 160px 160px, 260px 260px, 200px 200px;
  background-position: 0 0, 80px 40px, 120px 120px;
  opacity: 0.45;
  pointer-events: none;
  z-index: 0;
}

.dashboard-page::after {
  opacity: 0.28;
  background-position: 40px 120px, 120px 20px, 200px 160px;
}

.content {
  max-width: 900px;
  margin: 0 auto;
  position: relative;
  z-index: 1;
}

.header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.ghost {
  background: rgba(255, 255, 255, 0.2);
  color: #0f172a;
  border: 1px solid rgba(148, 163, 184, 0.6);
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
  backdrop-filter: blur(8px);
}

.logout-fab {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 50;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.82);
  border: 1px solid rgba(148, 163, 184, 0.4);
  color: #e2e8f0;
  cursor: pointer;
  backdrop-filter: blur(8px);
}

.section {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid rgba(148, 163, 184, 0.4);
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.field {
  display: grid;
  gap: 6px;
  font-size: 13px;
  color: rgba(15, 23, 42, 0.72);
}

.field input {
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.5);
  background: rgba(255, 255, 255, 0.75);
  color: #0f172a;
}

.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin: 12px 0 0;
}

.actions button {
  padding: 8px 12px;
  border-radius: 8px;
  border: none;
  cursor: pointer;
  background: #0f172a;
  color: #f8fafc;
  font-weight: 600;
}

.status-message {
  margin-top: 10px;
  font-size: 12px;
  color: #0f172a;
}

.status-message.error {
  color: #b91c1c;
}

@media (max-width: 720px) {
  .grid {
    grid-template-columns: 1fr;
  }

  .header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
