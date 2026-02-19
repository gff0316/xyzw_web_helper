<template>
  <div class="batch-page">
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
          <h1>批量日常任务</h1>
          <p>选择账号与任务设置，一键执行每日任务。</p>
          <p class="hint">
            定时规则请在右侧“后端调度配置”维护，本页只保留手动批量执行。
          </p>
        </div>
        <div class="header-actions">
          <button
            class="primary"
            type="button"
            :disabled="isRunning || selectedTokenKeys.length === 0"
            @click="startBatch"
          >
            {{ isRunning ? "执行中..." : "开始执行" }}
          </button>
          <button
            class="ghost"
            type="button"
            :disabled="!isRunning"
            @click="stopBatch"
          >
            停止
          </button>
          <button class="ghost" type="button" @click="fetchTokens">
            刷新账号
          </button>
        </div>
      </div>

      <div class="main-grid">
        <div class="left-panel">
          <div class="card token-card">
            <div class="card-header">
              <div class="card-title">账号列表</div>
              <label class="token-select-all">
                <input
                  type="checkbox"
                  :checked="allSelected"
                  @change="toggleSelectAll"
                />
                <span>全选（{{ selectedTokenKeys.length }}/{{ tokens.length }}）</span>
              </label>
            </div>
            <div class="card-body">
              <div v-if="tokensLoading" class="card-empty">正在加载...</div>
              <div v-else-if="tokens.length === 0" class="card-empty">
                暂无账号，请先上传 Bin 并生成 Token。
              </div>
              <div v-else class="token-list">
                <label
                  v-for="token in tokens"
                  :key="token.key"
                  class="token-row"
                >
                  <input
                    type="checkbox"
                    :value="token.key"
                    v-model="selectedTokenKeys"
                  />
                  <div class="token-info">
                    <div class="token-name">{{ token.displayName }}</div>
                    <div class="token-meta">
                      {{ token.serverLabel }} · {{ token.binLabel }}
                    </div>
                  </div>
                </label>
              </div>
            </div>
          </div>

          <div class="card settings-card">
            <div class="card-header">
              <div class="card-title">任务设置</div>
              <div class="header-actions">
                <div class="setting-inline">
                  <label>任务间隔(ms)</label>
                  <input
                    type="number"
                    min="0"
                    v-model.number="delaySettings.taskDelay"
                  />
                </div>
                <button class="ghost" type="button" @click="resetDailySettings">
                  恢复默认
                </button>
              </div>
            </div>
            <div class="card-body">
              <div class="setting-grid">
                <div class="setting-row">
                  <label>竞技场阵容</label>
                  <select v-model.number="dailySettings.arenaFormation">
                    <option :value="1">阵容1</option>
                    <option :value="2">阵容2</option>
                    <option :value="3">阵容3</option>
                    <option :value="4">阵容4</option>
                  </select>
                </div>
                <div class="setting-row">
                  <label>BOSS阵容</label>
                  <select v-model.number="dailySettings.bossFormation">
                    <option :value="1">阵容1</option>
                    <option :value="2">阵容2</option>
                    <option :value="3">阵容3</option>
                    <option :value="4">阵容4</option>
                  </select>
                </div>
                <div class="setting-row">
                  <label>BOSS次数</label>
                  <select v-model.number="dailySettings.bossTimes">
                    <option :value="0">0次</option>
                    <option :value="1">1次</option>
                    <option :value="2">2次</option>
                    <option :value="3">3次</option>
                    <option :value="4">4次</option>
                  </select>
                </div>
              </div>

              <div class="setting-section">
                <div class="setting-section-title">基础收益任务</div>
                <div class="switch-group">
                  <div class="setting-option">
                    <label class="switch-row">
                      <input type="checkbox" v-model="dailySettings.claimHangUp" />
                      <span>领取挂机奖励和加钟</span>
                    </label>
                    <div class="setting-desc">
                      先领挂机，再执行多次加钟回调，适合长期挂机账号。
                    </div>
                  </div>
                  <div class="setting-option">
                    <label class="switch-row">
                      <input type="checkbox" v-model="dailySettings.claimBottle" />
                      <span>重启并领取盐罐</span>
                    </label>
                    <div class="setting-desc">
                      执行停止/启动/领取流程；无奖励时会自动跳过。
                    </div>
                  </div>
                  <div class="setting-option">
                    <label class="switch-row">
                      <input type="checkbox" v-model="dailySettings.openBox" />
                      <span>开启宝箱</span>
                    </label>
                    <div class="setting-desc">
                      使用固定宝箱参数，失败会继续后续流程。
                    </div>
                  </div>
                  <div class="setting-option">
                    <label class="switch-row">
                      <input type="checkbox" v-model="dailySettings.claimEmail" />
                      <span>领取邮件奖励</span>
                    </label>
                    <div class="setting-desc">
                      执行一键领取附件，减少遗漏奖励。
                    </div>
                  </div>
                </div>
              </div>

              <div class="setting-section">
                <div class="setting-section-title">战斗与成长任务</div>
                <div class="switch-group">
                  <div class="setting-option">
                    <label class="switch-row">
                      <input type="checkbox" v-model="dailySettings.arenaEnable" />
                      <span>竞技场任务</span>
                    </label>
                    <div class="setting-desc">
                      使用上方竞技场阵容，自动找目标并发起战斗。
                    </div>
                  </div>
                  <div class="setting-option">
                    <label class="switch-row">
                      <input type="checkbox" v-model="dailySettings.payRecruit" />
                      <span>付费招募</span>
                    </label>
                    <div class="setting-desc">
                      在免费招募后执行一次付费招募；冷却时自动跳过。
                    </div>
                  </div>
                  <div class="setting-option">
                    <label class="switch-row">
                      <input
                        type="checkbox"
                        v-model="dailySettings.blackMarketPurchase"
                      />
                      <span>黑市购买</span>
                    </label>
                    <div class="setting-desc">
                      执行一次黑市购买指令，失败不会中断整轮任务。
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

        </div>

        <div class="right-panel">
          <div class="card scheduler-card">
            <div class="card-header">
              <div class="card-title">后端调度配置</div>
              <button class="ghost" type="button" @click="fetchJobConfigs">
                刷新
              </button>
            </div>
            <div class="card-body">
              <div v-if="jobConfigsLoading" class="card-empty">正在加载...</div>
              <div v-else-if="jobConfigs.length === 0" class="card-empty">
                暂无后端任务配置
              </div>
              <div v-else class="job-config-list">
                <div
                  v-for="cfg in jobConfigs"
                  :key="cfg.jobKey"
                  class="job-config-item"
                >
                  <div class="job-config-head">
                    <div class="job-config-name">
                      {{ cfg.jobName || formatBackendJobName(cfg.jobKey) }}
                    </div>
                    <label class="switch-row">
                      <input type="checkbox" v-model="cfg.enabled" />
                      <span>启用</span>
                    </label>
                  </div>
                  <div class="job-config-body">
                    <input v-model="cfg.cronExpr" placeholder="Cron 表达式" />
                    <div class="job-config-actions">
                      <button class="ghost" type="button" @click="saveJobConfig(cfg)">
                        保存
                      </button>
                      <button class="ghost" type="button" @click="runBackendJob(cfg.jobKey)">
                        立即执行
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { g_utils } from "../utils/bonProtocol.js";

const router = useRouter();

const authToken = ref(localStorage.getItem("authToken") || "");
const authUser = ref(
  localStorage.getItem("authUser")
    ? JSON.parse(localStorage.getItem("authUser"))
    : null,
);

const tokens = ref([]);
const tokensLoading = ref(false);
const selectedTokenKeys = ref([]);
const logEntries = ref([]);
const isRunning = ref(false);
const stopRequested = ref(false);
const jobConfigs = ref([]);
const jobConfigsLoading = ref(false);

const delaySettings = reactive({
  taskDelay: 500,
});

const defaultDailySettings = {
  arenaFormation: 1,
  bossFormation: 1,
  bossTimes: 2,
  claimBottle: true,
  payRecruit: true,
  openBox: true,
  arenaEnable: true,
  claimHangUp: true,
  claimEmail: true,
  blackMarketPurchase: true,
};

const backendJobNameMap = {
  batchDailyRunner: "批量日常扫描",
  batchDailyTask: "批量日常任务",
  tokenMaintenanceRunner: "罐子和挂机奖励",
  tokenRefreshRunner: "Token 刷新",
};

const backendStatusTextMap = {
  SUCCESS: "执行成功",
  FAILED: "执行失败",
  PARTIAL: "部分成功",
  RUNNING: "执行中",
};

const dailySettings = reactive({ ...defaultDailySettings });

const authHeaders = () =>
  authToken.value ? { Authorization: `Bearer ${authToken.value}` } : {};

const ensureAuth = () => {
  if (!authToken.value) {
    router.push("/login");
    return false;
  }
  return true;
};

const handleLogout = () => {
  localStorage.removeItem("authToken");
  localStorage.removeItem("authUser");
  router.push("/login");
};

const addLog = (message, type = "info") => {
  logEntries.value.unshift({
    time: new Date().toLocaleTimeString(),
    message,
    type,
  });
  if (logEntries.value.length > 200) {
    logEntries.value.length = 200;
  }
};

const formatBinName = (bin) => {
  const raw = bin?.name;
  if (typeof raw === "string" && raw.trim()) return raw.trim();
  if (bin?.filePath) return bin.filePath;
  if (bin?.id != null) return `Bin #${bin.id}`;
  return "未命名";
};

const buildTokenList = (bins) => {
  const list = [];
  (bins || []).forEach((bin) => {
    const binLabel = formatBinName(bin);
    (bin.tokens || []).forEach((token, index) => {
      const key = token.id ?? token.uuid ?? `${bin.id}-${index}`;
      list.push({
        ...token,
        key,
        binLabel,
        displayName: token.name || token.uuid || `Token ${key}`,
        serverLabel: token.server || "未配置服务器",
      });
    });
  });
  return list;
};

const fetchTokens = async () => {
  if (!ensureAuth()) return;
  tokensLoading.value = true;
  try {
    const response = await fetch("/api/v1/xyzw/bins", {
      headers: { ...authHeaders() },
    });
    if (!response.ok) {
      throw new Error("获取账号列表失败");
    }
    const payload = await response.json();
    tokens.value = buildTokenList(payload?.data?.bins || []);
    if (selectedTokenKeys.value.length === 0) {
      selectedTokenKeys.value = tokens.value.map((item) => item.key);
    }
  } catch (error) {
    addLog(error.message || "获取账号列表失败", "error");
  } finally {
    tokensLoading.value = false;
  }
};

const allSelected = computed(
  () => tokens.value.length > 0 && selectedTokenKeys.value.length === tokens.value.length,
);

const toggleSelectAll = () => {
  if (allSelected.value) {
    selectedTokenKeys.value = [];
  } else {
    selectedTokenKeys.value = tokens.value.map((item) => item.key);
  }
};

const selectedTokens = computed(() => {
  const set = new Set(selectedTokenKeys.value);
  return tokens.value.filter((item) => set.has(item.key));
});

const decodeTokenFromBase64 = (encoded) => {
  const binary = atob(encoded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  const msg = g_utils.parse(bytes);
  return msg.getData();
};

const buildTokenJson = (token) => {
  if (!token?.token) return "";
  const data = decodeTokenFromBase64(token.token);
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

const sleep = (ms) =>
  new Promise((resolve) => {
    setTimeout(resolve, ms);
  });

const runDailyForToken = async (token, settings) => {
  const tokenJson = buildTokenJson(token);
  if (!tokenJson) {
    throw new Error("Token 数据为空");
  }
  const resp = await fetch("/api/v1/xyzw/ws/daily/run", {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({
      token: tokenJson,
      settings,
    }),
  });
  const payload = await resp.json();
  if (!resp.ok || !payload.success) {
    throw new Error(payload.message || "执行失败");
  }
  return payload?.data || {};
};

const runBatchTokens = async (tokenList, settings, label = "批量日常任务") => {
  if (tokenList.length === 0) {
    addLog("请选择至少一个账号。", "warning");
    return;
  }

  isRunning.value = true;
  stopRequested.value = false;
  addLog(`${label}开始执行，共 ${tokenList.length} 个账号。`, "info");
  let successCount = 0;
  let failCount = 0;

  for (const token of tokenList) {
    if (stopRequested.value) {
      addLog("已收到停止指令，任务中断。", "warning");
      break;
    }
    const name = token.displayName || token.uuid || token.id || "未知账号";
    try {
      const data = await runDailyForToken(token, settings);
      const executedCount = Number(data.executedCount || 0);
      addLog(`完成：${name}，已发送 ${executedCount} 个指令。`, "success");
      successCount += 1;
    } catch (error) {
      addLog(`失败：${name}，${error.message}`, "error");
      failCount += 1;
    }
    await sleep(delaySettings.taskDelay);
  }

  isRunning.value = false;
  stopRequested.value = false;
  addLog(`${label}执行结束：成功 ${successCount}，失败 ${failCount}。`, "success");
};

const startBatch = async () => {
  if (isRunning.value) return;
  await runBatchTokens(selectedTokens.value, { ...dailySettings });
};

const stopBatch = () => {
  if (!isRunning.value) return;
  stopRequested.value = true;
};

const saveDailySettings = () => {
  localStorage.setItem(
    "batch-daily-settings",
    JSON.stringify({ ...dailySettings }),
  );
};

const loadDailySettings = () => {
  const raw = localStorage.getItem("batch-daily-settings");
  if (!raw) return;
  try {
    Object.assign(dailySettings, defaultDailySettings, JSON.parse(raw));
  } catch (error) {
    Object.assign(dailySettings, defaultDailySettings);
  }
};

const resetDailySettings = () => {
  Object.assign(dailySettings, defaultDailySettings);
  addLog("任务设置已恢复默认值。", "success");
};

const fetchJobConfigs = async () => {
  if (!ensureAuth()) return;
  jobConfigsLoading.value = true;
  try {
    const resp = await fetch("/api/v1/jobs/configs", {
      headers: { ...authHeaders() },
    });
    const payload = await resp.json();
    if (!resp.ok || !payload.success) {
      throw new Error(payload.message || "获取后端调度配置失败");
    }
    jobConfigs.value = (payload?.data?.configs || []).map((item) => ({
      ...item,
      enabled: !!item.enabled,
      cronExpr: item.cronExpr || "",
    }));
  } catch (error) {
    addLog(error.message || "获取后端调度配置失败", "error");
  } finally {
    jobConfigsLoading.value = false;
  }
};

const saveJobConfig = async (cfg) => {
  if (!ensureAuth()) return;
  try {
    const resp = await fetch(`/api/v1/jobs/configs/${cfg.jobKey}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({
        cronExpr: cfg.cronExpr,
        enabled: !!cfg.enabled,
      }),
    });
    const payload = await resp.json();
    if (!resp.ok || !payload.success) {
      throw new Error(payload.message || "保存调度配置失败");
    }
    addLog(`已保存后端调度配置：${formatBackendJobName(cfg.jobKey)}`, "success");
    await fetchJobConfigs();
  } catch (error) {
    addLog(error.message || "保存调度配置失败", "error");
  }
};

const runBackendJob = async (jobKey) => {
  if (!ensureAuth()) return;
  try {
    const resp = await fetch(`/api/v1/jobs/run/${jobKey}`, {
      method: "POST",
      headers: { ...authHeaders() },
    });
    const payload = await resp.json();
    if (!resp.ok || !payload.success) {
      throw new Error(payload.message || "触发后端任务失败");
    }
    addLog(`已触发后端任务：${formatBackendJobName(jobKey)}`, "success");
  } catch (error) {
    addLog(error.message || "触发后端任务失败", "error");
  }
};

const formatBackendJobName = (jobKey) => {
  const raw = String(jobKey || "").trim();
  if (!raw) return "未知任务";
  return backendJobNameMap[raw] || raw;
};

watch(
  () => ({ ...dailySettings }),
  () => {
    saveDailySettings();
  },
  { deep: true },
);

onMounted(() => {
  loadDailySettings();
  fetchTokens();
  fetchJobConfigs();
});

// no-op
</script>

<style scoped>
.batch-page {
  min-height: 100vh;
  background: radial-gradient(circle at 20% 20%, #dff2ff, transparent 50%),
    radial-gradient(circle at 80% 10%, #e3e8ff, transparent 45%),
    #f2f6ff;
  padding: 32px 16px 80px;
  color: #0f172a;
}

.logout-fab {
  position: fixed;
  top: 20px;
  right: 20px;
  border: none;
  background: #2b3240;
  color: #fff;
  padding: 8px 14px;
  border-radius: 999px;
  cursor: pointer;
  font-size: 12px;
}

.content {
  max-width: 1200px;
  margin: 0 auto;
}

.header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
}

.header h1 {
  font-size: 24px;
  margin: 0 0 4px;
}

.header p {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}

.header .hint {
  margin-top: 4px;
}

.header-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.primary {
  padding: 10px 16px;
  border-radius: 10px;
  border: none;
  cursor: pointer;
  background: linear-gradient(135deg, #8f9fb0, #6e7a8a);
  color: #f8fafc;
  font-weight: 600;
}

.primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ghost {
  padding: 8px 14px;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.6);
  background: rgba(255, 255, 255, 0.7);
  color: #334155;
  cursor: pointer;
}

.ghost.danger {
  color: #b91c1c;
  border-color: rgba(248, 113, 113, 0.6);
}

.main-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: 18px;
}

.card {
  background: rgba(255, 255, 255, 0.88);
  border-radius: 16px;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.12);
  padding: 16px;
}

.card + .card {
  margin-top: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.card-title {
  font-size: 15px;
  font-weight: 700;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card-empty {
  color: #64748b;
  font-size: 13px;
}

.token-select-all {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #475569;
}

.token-list {
  display: grid;
  gap: 8px;
  max-height: 320px;
  overflow-y: auto;
  padding-right: 4px;
}

.token-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px;
  border-radius: 12px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(248, 250, 252, 0.7);
}

.token-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.token-name {
  font-weight: 600;
  font-size: 13px;
}

.token-meta {
  font-size: 12px;
  color: #64748b;
}

.setting-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
}

.setting-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: #475569;
}

.setting-row select,
.setting-row input {
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.6);
  padding: 6px 8px;
  background: white;
}

.setting-inline {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #475569;
}

.setting-inline input {
  width: 80px;
  padding: 4px 6px;
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.6);
}

.setting-section {
  display: grid;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px dashed rgba(148, 163, 184, 0.35);
}

.setting-section + .setting-section {
  margin-top: 4px;
}

.setting-section-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.switch-group {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}

.setting-option {
  display: grid;
  gap: 4px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid rgba(203, 213, 225, 0.9);
  background: rgba(248, 250, 252, 0.75);
}

.switch-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 13px;
  color: #475569;
}

.switch-row input {
  margin-top: 2px;
}

.switch-row.compact {
  align-items: center;
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 999px;
  border: 1px solid rgba(203, 213, 225, 0.9);
  background: rgba(248, 250, 252, 0.8);
  color: #334155;
}

.switch-row.compact input {
  margin-top: 0;
}

.setting-desc {
  font-size: 12px;
  line-height: 1.4;
  color: #64748b;
  padding-left: 22px;
}

.job-config-list {
  display: grid;
  gap: 10px;
}

.job-config-item {
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(248, 250, 252, 0.8);
  border-radius: 12px;
  padding: 10px;
  display: grid;
  gap: 8px;
}

.job-config-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.job-config-name {
  font-size: 13px;
  font-weight: 600;
}

.job-config-body {
  display: grid;
  gap: 8px;
}

.job-config-body input {
  padding: 6px 8px;
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.6);
}

.job-config-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.log-body {
  max-height: 520px;
  overflow-y: auto;
}

.sub-title {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}

.log-list {
  display: grid;
  gap: 8px;
}

.log-item {
  display: flex;
  gap: 10px;
  font-size: 12px;
  color: #334155;
  padding: 6px 8px;
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.7);
}

.log-item.success {
  color: #166534;
  background: rgba(220, 252, 231, 0.7);
}

.log-item.error {
  color: #b91c1c;
  background: rgba(254, 226, 226, 0.7);
}

.log-item.warning {
  color: #92400e;
  background: rgba(254, 243, 199, 0.7);
}

.log-time {
  font-weight: 600;
}

.right-panel .card {
  height: auto;
}

@media (max-width: 960px) {
  .main-grid {
    grid-template-columns: 1fr;
  }

  .switch-row.compact {
    padding: 3px 7px;
  }
}
</style>
