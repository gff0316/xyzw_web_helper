<template>
  <div class="profile-page">
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
          <h1>个人信息管理</h1>
          <p v-if="authUser">
            欢迎，{{ authUser.username }}，在这里维护你的 Bin 和 Token。
          </p>
          <p v-else>请先登录。</p>
        </div>
        <div class="header-actions">
          <button
            class="ghost"
            type="button"
            @click="router.push('/upload-bin')"
          >
            上传 Bin
          </button>
          <button class="ghost" type="button" @click="router.push('/wx-login')">
            微信扫码登录
          </button>
          <button class="ghost" type="button" @click="fetchBins">刷新</button>
        </div>
      </div>

      <div class="bins-section">
        <div v-if="loading" class="hint">正在加载...</div>
        <div v-else-if="bins.length === 0" class="empty">
          暂无 Bin，请先上传。
        </div>
        <div v-else class="bins-list">
          <div v-for="bin in bins" :key="bin.id" class="bin-item">
            <button class="bin-header" type="button" @click="toggleBin(bin.id)">
              <div class="bin-title">
                <div class="name">
                  名称：<span class="name-value">{{ formatBinName(bin) }}</span>
                </div>
                <div class="meta">
                  {{ formatDate(bin.createdAt) }} · Token
                  {{ bin.tokens?.length || 0 }} 个
                </div>
              </div>
              <div class="bin-actions">
                <button
                  class="ghost danger"
                  type="button"
                  @click.stop="handleDeleteBin(bin.id)"
                >
                  删除 Bin
                </button>
                <span class="caret" :class="{ open: expandedBinId === bin.id }"
                  >▼</span
                >
              </div>
            </button>

            <div v-if="expandedBinId === bin.id" class="bin-body">
              <div class="bin-meta">
                <div><span>备注：</span>{{ bin.remark || "—" }}</div>
                <div><span>文件：</span>{{ bin.filePath || "—" }}</div>
              </div>

              <div class="token-form">
                <input
                  v-model="getTokenForm(bin.id).name"
                  placeholder="Token 名称（可选）"
                />
                <input
                  v-model="getTokenForm(bin.id).server"
                  placeholder="服务器（可选）"
                />
                <input
                  v-model="getTokenForm(bin.id).wsUrl"
                  placeholder="WS 地址（可选）"
                />
                <button
                  :disabled="actionLoading"
                  @click="handleCreateToken(bin.id)"
                >
                  生成 Token
                </button>
              </div>

              <div class="token-list">
                <div v-if="!bin.tokens || bin.tokens.length === 0" class="hint">
                  暂无 Token，可以点击“生成 Token”。
                </div>
                <div v-else class="token-items">
                  <div
                    v-for="token in bin.tokens"
                    :key="token.id"
                    class="token-item"
                  >
                    <div class="token-info">
                      <div class="token-title">
                        {{ token.name || token.uuid }}
                      </div>
                      <div class="token-meta">
                        {{ token.server || "无服务器" }} ·
                        {{ formatDate(token.createdAt) }}
                      </div>
                      <div class="token-value">
                        {{ formatToken(token.token) }}
                      </div>
                    </div>
                    <div class="token-actions">
                      <button
                        class="ghost"
                        type="button"
                        @click="copyToken(token.token)"
                      >
                        复制 Token
                      </button>
                      <button
                        class="ghost"
                        type="button"
                        @click="handleEnterGame(token.uuid)"
                      >
                        进入罐子
                      </button>
                      <button
                        class="ghost danger"
                        type="button"
                        @click="handleDeleteToken(token.id, bin.id)"
                      >
                        删除
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <p v-if="statusMessage" class="status-message" :class="statusType">
        {{ statusMessage }}
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();

const authToken = ref(localStorage.getItem("authToken") || "");
const authUser = ref(
  localStorage.getItem("authUser")
    ? JSON.parse(localStorage.getItem("authUser"))
    : null
);

const bins = ref([]);
const loading = ref(false);
const actionLoading = ref(false);
const statusMessage = ref("");
const statusType = ref("info");
const expandedBinId = ref(null);
const tokenForms = reactive({});

const authHeaders = () =>
  authToken.value ? { Authorization: `Bearer ${authToken.value}` } : {};

const ensureAuth = () => {
  if (!authToken.value) {
    router.push("/login");
    return false;
  }
  return true;
};

const setStatus = (text, type = "info") => {
  statusMessage.value = text;
  statusType.value = type;
};

const formatDate = (value) => {
  if (!value) return "";
  return String(value).replace("T", " ").replace(".000", "");
};

const formatBinName = (bin) => {
  const raw = bin?.name;
  if (typeof raw === "string" && raw.trim()) return raw.trim();
  if (bin?.filePath) return bin.filePath;
  if (bin?.id != null) return `Bin #${bin.id}`;
  return "未命名";
};

const formatToken = (token) => {
  if (!token) return "";
  if (token.length <= 16) return token;
  return `${token.slice(0, 8)}...${token.slice(-6)}`;
};

const copyToken = async (token) => {
  try {
    await navigator.clipboard.writeText(token);
    setStatus("Token 已复制", "success");
  } catch (err) {
    setStatus("复制失败，请手动复制", "error");
  }
};

const getTokenForm = (binId) => {
  if (!tokenForms[binId]) {
    tokenForms[binId] = { name: "", server: "", wsUrl: "" };
  }
  return tokenForms[binId];
};

const toggleBin = (binId) => {
  expandedBinId.value = expandedBinId.value === binId ? null : binId;
};

const fetchBins = async () => {
  if (!ensureAuth()) return;
  loading.value = true;
  try {
    const response = await fetch("/api/v1/xyzw/bins", {
      headers: { ...authHeaders() },
    });
    if (!response.ok) {
      if (response.status === 401) {
        router.push("/login");
        return;
      }
      throw new Error("获取列表失败");
    }
    const payload = await response.json();
    bins.value = payload?.data?.bins || [];
  } catch (err) {
    setStatus(err.message || "获取列表失败", "error");
  } finally {
    loading.value = false;
  }
};

const handleCreateToken = async (binId) => {
  if (!ensureAuth()) return;
  actionLoading.value = true;
  try {
    const form = getTokenForm(binId);
    const response = await fetch(`/api/v1/xyzw/bins/${binId}/token`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({
        name: form.name || null,
        server: form.server || null,
        wsUrl: form.wsUrl || null,
      }),
    });
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || "生成 Token 失败");
    }
    setStatus("Token 生成成功", "success");
    form.name = "";
    form.server = "";
    form.wsUrl = "";
    await fetchBins();
  } catch (err) {
    setStatus(err.message || "生成 Token 失败", "error");
  } finally {
    actionLoading.value = false;
  }
};

const handleDeleteToken = async (tokenId, binId) => {
  if (!ensureAuth()) return;
  if (!confirm("确认删除该 Token 吗？")) return;
  actionLoading.value = true;
  try {
    const response = await fetch(`/api/v1/xyzw/tokens/${tokenId}`, {
      method: "DELETE",
      headers: { ...authHeaders() },
    });
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || "删除 Token 失败");
    }
    setStatus("Token 已删除", "success");
    if (expandedBinId.value === binId) {
      await fetchBins();
    }
  } catch (err) {
    setStatus(err.message || "删除 Token 失败", "error");
  } finally {
    actionLoading.value = false;
  }
};

const handleEnterGame = (tokenUuid) => {
  if (!tokenUuid) return;
  router.push(`/game/${tokenUuid}`);
};

const handleDeleteBin = async (binId) => {
  if (!ensureAuth()) return;
  if (!confirm("确认删除该 Bin？该 Bin 下的 Token 也会被删除。")) return;
  actionLoading.value = true;
  try {
    const response = await fetch(`/api/v1/xyzw/bins/${binId}`, {
      method: "DELETE",
      headers: { ...authHeaders() },
    });
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || "删除 Bin 失败");
    }
    setStatus("Bin 已删除", "success");
    if (expandedBinId.value === binId) {
      expandedBinId.value = null;
    }
    await fetchBins();
  } catch (err) {
    setStatus(err.message || "删除 Bin 失败", "error");
  } finally {
    actionLoading.value = false;
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

onMounted(() => {
  fetchBins();
});
</script>

<style scoped>
.profile-page {
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

.profile-page::before,
.profile-page::after {
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

.profile-page::after {
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
  flex-wrap: wrap;
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

.ghost.danger {
  border-color: rgba(248, 113, 113, 0.6);
  color: #b91c1c;
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

.bins-section {
  margin-top: 16px;
}

.bins-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.bin-item {
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(148, 163, 184, 0.45);
  overflow: hidden;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
}

.bin-header {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  background: transparent;
  border: none;
  cursor: pointer;
  text-align: left;
}

.bin-title .name {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.bin-title .name-value {
  color: #0f172a;
  font-weight: 700;
}

.bin-title .meta {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}

.bin-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.caret {
  display: inline-block;
  transition: transform 0.2s ease;
}

.caret.open {
  transform: rotate(180deg);
}

.bin-body {
  padding: 0 18px 18px;
  border-top: 1px solid rgba(148, 163, 184, 0.3);
}

.bin-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: #475569;
  padding: 12px 0;
}

.bin-meta span {
  color: #0f172a;
  font-weight: 600;
}

.token-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.token-form input {
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.6);
  background: rgba(255, 255, 255, 0.9);
}

.token-form button {
  border: none;
  border-radius: 8px;
  background: #0ea5e9;
  color: white;
  padding: 8px 12px;
  cursor: pointer;
}

.token-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.token-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.35);
}

.token-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.token-title {
  font-weight: 600;
}

.token-meta {
  font-size: 12px;
  color: #64748b;
}

.token-value {
  font-size: 12px;
  color: #1e293b;
}

.token-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.token-actions button {
  border: none;
  border-radius: 8px;
  padding: 6px 10px;
  cursor: pointer;
  background: rgba(226, 232, 240, 0.9);
}

.token-actions .danger {
  background: rgba(248, 113, 113, 0.2);
  color: #b91c1c;
}

.empty,
.hint {
  font-size: 13px;
  color: #64748b;
  padding: 10px 0;
}

.status-message {
  margin-top: 12px;
  font-size: 13px;
}

.status-message.success {
  color: #0f766e;
}

.status-message.error {
  color: #b91c1c;
}

@media (max-width: 720px) {
  .header {
    flex-direction: column;
    align-items: flex-start;
  }

  .token-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .token-actions {
    flex-direction: row;
    flex-wrap: wrap;
  }
}
</style>
