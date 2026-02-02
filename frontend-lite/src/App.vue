<template>
  <div class="card">
    <div class="header">
      <h1>XYZW WebSocket 控制台</h1>
      <p>仅保留 WebSocket 连接与罐子功能，用于对接后端接口。</p>
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
      <button :disabled="loading" @click="handleFetchToken">
        获取 Token
      </button>
      <button
        class="secondary"
        :disabled="!token || loading"
        @click="handleConnect"
      >
        建立 WebSocket
      </button>
      <button class="ghost" :disabled="status !== 'connected'" @click="handleDisconnect">
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
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref } from "vue";
import { g_utils } from "./utils/bonProtocol.js";

const form = reactive({
  name: "",
  server: "",
  wsUrl: "",
});

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

const handleFileChange = (event) => {
  const file = event.target.files?.[0];
  binFile.value = file || null;
};

const handleFetchToken = async () => {
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
    token.value,
  )}&e=x&lang=chinese`;
};

const handleConnect = async () => {
  if (!token.value) {
    addLog("请先获取 token。");
    return;
  }

  status.value = "connecting";
  const wsUrl = buildWsUrl();
  try {
    const response = await fetch("/api/v1/xyzw/ws/connect", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
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
  if (!token.value) {
    addLog("请先获取 token。");
    return;
  }

  try {
    const response = await fetch("/api/v1/xyzw/ws/disconnect", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
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
  if (status.value !== "connected") {
    addLog("WebSocket 未连接，无法操作罐子。");
    return;
  }
  try {
    const response = await fetch("/api/v1/xyzw/ws/bottlehelper/restart", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
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
  if (!token.value) return;
  try {
    const response = await fetch(
      `/api/v1/xyzw/ws/status?token=${encodeURIComponent(token.value)}`,
    );
    const payload = await response.json();
    if (response.ok && payload.success && payload.data?.status) {
      status.value = payload.data.status;
    }
  } catch (error) {
    // ignore
  }
};

const statusTimer = setInterval(refreshStatus, 2000);
onBeforeUnmount(() => clearInterval(statusTimer));
</script>





