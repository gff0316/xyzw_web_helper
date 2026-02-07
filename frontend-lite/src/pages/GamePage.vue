<template>
  <div class="game-page">
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

      <div class="card helper-card">
        <div class="card-header-line">
          <div class="card-title with-icon">
            <img
              class="card-icon"
              src="/icons/173746572831736.png"
              alt="罐子助手"
            />
            罐子助手
          </div>
        </div>
        <div class="helper-body">
          <div class="helper-meta">
            <div class="helper-label">剩余时间</div>
            <div class="helper-value">{{ formatTime(helperRemaining) }}</div>
          </div>
          <div class="helper-actions">
            <button
              class="primary"
              :disabled="loading || !tokenRecord"
              @click="handleRestartBottle"
            >
              重启罐子
            </button>
          </div>
        </div>
      </div>

      <div class="card hangup-card">
        <div class="card-header-line">
          <div class="card-title with-icon">
            <img
              class="card-icon"
              src="/icons/174061875626614.png"
              alt="挂机时间"
            />
            挂机时间
          </div>
          <span
            class="pill"
            :class="{ connected: hangupRunning, disconnected: !hangupRunning }"
          >
            {{ hangupRunning ? "运行中" : "已停" }}
          </span>
        </div>
        <div class="helper-body">
          <div class="helper-meta">
            <div class="helper-label">剩余时间</div>
            <div class="helper-value">{{ formatTime(hangupRemaining) }}</div>
          </div>
          <div class="helper-actions">
            <button
              class="primary"
              :disabled="loading || !tokenRecord"
              @click="handleExtendHangup"
            >
              加钟
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="connection-actions-fixed">
      <button
        class="primary"
        :disabled="loading || !tokenRecord"
        @click="handleReconnect"
      >
        重新连接
      </button>
      <div class="connection-status" :class="statusClass">
        连接状态：{{ statusText }}
      </div>
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
    : null
);

const tokenRecord = ref(null);
const tokenJson = ref("");
const tokenId = ref(route.params.tokenId || "");
const loading = ref(false);
const status = ref("disconnected");
const statusNote = ref("");
const statusIsError = ref(false);
const helperRemaining = ref(0);
const helperRunning = ref(false);
const hangupRemaining = ref(0);
const hangupRunning = ref(false);
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
  authToken.value ? { Authorization: `Bearer ${authToken.value}` } : {};

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
    setNote("Token 已加载，可开始操作。", false);
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
      `/api/v1/xyzw/ws/roleinfo?token=${encodeURIComponent(
        tokenJsonValue
      )}&refresh=${refresh}`,
      { headers: { ...authHeaders() } }
    );
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || "获取身份牌失败");
    }
    roleInfo.value = payload.data?.roleInfo || null;
    const stopTime =
      payload.data?.roleInfo?.role?.bottleHelpers?.helperStopTime;
    if (stopTime) {
      const nowSec = Date.now() / 1000;
      helperRemaining.value = Math.max(0, Math.floor(stopTime - nowSec));
      helperRunning.value = helperRemaining.value > 0;
    }
    const hang = payload.data?.roleInfo?.role?.hangUp;
    if (hang?.hangUpTime != null && hang?.lastTime != null) {
      const nowSec = Date.now() / 1000;
      const elapsed = nowSec - hang.lastTime;
      const remain = Math.floor(hang.hangUpTime - elapsed);
      hangupRemaining.value = Math.max(0, remain);
      hangupRunning.value = hangupRemaining.value > 0;
    }
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

    const restartResponse = await fetch(
      "/api/v1/xyzw/ws/bottlehelper/restart",
      {
        method: "POST",
        headers: { "Content-Type": "application/json", ...authHeaders() },
        body: JSON.stringify({ token: tokenJsonValue, bottleType: 0 }),
      }
    );
    const restartPayload = await restartResponse.json();
    if (!restartResponse.ok || !restartPayload.success) {
      throw new Error(restartPayload.message || "重启罐子失败");
    }

    status.value = "connected";
    await loadRoleInfo(true);
    if (helperRemaining.value > 0) {
      setNote(
        `已请求重启罐子，剩余时间 ${formatTime(helperRemaining.value)}`,
        false
      );
    } else {
      status.value = "error";
      setNote("重启罐子可能失败：剩余时间为 0，建议重试。", true);
    }
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

const handleReconnect = async () => {
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) {
    setNote("请先加载 Token。", true);
    return;
  }
  loading.value = true;
  status.value = "connecting";
  try {
    const tokenJsonValue = buildTokenJson();
    const resp = await fetch("/api/v1/xyzw/ws/connect", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ token: tokenJsonValue }),
    });
    const payload = await resp.json();
    if (!resp.ok || !payload.success) {
      throw new Error(payload.message || "重连 WebSocket 失败");
    }
    status.value = "connected";
    setNote("已重新建立 WebSocket。", false);
    await loadRoleInfo(true);
  } catch (error) {
    status.value = "error";
    setNote(`重连失败: ${error.message}`, true);
  } finally {
    loading.value = false;
  }
};

const handleExtendHangup = async () => {
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) {
    setNote("请先加载 Token。", true);
    return;
  }
  loading.value = true;
  try {
    const tokenJsonValue = buildTokenJson();
    const resp = await fetch("/api/v1/xyzw/ws/hangup/extend", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ token: tokenJsonValue }),
    });
    const payload = await resp.json();
    if (!resp.ok || !payload.success) {
      throw new Error(payload.message || "挂机加钟失败");
    }
    setNote("已请求加钟，请稍后查看剩余时间。", false);
    await loadRoleInfo(true);
  } catch (error) {
    setNote(`加钟失败: ${error.message}`, true);
  } finally {
    loading.value = false;
  }
};

const formatTime = (seconds) => {
  const total = Math.max(0, Math.floor(Number(seconds) || 0));
  const h = Math.floor(total / 3600)
    .toString()
    .padStart(2, "0");
  const m = Math.floor((total % 3600) / 60)
    .toString()
    .padStart(2, "0");
  const s = (total % 60).toString().padStart(2, "0");
  return `${h}:${m}:${s}`;
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
  clearInterval(helperTimer);
  clearInterval(hangupTimer);
});

const helperTimer = setInterval(() => {
  if (helperRunning.value && helperRemaining.value > 0) {
    helperRemaining.value = Math.max(0, helperRemaining.value - 1);
    if (helperRemaining.value === 0) helperRunning.value = false;
  }
}, 1000);

const hangupTimer = setInterval(() => {
  if (hangupRunning.value && hangupRemaining.value > 0) {
    hangupRemaining.value = Math.max(0, hangupRemaining.value - 1);
    if (hangupRemaining.value === 0) hangupRunning.value = false;
  }
}, 1000);
</script>

<style scoped>
.game-page {
  min-height: 100dvh;
  padding: 32px 22px 88px;
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

.game-page::before,
.game-page::after {
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

.card-title.with-icon {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 0;
}

.card-icon {
  width: 24px;
  height: 24px;
  object-fit: contain;
}

.card-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.helper-card {
  max-width: 520px;
  margin-top: 8px;
}

.hangup-card {
  max-width: 520px;
  margin-top: 10px;
}

.card-header-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.helper-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.helper-meta {
  flex: 1;
}

.helper-label {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 4px;
}

.helper-value {
  font-size: 14px;
  color: #0f172a;
  min-height: 20px;
}

.helper-value.subtle,
.helper-label.subtle {
  color: #94a3b8;
  font-size: 12px;
}

.helper-actions {
  display: flex;
  gap: 8px;
}

.primary {
  padding: 10px 16px;
  border-radius: 10px;
  border: none;
  cursor: pointer;
  background: linear-gradient(135deg, #b8c1cc, #8d99aa);
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

.connection-actions-fixed {
  position: fixed;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  z-index: 12;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.connection-actions-fixed .primary {
  padding: 6px 12px;
  border-radius: 10px;
  font-size: 12px;
  min-width: 130px;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.12);
}

.connection-status {
  position: relative;
  padding: 6px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.6);
  font-size: 12px;
  color: #334155;
  min-width: 130px;
  text-align: center;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
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







