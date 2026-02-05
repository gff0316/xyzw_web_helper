<template>
  <div class="dashboard-page">
    <button v-if="authUser" class="logout-fab" type="button" @click="handleLogout">
      退出登录
    </button>
    <div class="content">
      <div class="header">
        <div>
          <h1>游戏功能</h1>
          <p v-if="authUser">欢迎，{{ authUser.username }}，选择 Token 后即可操作收罐子。</p>
          <p v-else>请先登录。</p>
        </div>
        <div class="header-actions">
          <button class="ghost" type="button" @click="router.push('/profile')">个人信息管理</button>
        </div>
      </div>

      <div class="grid">
        <label class="field">
          Token UUID
          <input v-model="tokenIdInput" placeholder="输入 Token ID" />
        </label>
        <div class="field">
          当前 Token
          <div class="pill" v-if="tokenRecord">
            #{{ tokenRecord.id }}
            <span v-if="tokenRecord.regionName">· {{ tokenRecord.regionName }}</span>
            <span v-if="tokenRecord.roleName">· {{ tokenRecord.roleName }}</span>
          </div>
          <div class="pill" v-else>未选择</div>
        </div>
      </div>

      <div class="actions">
        <button class="ghost" :disabled="loading" @click="fetchTokenRecord">
          加载 Token
        </button>
        <button class="secondary" :disabled="loading || !tokenRecord" @click="handleRestartBottle">
          重启罐子
        </button>
      </div>

      <div class="status">
        <strong>连接状态</strong>
        <span :class="statusClass">{{ statusText }}</span>
        <span v-if="tokenRecord">Token: {{ maskedToken }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { g_utils } from "../utils/bonProtocol.js";

const route = useRoute();
const router = useRouter();
const authToken = ref(localStorage.getItem("authToken") || "");
const authUser = ref(
  localStorage.getItem("authUser")
    ? JSON.parse(localStorage.getItem("authUser"))
    : null,
);

const tokenRecord = ref(null);
const tokenJson = ref("");
const tokenIdInput = ref(route.params.tokenId || "");
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
  if (!tokenRecord.value?.token) return "";
  const raw = tokenRecord.value.token;
  if (raw.length <= 8) return raw;
  return `${raw.slice(0, 6)}...${raw.slice(-4)}`;
});

const authHeaders = () =>
  authToken.value
    ? { Authorization: `Bearer ${authToken.value}` }
    : {};

const ensureAuth = async () => {
  if (!authToken.value) {
    router.push("/login");
    return false;
  }
  return true;
};

const buildTokenJson = () => {
  if (!tokenRecord.value?.token) return "";
  if (tokenJson.value) return tokenJson.value;
  tokenJson.value = decodeTokenFromBase64(tokenRecord.value.token);
  return tokenJson.value;
};

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

const fetchTokenRecord = async () => {
  if (!(await ensureAuth())) return;
  if (!tokenIdInput.value) {
    addLog("请输入 Token ID。");
    return;
  }
  loading.value = true;
  try {
    const response = await fetch(`/api/v1/xyzw/tokens/${tokenIdInput.value}`, {
      headers: { ...authHeaders() },
    });
    const payload = await response.json();
    if (!response.ok || !payload.success || !payload.data?.token) {
      throw new Error(payload.message || "加载 Token 失败");
    }
    tokenRecord.value = payload.data.token;
    tokenJson.value = "";
    if (payload.data.bin) {
      tokenRecord.value.bin = payload.data.bin;
    }
    addLog(`已加载 Token #${tokenRecord.value.id}`);
  } catch (error) {
    addLog(`加载 Token 失败: ${error.message}`);
  } finally {
    loading.value = false;
  }
};

const handleRestartBottle = async () => {
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) {
    addLog("请先加载 Token。");
    return;
  }

  loading.value = true;
  status.value = "connecting";
  try {
    const tokenJsonValue = buildTokenJson();
    const connectResponse = await fetch("/api/v1/xyzw/ws/connect", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ token: tokenJsonValue }),
    });
    const connectPayload = await connectResponse.json();
    if (!connectResponse.ok || !connectPayload.success) {
      throw new Error(connectPayload.message || "建立 WebSocket 失败");
    }

    await new Promise((resolve) => setTimeout(resolve, 800));

    const restartResponse = await fetch("/api/v1/xyzw/ws/bottlehelper/restart", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ token: tokenJsonValue, bottleType: 0 }),
    });
    const restartPayload = await restartResponse.json();
    if (!restartResponse.ok || !restartPayload.success) {
      throw new Error(restartPayload.message || "重启罐子失败");
    }

    status.value = "connected";
    addLog("已请求后端重启罐子。");
  } catch (error) {
    status.value = "error";
    addLog(`重启罐子失败: ${error.message}`);
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

const refreshStatus = async () => {
  if (!tokenRecord.value?.token || status.value !== "connected") return;
  try {
    const tokenJsonValue = buildTokenJson();
    const response = await fetch(
      `/api/v1/xyzw/ws/status?token=${encodeURIComponent(tokenJsonValue)}`,
      { headers: { ...authHeaders() } },
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
  if (route.params.tokenId) {
    tokenIdInput.value = route.params.tokenId;
    fetchTokenRecord();
  }
});

onBeforeUnmount(() => {
  clearInterval(statusTimer);
});
</script>

<style scoped>
.dashboard-page {
  min-height: 100dvh;
  padding: 32px 22px 72px;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(420px 320px at 12% 12%, rgba(125, 211, 252, 0.45), transparent 70%),
    radial-gradient(520px 360px at 88% 8%, rgba(199, 210, 254, 0.5), transparent 70%),
    radial-gradient(640px 420px at 70% 80%, rgba(224, 231, 255, 0.6), transparent 70%),
    linear-gradient(140deg, #f8fafc 0%, #eef2ff 45%, #f1f5f9 100%);
  color: #0f172a;
  font-family: "Manrope", "Noto Sans SC", "PingFang SC", sans-serif;
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

.content {
  max-width: 980px;
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
  color: rgba(15, 23, 42, 0.72);
}

.field input {
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.5);
  background: rgba(255, 255, 255, 0.75);
  color: #0f172a;
}

.pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(148, 163, 184, 0.5);
  color: #0f172a;
  font-size: 12px;
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
  background: #0f172a;
  color: #f8fafc;
  font-weight: 600;
}

.actions .secondary {
  background: #1f2937;
}

.actions .ghost {
  background: transparent;
  color: #0f172a;
  border: 1px solid rgba(148, 163, 184, 0.6);
}

.status {
  margin-bottom: 12px;
  color: #475569;
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
