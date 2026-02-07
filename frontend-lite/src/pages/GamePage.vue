<template>
  <div class="game-page">
    <button v-if="authUser" class="logout-fab" type="button" @click="handleLogout">
      退出登录
    </button>

    <div class="content">
      <div class="header">
        <div class="header-actions">
          <button class="ghost" type="button" @click="router.push('/profile')">
            个人信息管理
          </button>
        </div>
      </div>

      <div class="identity-wrapper">
        <IdentityCard
          :role-info="roleInfo"
          :loading="roleLoading"
          :show-refresh="true"
          @refresh="loadRoleInfo(true)"
        />
      </div>

      <div class="card">
        <div class="card-title">操作面板</div>
        <div class="card-actions">
          <button class="primary" :disabled="loading || !tokenRecord" @click="handleRestartBottle">
            重启罐子
          </button>
        </div>
        <p v-if="statusNote" class="status-note" :class="{ error: statusIsError }">
          {{ statusNote }}
        </p>
      </div>
    </div>

    <div class="connection-status" :class="statusClass">
      连接状态：{{ statusText }}
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { g_utils } from "../utils/bonProtocol.js";
import IdentityCard from "../components/IdentityCard.vue";

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
const tokenId = ref(route.params.tokenId || "");
const loading = ref(false);
const status = ref("disconnected");
const statusNote = ref("");
const statusIsError = ref(false);
const roleInfo = ref(null);
const roleLoading = ref(false);

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

const setNote = (message, isError = false) => {
  statusNote.value = message;
  statusIsError.value = isError;
};

const fetchTokenRecord = async () => {
  if (!(await ensureAuth())) return;
  if (!tokenId.value) {
    setNote("缺少 Token，无法加载。", true);
    return;
  }
  loading.value = true;
  try {
    const response = await fetch(`/api/v1/xyzw/tokens/${tokenId.value}`, {
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
    setNote("Token 已加载，可进行操作。", false);
    await loadRoleInfo(true);
  } catch (error) {
    setNote(`加载 Token 失败: ${error.message}`, true);
  } finally {
    loading.value = false;
  }
};

const loadRoleInfo = async (refresh = false) => {
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) return;
  roleLoading.value = true;
  try {
    const tokenJsonValue = buildTokenJson();
    const response = await fetch(
      `/api/v1/xyzw/ws/roleinfo?token=${encodeURIComponent(tokenJsonValue)}&refresh=${refresh}`,
      { headers: { ...authHeaders() } },
    );
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || "获取身份牌失败");
    }
    roleInfo.value = payload.data?.roleInfo || null;
    if (!roleInfo.value && refresh) {
      setNote("身份牌信息为空，可稍后重试。", true);
    }
  } catch (error) {
    setNote(`获取身份牌失败: ${error.message}`, true);
  } finally {
    roleLoading.value = false;
  }
};

const handleRestartBottle = async () => {
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) {
    setNote("请先加载 Token。", true);
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
    setNote("已请求后端重启罐子。", false);
    await loadRoleInfo(true);
  } catch (error) {
    status.value = "error";
    setNote(`重启罐子失败: ${error.message}`, true);
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
    tokenId.value = route.params.tokenId;
    fetchTokenRecord();
  } else {
    setNote("请从个人信息管理进入该页面。", true);
  }
});

onBeforeUnmount(() => {
  clearInterval(statusTimer);
});
</script>

<style scoped>
.game-page {
  min-height: 100dvh;
  padding: 32px 22px 88px;
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

.game-page::before,
.game-page::after {
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

.game-page::after {
  opacity: 0.28;
  background-position: 40px 120px, 120px 20px, 200px 160px;
}

.content {
  max-width: 1600px;
  width: min(96vw, 1600px);
  margin: 0 auto;
  position: relative;
  z-index: 1;
}

.header {
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
  gap: 16px;
  margin-bottom: 8px;
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

.card {
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(148, 163, 184, 0.45);
  padding: 18px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
  max-width: 520px;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 12px;
}

.card-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.primary {
  padding: 10px 16px;
  border-radius: 10px;
  border: none;
  cursor: pointer;
  background: #0f172a;
  color: #f8fafc;
  font-weight: 600;
}

.primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.status-note {
  margin-top: 10px;
  font-size: 12px;
  color: #0f172a;
}

.status-note.error {
  color: #b91c1c;
}

.connection-status {
  position: fixed;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  padding: 6px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(148, 163, 184, 0.6);
  font-size: 12px;
  color: #334155;
  z-index: 10;
}

.connection-status.connected {
  color: #0f766e;
  border-color: rgba(13, 148, 136, 0.4);
}

.connection-status.connecting {
  color: #1d4ed8;
  border-color: rgba(59, 130, 246, 0.5);
}

.connection-status.error {
  color: #b91c1c;
  border-color: rgba(248, 113, 113, 0.5);
}

@media (max-width: 720px) {
  .header {
    flex-direction: column;
    align-items: flex-start;
  }

  .card {
    width: 100%;
  }
}

.identity-wrapper {
  margin: 0 auto 10px;
  width: 100%;
}
</style>
