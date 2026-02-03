<template>
  <div class="dashboard-page">
    <button v-if="authUser" class="logout-fab" type="button" @click="handleLogout">
      退出登录
    </button>
    <div class="card">
    <div class="header">
      <div>
        <h1>XYZW WebSocket 控制台</h1>
        <p v-if="authUser">欢迎，{{ authUser.username }}，进入系统控制台。</p>
        <p v-else>仅保留 WebSocket 连接与罐子功能，用于对接后端接口。</p>
      </div>
    </div>

    <div class="grid">
      <label class="field">
        账号名称
        <input v-model="form.name" placeholder="例如：主号" />
      </label>
      <label class="field">
        Bin 文件
        <input type="file" accept=".bin" @change="handleFileChange" />
      </label>
      <label class="field">
        服务器名称（可选）
        <input v-model="form.server" placeholder="S1-xxx" />
      </label>
      <label class="field">
        自定义 WS 地址（可选）
        <input v-model="form.wsUrl" placeholder="wss://..." />
      </label>
    </div>

    <div class="actions">
      <button :disabled="loading" @click="handleFetchToken">获取 Token</button>
      <button
        class="secondary"
        :disabled="!token || loading"
        @click="handleConnect"
      >
        建立 WebSocket
      </button>
      <button
        class="ghost"
        :disabled="status !== 'connected'"
        @click="handleDisconnect"
      >
        断开连接
      </button>
      <button
        class="secondary"
        :disabled="status !== 'connected'"
        @click="handleBottleHelper"
      >
        启动/重启罐子
      </button>
    </div>

    <div class="status">
      <strong>连接状态</strong>
      <span :class="statusClass">{{ statusText }}</span>
      <span v-if="token">Token: {{ maskedToken }}</span>
    </div>

    <div class="log">
      <p v-for="(item, index) in logs" :key="index">{{ item }}</p>
      <p v-if="logs.length === 0">暂无日志</p>
    </div>
  </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { g_utils } from "../utils/bonProtocol.js";

const router = useRouter();
const form = reactive({
  name: "",
  server: "",
  wsUrl: "",
});

const authToken = ref(localStorage.getItem("authToken") || "");
const authUser = ref(
  localStorage.getItem("authUser")
    ? JSON.parse(localStorage.getItem("authUser"))
    : null
);
const authValidated = ref(!!authUser.value);

const token = ref("");
const binFile = ref(null);
const loading = ref(false);
const status = ref("disconnected");
const logs = ref([]);

const addLog = (message) => {
  logs.value.unshift(`[${new Date().toLocaleTimeString()}] ${message}`);
};

const statusText = computed(() => {
  switch (status.value) {
    case "connected":
      return "已连接";
    case "connecting":
      return "连接中";
    case "error":
      return "连接异常";
    default:
      return "未连接";
  }
});

const statusClass = computed(() => ({
  connected: status.value === "connected",
  connecting: status.value === "connecting",
  error: status.value === "error",
}));

const maskedToken = computed(() => {
  if (!token.value) return "";
  if (token.value.length <= 8) return token.value;
  return `${token.value.slice(0, 6)}...${token.value.slice(-4)}`;
});

const decodeTokenFromBase64 = (encoded) => {
  const binary = atob(encoded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  const msg = g_utils.parse(bytes);
  const data = msg.getData();
  const currentTime = Date.now();
  const sessId = currentTime * 100 + Math.floor(Math.random() * 100);
  const connId = currentTime + Math.floor(Math.random() * 10);

  return JSON.stringify({
    ...data,
    sessId,
    connId,
    isRestore: 0,
  });
};

const authHeaders = () =>
  authToken.value ? { Authorization: `Bearer ${authToken.value}` } : {};

const ensureAuth = async () => {
  if (!authToken.value) {
    router.push("/login");
    return false;
  }
  if (authValidated.value) return true;
  try {
    const response = await fetch("/api/v1/auth/user", {
      headers: { ...authHeaders() },
    });
    const payload = await response.json();
    if (!response.ok || !payload.success || !payload.data) {
      throw new Error(payload.message || "登录状态失效");
    }
    authUser.value = {
      id: payload.data.id,
      username: payload.data.username,
      email: payload.data.email,
    };
    localStorage.setItem("authUser", JSON.stringify(authUser.value));
    authValidated.value = true;
    return true;
  } catch (error) {
    authToken.value = "";
    authUser.value = null;
    authValidated.value = false;
    localStorage.removeItem("authToken");
    localStorage.removeItem("authUser");
    router.push("/login");
    return false;
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
  authValidated.value = false;
  localStorage.removeItem("authToken");
  localStorage.removeItem("authUser");
  router.push("/login");
};

const handleFileChange = (event) => {
  const file = event.target.files?.[0];
  binFile.value = file || null;
};

const handleFetchToken = async () => {
  if (!(await ensureAuth())) return;
  if (!form.name || !binFile.value) {
    addLog("请填写账号名称并选择 bin 文件。");
    return;
  }

  loading.value = true;
  try {
    const formData = new FormData();
    formData.append("file", binFile.value);
    formData.append("name", form.name);
    if (form.server) formData.append("server", form.server);
    if (form.wsUrl) formData.append("wsUrl", form.wsUrl);

    const response = await fetch("/api/v1/xyzw/token", {
      method: "POST",
      body: formData,
    });

    const payload = await response.json();
    if (!response.ok || !payload.success || !payload.data?.token) {
      throw new Error(payload.message || "获取 token 失败");
    }

    token.value = decodeTokenFromBase64(payload.data.token);
    addLog(`已获取 token: ${maskedToken.value}`);
  } catch (error) {
    addLog(`获取 token 失败: ${error.message}`);
  } finally {
    loading.value = false;
  }
};

const buildWsUrl = () => {
  if (form.wsUrl) return form.wsUrl;
  return `wss://xxz-xyzw.hortorgames.com/agent?p=${encodeURIComponent(
    token.value
  )}&e=x&lang=chinese`;
};

const handleConnect = async () => {
  if (!(await ensureAuth())) return;
  if (!token.value) {
    addLog("请先获取 token。");
    return;
  }

  status.value = "connecting";
  const wsUrl = buildWsUrl();
  try {
    const response = await fetch("/api/v1/xyzw/ws/connect", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ token: token.value, wsUrl }),
    });
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || "建立 WebSocket 失败");
    }
    addLog(`已请求后端建立 WebSocket: ${wsUrl}`);
  } catch (error) {
    status.value = "error";
    addLog(`建立 WebSocket 失败: ${error.message}`);
  }
};

const handleDisconnect = async () => {
  if (!(await ensureAuth())) return;
  if (!token.value) {
    addLog("请先获取 token。");
    return;
  }

  try {
    const response = await fetch("/api/v1/xyzw/ws/disconnect", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ token: token.value }),
    });
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || "断开失败");
    }
    status.value = "disconnected";
    addLog("已通知后端断开 WebSocket。");
  } catch (error) {
    addLog(`断开失败: ${error.message}`);
  }
};

const handleBottleHelper = async () => {
  if (!(await ensureAuth())) return;
  if (status.value !== "connected") {
    addLog("WebSocket 未连接，无法操作罐子。");
    return;
  }
  try {
    const response = await fetch("/api/v1/xyzw/ws/bottlehelper/restart", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ token: token.value, bottleType: 0 }),
    });
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || "重启罐子失败");
    }
    addLog("已请求后端重启罐子。");
  } catch (error) {
    addLog(`重启罐子失败: ${error.message}`);
  }
};

const refreshStatus = async () => {
  if (!token.value || status.value !== "connected") return;
  try {
    const response = await fetch(
      `/api/v1/xyzw/ws/status?token=${encodeURIComponent(token.value)}`,
      { headers: { ...authHeaders() } }
    );
    const payload = await response.json();
    if (response.ok && payload.success && payload.data?.status) {
      status.value = payload.data.status;
    }
  } catch (error) {
    // ignore
  }
};

const statusTimer = setInterval(refreshStatus, 30000);

onMounted(() => {
  ensureAuth();
});

onBeforeUnmount(() => {
  clearInterval(statusTimer);
});
</script>

<style scoped>
.card {
  max-width: 980px;
  margin: 30px auto;
  background: rgba(15, 23, 42, 0.88);
  border: 1px solid rgba(148, 163, 184, 0.25);
  border-radius: 18px;
  padding: 24px;
  box-shadow: 0 30px 60px rgba(15, 23, 42, 0.35);
  position: relative;
  z-index: 1;
}

.dashboard-page {
  min-height: 100dvh;
  padding: 28px 20px 60px;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(420px 320px at 12% 12%, rgba(125, 211, 252, 0.45), transparent 70%),
    radial-gradient(520px 360px at 88% 8%, rgba(199, 210, 254, 0.5), transparent 70%),
    radial-gradient(640px 420px at 70% 80%, rgba(224, 231, 255, 0.6), transparent 70%),
    linear-gradient(140deg, #f8fafc 0%, #eef2ff 45%, #f1f5f9 100%);
  color: #0f172a;
}

.dashboard-page::before,
.dashboard-page::after {
  content: "";
  position: absolute;
  inset: -20%;
  background-image:
    radial-gradient(circle, rgba(59, 130, 246, 0.25) 0 2px, transparent 3px),
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

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.user-box {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-name {
  font-weight: 600;
  color: #e2e8f0;
}

.ghost {
  background: transparent;
  color: #e2e8f0;
  border: 1px solid rgba(148, 163, 184, 0.4);
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
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
  background: rgba(15, 23, 42, 0.78);
  border: 1px solid rgba(148, 163, 184, 0.4);
  color: #e2e8f0;
  cursor: pointer;
  backdrop-filter: blur(8px);
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
  color: #94a3b8;
}

.field input {
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.3);
  background: rgba(2, 6, 23, 0.6);
  color: #e2e8f0;
}

.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin: 16px 0;
}

.actions button {
  padding: 8px 12px;
  border-radius: 8px;
  border: none;
  cursor: pointer;
  background: #22d3ee;
  color: #0f172a;
  font-weight: 600;
}

.actions .secondary {
  background: #38bdf8;
}

.actions .ghost {
  background: transparent;
  color: #e2e8f0;
  border: 1px solid rgba(148, 163, 184, 0.4);
}

.status {
  margin-bottom: 12px;
  color: #cbd5f5;
}

.log {
  background: rgba(2, 6, 23, 0.6);
  border-radius: 10px;
  padding: 12px;
  min-height: 70px;
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
