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

      <div class="cards-grid">
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
          <div class="card-main helper-main">
            <div class="helper-meta">
              <div class="helper-label">剩余时间</div>
              <div class="helper-value">{{ formatTime(helperRemaining) }}</div>
            </div>
          </div>
          <div class="card-footer-actions">
            <button
              class="card-btn"
              :disabled="bottleLoading || !tokenRecord"
              @click="handleRestartBottle"
            >
              重启罐子
            </button>
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
          </div>
          <div class="card-main helper-main">
            <div class="helper-meta">
              <div class="helper-label">剩余时间</div>
              <div class="helper-value">{{ formatTime(hangupRemaining) }}</div>
              <div class="helper-label">已挂机时间</div>
              <div class="helper-value">{{ formatTime(hangupElapsed) }}</div>
            </div>
          </div>
          <div class="card-footer-actions">
            <button
              class="card-btn"
              :disabled="hangupExtendLoading || !tokenRecord"
              @click="handleExtendHangup"
            >
              加钟
            </button>
            <button
              class="card-btn card-btn-secondary"
              :disabled="hangupClaimLoading || !tokenRecord"
              @click="handleClaimHangupReward"
            >
              获取奖励
            </button>
          </div>
        </div>

        <div class="card daily-card">
          <div class="card-header-line">
            <div class="card-title with-icon">
              <img
                class="card-icon"
                src="/icons/174023274867420.png"
                alt="每日任务"
              />
              每日任务
            </div>
          </div>
          <div class="card-main daily-progress">
            <div class="progress-label">
              进度
              <span class="progress-value">{{ dailyPoint }} / 100</span>
            </div>
            <div class="progress-rail">
              <div
                class="progress-bar"
                :style="{ width: `${dailyPoint}%` }"
              ></div>
            </div>
            <div class="progress-hint">
              先在任务设置中勾选要执行的项目，再一键完成每日任务。
            </div>
          </div>
          <div class="card-footer-actions">
            <button
              class="card-btn card-btn-secondary"
              :disabled="dailyRunLoading || !tokenRecord"
              @click="showDailySettings = true"
            >
              任务设置
            </button>
            <button
              class="card-btn"
              :disabled="dailyRunLoading || !tokenRecord"
              @click="handleRunDailyTasks"
            >
              {{ dailyRunLoading ? "执行中..." : "完成任务" }}
            </button>
          </div>
        </div>

        <div class="card tower-card">
          <div class="card-header-line">
            <div class="card-title with-icon">
              <img
                class="card-icon"
                src="/icons/1733492491706148.png"
                alt="咸将塔"
              />
              咸将塔
            </div>
          </div>
          <div class="card-main tower-overview">
            <div>
              <div class="helper-label">当前层数</div>
              <div class="helper-value">{{ towerFloor }}</div>
            </div>
            <div>
              <div class="helper-label">体力</div>
              <div class="helper-value">{{ towerEnergy }}</div>
            </div>
          </div>
          <div class="card-footer-actions">
            <button
              class="card-btn card-btn-secondary"
              :disabled="towerLoading || !tokenRecord"
              @click="loadTowerInfo(true)"
            >
              刷新
            </button>
            <button
              class="card-btn"
              :disabled="
                towerActionLoading || towerLoading || !tokenRecord || towerClimbing || towerEnergy <= 0
              "
              @click="handleStartTowerClimb"
            >
              {{ towerClimbing ? "爬塔中..." : "开始爬塔" }}
            </button>
            <button
              class="card-btn card-btn-secondary"
              :disabled="towerActionLoading || !towerClimbing"
              @click="handleStopTowerClimb"
            >
              停止爬塔
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="connection-actions-fixed">
      <button
        class="primary"
        :disabled="reconnectLoading || !tokenRecord"
        @click="handleReconnect"
      >
        重新连接
      </button>
      <div class="connection-status" :class="statusClass">
        连接状态：{{ statusText }}
      </div>
    </div>

    <div
      v-if="showDailySettings"
      class="modal-mask"
      @click.self="showDailySettings = false"
    >
      <div class="modal-panel">
        <div class="modal-head">
          <div class="modal-title">任务设置</div>
          <button class="ghost close-btn" @click="showDailySettings = false">
            关闭
          </button>
        </div>
        <div class="modal-body">
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

          <label class="switch-row">
            <input type="checkbox" v-model="dailySettings.claimBottle" />
            <span>领取盐罐奖励</span>
          </label>
          <label class="switch-row">
            <input type="checkbox" v-model="dailySettings.claimHangUp" />
            <span>领取挂机奖励和加钟</span>
          </label>
          <label class="switch-row">
            <input type="checkbox" v-model="dailySettings.arenaEnable" />
            <span>竞技场任务</span>
          </label>
          <label class="switch-row">
            <input type="checkbox" v-model="dailySettings.openBox" />
            <span>开启宝箱</span>
          </label>
          <label class="switch-row">
            <input type="checkbox" v-model="dailySettings.claimEmail" />
            <span>领取邮件奖励</span>
          </label>
          <label class="switch-row">
            <input
              type="checkbox"
              v-model="dailySettings.blackMarketPurchase"
            />
            <span>黑市购买</span>
          </label>
          <label class="switch-row">
            <input type="checkbox" v-model="dailySettings.payRecruit" />
            <span>付费招募</span>
          </label>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
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
const reconnectLoading = ref(false);
const bottleLoading = ref(false);
const hangupExtendLoading = ref(false);
const hangupClaimLoading = ref(false);
const towerActionLoading = ref(false);
const status = ref("disconnected");
const statusNote = ref("");
const statusIsError = ref(false);
const helperRemaining = ref(0);
const helperRunning = ref(false);
const hangupRemaining = ref(0);
const hangupElapsed = ref(0);
const hangupRunning = ref(false);
const roleInfo = ref(null);
const roleLoading = ref(false);
const towerLoading = ref(false);
const towerFloor = ref("0 - 0");
const towerEnergy = ref(0);
const towerClimbing = ref(false);
const dailyRunLoading = ref(false);
const showDailySettings = ref(false);
const BOTTLE_HELPER_RESET_SECONDS = 8 * 3600;
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
const dailySettings = reactive({ ...defaultDailySettings });

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

const dailyPoint = computed(() => {
  const point = roleInfo.value?.role?.dailyTask?.dailyPoint;
  const numeric = Math.max(0, Math.min(100, Number(point) || 0));
  return numeric;
});

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

const sleep = (ms) =>
  new Promise((resolve) => {
    setTimeout(resolve, ms);
  });

const fetchWsStatus = async (tokenJsonValue) => {
  const response = await fetch(
    `/api/v1/xyzw/ws/status?token=${encodeURIComponent(tokenJsonValue)}`,
    { headers: { ...authHeaders() } }
  );
  const payload = await response.json();
  if (!response.ok || !payload.success || !payload.data?.status) {
    return null;
  }
  return payload.data.status;
};

const waitForWsConnected = async (tokenJsonValue, timeoutMs = 5000) => {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const currentStatus = await fetchWsStatus(tokenJsonValue);
    if (currentStatus === "connected") {
      return true;
    }
    await sleep(300);
  }
  return false;
};

const applyTowerFromRoleInfo = (info) => {
  const tower = info?.role?.tower;
  if (!tower || tower.id == null) return;
  const towerId = Math.max(0, Number(tower.id) || 0);
  const floor = Math.floor(towerId / 10) + 1;
  const layer = (towerId % 10) + 1;
  towerFloor.value = `${floor} - ${layer}`;
  towerEnergy.value = Math.max(0, Number(tower.energy) || 0);
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
    loadDailySettings();
    setNote("Token 已加载，可开始操作。", false);
    await loadRoleInfo(true);
    await loadTowerInfo(true);
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
      const total = Math.max(0, Math.floor(Number(hang.hangUpTime) || 0));
      const elapsed = Math.max(0, Math.floor(nowSec - Number(hang.lastTime)));
      const remain = Math.floor(total - elapsed);
      hangupRemaining.value = Math.max(0, remain);
      hangupElapsed.value = Math.max(0, total - hangupRemaining.value);
      hangupRunning.value = hangupRemaining.value > 0;
    }
    applyTowerFromRoleInfo(payload.data?.roleInfo || null);
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

  bottleLoading.value = true;
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

    const connected = await waitForWsConnected(tokenJsonValue, 5000);
    if (!connected) {
      throw new Error("WebSocket 连接失败或超时");
    }

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
    helperRemaining.value = BOTTLE_HELPER_RESET_SECONDS;
    helperRunning.value = true;
    setNote(
      `已经请求重启罐子，剩余时间已重置为 ${formatTime(
        BOTTLE_HELPER_RESET_SECONDS
      )}`,
      false
    );
  } catch (error) {
    status.value = "error";
    setNote(`重启罐子失败: ${error.message}`, true);
  } finally {
    bottleLoading.value = false;
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
  if (!tokenRecord.value?.token) return;
  try {
    const tokenJsonValue = buildTokenJson();
    const currentStatus = await fetchWsStatus(tokenJsonValue);
    if (currentStatus) {
      status.value = currentStatus;
    }
  } catch (error) {
    // ignore
  }
};

const dailySettingsKey = () => {
  if (tokenRecord.value?.id != null) {
    return `daily-settings:${tokenRecord.value.id}`;
  }
  if (tokenId.value) {
    return `daily-settings:${tokenId.value}`;
  }
  return "";
};

const loadDailySettings = () => {
  const key = dailySettingsKey();
  if (!key) return;
  try {
    const raw = localStorage.getItem(key);
    const parsed = raw ? JSON.parse(raw) : null;
    Object.assign(dailySettings, defaultDailySettings, parsed || {});
  } catch (error) {
    Object.assign(dailySettings, defaultDailySettings);
  }
};

const applyDailyPoint = (point) => {
  const value = Math.max(0, Math.min(100, Number(point) || 0));
  const current = roleInfo.value || {};
  const role = current.role && typeof current.role === "object" ? current.role : {};
  const dailyTask =
    role.dailyTask && typeof role.dailyTask === "object" ? role.dailyTask : {};
  roleInfo.value = {
    ...current,
    role: {
      ...role,
      dailyTask: {
        ...dailyTask,
        dailyPoint: value,
      },
    },
  };
};

const handleRunDailyTasks = async () => {
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) {
    setNote("请先加载 Token。", true);
    return;
  }

  dailyRunLoading.value = true;
  try {
    const tokenJsonValue = buildTokenJson();
    const resp = await fetch("/api/v1/xyzw/ws/daily/run", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({
        token: tokenJsonValue,
        settings: { ...dailySettings },
      }),
    });
    const payload = await resp.json();
    if (!resp.ok || !payload.success) {
      throw new Error(payload.message || "执行每日任务失败");
    }

    const dailyPointFromServer = payload?.data?.dailyPoint;
    if (dailyPointFromServer != null) {
      applyDailyPoint(dailyPointFromServer);
    }

    const executedCount = Number(payload?.data?.executedCount || 0);
    setNote(`每日任务已执行，已发送 ${executedCount} 个指令。`, false);
  } catch (error) {
    setNote(`执行每日任务失败: ${error.message}`, true);
  } finally {
    dailyRunLoading.value = false;
  }
};

const handleReconnect = async () => {
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) {
    setNote("请先加载 Token。", true);
    return;
  }
  reconnectLoading.value = true;
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

    const connected = await waitForWsConnected(tokenJsonValue, 5000);
    if (!connected) {
      throw new Error("WebSocket 连接失败或超时");
    }

    status.value = "connected";
    setNote("已重新建立 WebSocket。", false);
    await loadTowerInfo(true);
  } catch (error) {
    status.value = "error";
    setNote(`重连失败: ${error.message}`, true);
  } finally {
    reconnectLoading.value = false;
  }
};

const handleExtendHangup = async () => {
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) {
    setNote("请先加载 Token。", true);
    return;
  }
  hangupExtendLoading.value = true;
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
    const synced = await syncHangupRemaining(tokenJsonValue);
    if (!synced) {
      const remain = Number(payload?.data?.remainingSeconds);
      const elapsed = Number(payload?.data?.elapsedSeconds);
      if (Number.isFinite(remain) && remain >= 0) {
        hangupRemaining.value = Math.floor(remain);
        hangupRunning.value = hangupRemaining.value > 0;
      }
      if (Number.isFinite(elapsed) && elapsed >= 0) {
        hangupElapsed.value = Math.floor(elapsed);
      }
    }
    setNote(`已请求加钟，挂机剩余时间 ${formatTime(hangupRemaining.value)}。`, false);
  } catch (error) {
    setNote(`加钟失败: ${error.message}`, true);
  } finally {
    hangupExtendLoading.value = false;
  }
};

const applyTowerData = (data) => {
  towerFloor.value = data?.floor || "0 - 0";
  towerEnergy.value = Math.max(0, Number(data?.energy) || 0);
};

const loadTowerInfo = async (refresh = true) => {
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) return;
  towerLoading.value = true;
  try {
    const tokenJsonValue = buildTokenJson();
    const response = await fetch(
      `/api/v1/xyzw/ws/tower/info?token=${encodeURIComponent(
        tokenJsonValue
      )}&refresh=${refresh}`,
      { headers: { ...authHeaders() } }
    );
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || "获取咸将塔信息失败");
    }
    applyTowerData(payload.data || {});
  } catch (error) {
    setNote(`获取咸将塔信息失败: ${error.message}`, true);
  } finally {
    towerLoading.value = false;
  }
};

const handleTowerChallengeOnce = async () => {
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) {
    setNote("请先加载 Token。", true);
    return;
  }
  towerActionLoading.value = true;
  towerLoading.value = true;
  try {
    const tokenJsonValue = buildTokenJson();
    const response = await fetch("/api/v1/xyzw/ws/tower/challenge", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ token: tokenJsonValue }),
    });
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || "咸将塔挑战失败");
    }
    applyTowerData(payload.data || {});
    setNote("咸将塔挑战请求已发送。", false);
  } catch (error) {
    setNote(`咸将塔挑战失败: ${error.message}`, true);
  } finally {
    towerLoading.value = false;
    towerActionLoading.value = false;
  }
};

const handleStartTowerClimb = async () => {
  if (towerClimbing.value) return;
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) {
    setNote("请先加载 Token。", true);
    return;
  }
  if (towerEnergy.value <= 0) {
    setNote("当前体力不足，无法爬塔。", true);
    return;
  }

  towerClimbing.value = true;
  let count = 0;
  const maxCount = 100;

  try {
    while (towerClimbing.value && count < maxCount) {
      if (towerEnergy.value <= 0) break;
      await handleTowerChallengeOnce();
      count += 1;
      if (!towerClimbing.value) break;
      await sleep(800);
      await loadTowerInfo(true);
    }
    if (towerEnergy.value <= 0) {
      setNote(`爬塔结束：体力已用完，共挑战 ${count} 次。`, false);
    } else {
      setNote(`爬塔已停止，共挑战 ${count} 次。`, false);
    }
  } finally {
    towerClimbing.value = false;
  }
};

const handleStopTowerClimb = () => {
  towerClimbing.value = false;
};

const handleClaimHangupReward = async () => {
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) {
    setNote("请先加载 Token。", true);
    return;
  }
  hangupClaimLoading.value = true;
  try {
    const tokenJsonValue = buildTokenJson();
    const resp = await fetch("/api/v1/xyzw/ws/hangup/claim", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ token: tokenJsonValue }),
    });
    const payload = await resp.json();
    if (!resp.ok || !payload.success) {
      throw new Error(payload.message || "领取挂机奖励失败");
    }
    const synced = await syncHangupRemaining(tokenJsonValue);
    if (!synced) {
      const remain = Number(payload?.data?.remainingSeconds);
      const elapsed = Number(payload?.data?.elapsedSeconds);
      if (Number.isFinite(remain) && remain >= 0) {
        hangupRemaining.value = Math.floor(remain);
        hangupRunning.value = hangupRemaining.value > 0;
      }
      if (Number.isFinite(elapsed) && elapsed >= 0) {
        hangupElapsed.value = Math.floor(elapsed);
      }
    }
    setNote(
      `挂机奖励已请求，剩余时间 ${formatTime(hangupRemaining.value)}。`,
      false
    );
  } catch (error) {
    setNote(`领取挂机奖励失败: ${error.message}`, true);
  } finally {
    hangupClaimLoading.value = false;
  }
};

const syncHangupRemaining = async (tokenJsonValue) => {
  const response = await fetch(
    `/api/v1/xyzw/ws/hangup/remaining?token=${encodeURIComponent(
      tokenJsonValue
    )}`,
    { headers: { ...authHeaders() } }
  );
  const payload = await response.json();
  if (!response.ok || !payload.success) {
    return false;
  }
  const remain = Number(payload?.data?.remainingSeconds);
  const elapsed = Number(payload?.data?.elapsedSeconds);
  if (!Number.isFinite(remain) || remain < 0) {
    return false;
  }
  hangupRemaining.value = Math.floor(remain);
  hangupRunning.value = hangupRemaining.value > 0;
  if (Number.isFinite(elapsed) && elapsed >= 0) {
    hangupElapsed.value = Math.floor(elapsed);
  }
  return true;
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

watch(
  dailySettings,
  () => {
    const key = dailySettingsKey();
    if (!key) return;
    localStorage.setItem(key, JSON.stringify(dailySettings));
  },
  { deep: true }
);

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
    hangupElapsed.value = Math.max(0, hangupElapsed.value + 1);
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

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
  gap: 10px;
  width: 100%;
  margin-top: 8px;
}

.card {
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(148, 163, 184, 0.45);
  padding: 18px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
  min-height: 188px;
  display: flex;
  flex-direction: column;
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
  margin-top: 0;
}

.hangup-card {
  margin-top: 0;
}

.card-header-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.card-main {
  flex: 1;
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

.card-footer-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  align-items: flex-end;
  margin-top: 12px;
  flex-wrap: wrap;
}

.card-btn {
  min-width: 88px;
  height: 34px;
  padding: 0 14px;
  border-radius: 10px;
  border: 1px solid transparent;
  cursor: pointer;
  background: linear-gradient(130deg, #9aa5b5, #7f8c9e);
  color: #f8fafc;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 8px 16px rgba(15, 23, 42, 0.12);
  transition: transform 0.15s ease, box-shadow 0.15s ease, opacity 0.15s ease;
}

.card-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 20px rgba(15, 23, 42, 0.16);
}

.card-btn:disabled {
  opacity: 0.48;
  cursor: not-allowed;
  box-shadow: none;
}

.card-btn-secondary {
  background: rgba(255, 255, 255, 0.86);
  color: #334155;
  border-color: rgba(148, 163, 184, 0.75);
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.08);
}

.helper-main {
  display: flex;
  align-items: flex-start;
}

.daily-progress {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.daily-card .progress-label {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #334155;
  margin-bottom: 6px;
}

.daily-card .progress-value {
  font-weight: 700;
  color: #0f172a;
}

.daily-card .progress-rail {
  width: 100%;
  height: 10px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.2);
  overflow: hidden;
  box-shadow: inset 0 1px 3px rgba(15, 23, 42, 0.08);
}

.daily-card .progress-bar {
  height: 100%;
  background: linear-gradient(120deg, #22c55e, #16a34a);
  border-radius: inherit;
  transition: width 0.25s ease;
}

.daily-card .progress-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #64748b;
}

.tower-overview {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  align-content: start;
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

.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 80;
}

.modal-panel {
  width: min(92vw, 420px);
  max-height: 80vh;
  overflow: auto;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.45);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.22);
  padding: 14px;
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.modal-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.close-btn {
  padding: 4px 10px;
  font-size: 12px;
}

.modal-body {
  display: grid;
  gap: 10px;
}

.setting-row {
  display: grid;
  gap: 4px;
}

.setting-row label {
  font-size: 12px;
  color: #475569;
}

.setting-row select {
  height: 34px;
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.6);
  background: #fff;
  color: #0f172a;
  padding: 0 10px;
}

.switch-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #334155;
  padding: 2px 0;
}

.switch-row input {
  width: 16px;
  height: 16px;
}

@media (max-width: 900px) {
  .cards-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .header {
    flex-direction: column;
    align-items: flex-start;
  }

  .card-footer-actions {
    width: 100%;
    justify-content: stretch;
  }

  .card-footer-actions .card-btn {
    width: 100%;
    text-align: center;
  }
}

.identity-wrapper {
  margin: 0 auto 10px;
  width: 100%;
}
</style>







