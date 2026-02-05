<template>
  <div class="dashboard-page">
    <button v-if="authUser" class="logout-fab" type="button" @click="handleLogout">
      退出登录
    </button>
    <div class="content">
      <div class="header">
        <div>
          <h1>个人信息管理</h1>
          <p v-if="authUser">欢迎，{{ authUser.username }}，在这里维护你的 Bin 和 Token。</p>
          <p v-else>请先登录。</p>
        </div>
        <div class="header-actions">
          <button class="ghost" type="button" @click="router.push('/game')">游戏功能</button>
          <button class="ghost" type="button" @click="router.push('/upload-bin')">
            上传 Bin
          </button>
        </div>
      </div>
      <p v-if="statusMessage" class="status-message" :class="{ error: statusIsError }">
        {{ statusMessage }}
      </p>

      <div class="section">
        <h2>我的 Bin</h2>
        <div v-if="bins.length === 0" class="empty">暂无 Bin，请先上传。</div>
        <div v-for="bin in bins" :key="bin.id" class="bin-card">
          <div class="bin-header" @click="toggleBin(bin.id)">
            <div class="bin-title">
              <strong>{{ bin.name || '未命名账号' }}</strong>
              <span class="bin-id">#{{ bin.id }}</span>
            </div>
            <div class="bin-header-actions" @click.stop>
              <button class="ghost small" :disabled="loading" @click="handleCreateToken(bin.id)">
                生成 Token
              </button>
              <button class="danger small" :disabled="loading" @click="handleDeleteBin(bin.id)">
                删除 Bin
              </button>
              <span class="chevron" :class="{ open: isBinOpen(bin.id) }">▾</span>
            </div>
          </div>
          <div v-if="isBinOpen(bin.id)" class="bin-body">
            <div class="bin-meta">
              <span v-if="bin.server">服务器：{{ bin.server }}</span>
              <span v-if="bin.wsUrl">WS：{{ bin.wsUrl }}</span>
              <span v-if="bin.fileName">文件：{{ bin.fileName }}</span>
            </div>
            <div class="token-list">
              <div v-for="token in bin.tokens" :key="token.id" class="token-item">
                <div>
                  <div class="token-title">
                    Token #{{ token.id }}
                    <span v-if="token.regionName">· {{ token.regionName }}</span>
                    <span v-if="token.roleName">· {{ token.roleName }}</span>
                  </div>
                  <div class="token-meta">{{ maskToken(token.token) }}</div>
                  <div class="token-meta" v-if="token.server">????{{ token.server }}</div>
                </div>
                <div class="token-actions">
                  <button class="secondary" @click="goGame(token.id)">进入游戏功能</button>
                  <button class="danger" @click="handleDeleteToken(token.id)">删除</button>
                </div>
              </div>
              <div v-if="!bin.tokens || bin.tokens.length === 0" class="empty">
                还没有 Token，可以点击“生成 Token”。
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();

const authToken = ref(localStorage.getItem("authToken") || "");
const authUser = ref(
  localStorage.getItem("authUser")
    ? JSON.parse(localStorage.getItem("authUser"))
    : null,
);

const bins = ref([]);
const loading = ref(false);
const logs = ref([]);
const expandedBins = ref([]);
const statusMessage = ref("");
const statusIsError = ref(false);

const addLog = (message) => {
  logs.value.unshift(`[${new Date().toLocaleTimeString()}] ${message}`);
};

const setStatus = (message, isError = false) => {
  statusMessage.value = message;
  statusIsError.value = isError;
};

const authHeaders = () =>
  authToken.value
    ? { Authorization: `Bearer ${authToken.value}` }
    : {};

const ensureAuth = async () => {
  if (!authToken.value) {
    router.push("/login");
    return false;
  }
  try {
    const response = await fetch("/api/v1/auth/user", {
      headers: { ...authHeaders() },
    });
    const payload = await response.json();
    if (!response.ok || !payload.success || !payload.data) {
      throw new Error(payload.message || "登录状态失效");
    }
    authUser.value = payload.data;
    localStorage.setItem("authUser", JSON.stringify(payload.data));
    return true;
  } catch (error) {
    authToken.value = "";
    authUser.value = null;
    localStorage.removeItem("authToken");
    localStorage.removeItem("authUser");
    router.push("/login");
    return false;
  }
};

const fetchBins = async () => {
  if (!(await ensureAuth())) return;
  try {
    const response = await fetch("/api/v1/xyzw/bins", {
      headers: { ...authHeaders() },
    });
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || "获取 Bin 失败");
    }
    bins.value = payload.data?.bins || [];
    setStatus("Bin 列表已更新。", false);
  } catch (error) {
    setStatus(`获取 Bin 失败: ${error.message}`, true);
  }
};

const handleCreateToken = async (binId) => {
  if (!(await ensureAuth())) return;
  loading.value = true;
  try {
    const response = await fetch(`/api/v1/xyzw/bins/${binId}/token`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ name: "", server: "" }),
    });
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || "生成 Token 失败");
    }
    setStatus(`已生成 Token #${payload.data?.id || ""}`, false);
    await fetchBins();
  } catch (error) {
    setStatus(`生成 Token 失败: ${error.message}`, true);
  } finally {
    loading.value = false;
  }
};

const toggleBin = (binId) => {
  if (expandedBins.value.includes(binId)) {
    expandedBins.value = expandedBins.value.filter((id) => id !== binId);
  } else {
    expandedBins.value = [...expandedBins.value, binId];
  }
};

const isBinOpen = (binId) => expandedBins.value.includes(binId);

const handleDeleteBin = async (binId) => {
  if (!(await ensureAuth())) return;
  if (!confirm("确定删除该 Bin 吗？对应 Token 会一并删除。")) return;
  loading.value = true;
  try {
    const response = await fetch(`/api/v1/xyzw/bins/${binId}`, {
      method: "DELETE",
      headers: { ...authHeaders() },
    });
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || "删除 Bin 失败");
    }
    expandedBins.value = expandedBins.value.filter((id) => id !== binId);
    setStatus("Bin 已删除。", false);
    await fetchBins();
  } catch (error) {
    setStatus(`删除 Bin 失败: ${error.message}`, true);
  } finally {
    loading.value = false;
  }
};

const handleDeleteToken = async (tokenId) => {
  if (!(await ensureAuth())) return;
  if (!confirm("确定删除该 Token 吗？")) return;
  loading.value = true;
  try {
    const response = await fetch(`/api/v1/xyzw/tokens/${tokenId}`, {
      method: "DELETE",
      headers: { ...authHeaders() },
    });
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || "删除 Token 失败");
    }
    setStatus("Token 已删除。", false);
    await fetchBins();
  } catch (error) {
    setStatus(`删除 Token 失败: ${error.message}`, true);
  } finally {
    loading.value = false;
  }
};

const goGame = (tokenId) => {
  router.push(`/game/${tokenId}`);
};

const maskToken = (token) => {
  if (!token) return "";
  if (token.length <= 10) return token;
  return `${token.slice(0, 8)}...${token.slice(-6)}`;
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

onMounted(() => {
  fetchBins();
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
  max-width: 1100px;
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

.section {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid rgba(148, 163, 184, 0.4);
}

.section h2 {
  margin: 0 0 12px;
  font-size: 16px;
  color: #0f172a;
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

.actions .secondary {
  background: #1f2937;
}

.actions .ghost {
  background: transparent;
  color: #0f172a;
  border: 1px solid rgba(148, 163, 184, 0.6);
}

.bin-card {
  margin-top: 14px;
  padding: 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(148, 163, 184, 0.45);
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
}

.bin-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  user-select: none;
}

.bin-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.bin-header-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.bin-id {
  margin-left: 8px;
  font-size: 12px;
  color: #64748b;
}

.bin-body {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(148, 163, 184, 0.3);
}

.bin-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
  font-size: 12px;
  color: #475569;
}

.token-list {
  margin-top: 12px;
  display: grid;
  gap: 10px;
}

.token-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.85);
  border: 1px solid rgba(148, 163, 184, 0.35);
}

.token-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.token-title {
  font-weight: 600;
  color: #0f172a;
}


.token-meta {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

.empty {
  margin-top: 8px;
  font-size: 12px;
  color: #64748b;
}

.status-message {
  margin: 0 0 8px;
  font-size: 12px;
  color: #0f172a;
}

.status-message.error {
  color: #b91c1c;
}

.small {
  padding: 5px 10px;
  font-size: 12px;
}

.danger {
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.5);
  color: #b91c1c;
  padding: 6px 10px;
  border-radius: 8px;
  cursor: pointer;
}

.chevron {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.5);
  color: #475569;
  transform: rotate(0deg);
  transition: transform 0.15s ease;
  font-size: 12px;
}

.chevron.open {
  transform: rotate(180deg);
}

@media (max-width: 720px) {
  .grid {
    grid-template-columns: 1fr;
  }

  .header {
    flex-direction: column;
    align-items: flex-start;
  }

  .token-item {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
