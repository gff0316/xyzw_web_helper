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
        Token 获取地址
        <input v-model="form.tokenUrl" placeholder="https://example.com/token" />
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
      <button class="ghost" :disabled="!client" @click="handleDisconnect">
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
      <strong>连接状态:</strong>
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
import { XyzwWebSocketClient } from "./utils/xyzwWebSocket.js";
import { g_utils } from "./utils/bonProtocol.js";

const form = reactive({
  name: "",
  tokenUrl: "",
  server: "",
  wsUrl: "",
});

const token = ref("");
const loading = ref(false);
const status = ref("disconnected");
const logs = ref([]);
let client = null;

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

const handleFetchToken = async () => {
  if (!form.name || !form.tokenUrl) {
    addLog("请填写账号名称和 Token 获取地址。");
    return;
  }

  loading.value = true;
  try {
    const response = await fetch("/api/v1/xyzw/token", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: form.name,
        tokenUrl: form.tokenUrl,
        server: form.server,
        wsUrl: form.wsUrl,
      }),
    });

    const payload = await response.json();
    if (!response.ok || !payload.success || !payload.data?.token) {
      throw new Error(payload.message || "获取 token 失败");
    }

    token.value = payload.data.token;
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

const handleConnect = () => {
  if (!token.value) {
    addLog("请先获取 token。");
    return;
  }

  if (client) {
    client.disconnect();
  }

  status.value = "connecting";
  const wsUrl = buildWsUrl();
  client = new XyzwWebSocketClient({
    url: wsUrl,
    utils: g_utils,
    heartbeatMs: 5000,
  });

  client.onConnect = () => {
    status.value = "connected";
    addLog("WebSocket 已连接。");
  };

  client.onDisconnect = (evt) => {
    status.value = "disconnected";
    addLog(`WebSocket 已断开: ${evt.code || ""}`);
  };

  client.onError = (error) => {
    status.value = "error";
    addLog(`WebSocket 错误: ${error.message || error}`);
  };

  client.setMessageListener((message) => {
    if (message?.cmd) {
      addLog(`收到消息: ${message.cmd}`);
    }
  });

  client.init();
  addLog(`开始连接 WebSocket: ${wsUrl}`);
};

const handleDisconnect = () => {
  if (client) {
    client.disconnect();
    client = null;
    status.value = "disconnected";
    addLog("已手动断开 WebSocket。");
  }
};

const handleBottleHelper = () => {
  if (!client || status.value !== "connected") {
    addLog("WebSocket 未连接，无法操作罐子。");
    return;
  }

  client.send("bottlehelper_stop", { bottleType: 0 });
  setTimeout(() => {
    client.send("bottlehelper_start", { bottleType: 0 });
    client.send("role_getroleinfo");
    addLog("已发送罐子启动/重启指令。");
  }, 500);
};

onBeforeUnmount(() => {
  if (client) {
    client.disconnect();
  }
});
</script>
