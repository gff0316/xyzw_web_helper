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

      <div class="section-tabs">
        <button
          v-for="section in sections"
          :key="section.key"
          type="button"
          class="tab-button"
          :class="{ active: activeSection === section.key }"
          @click="activeSection = section.key"
        >
          {{ section.label }}
        </button>
      </div>

      <div v-if="activeSection === 'daily'" class="cards-grid">
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

      <div
        v-else-if="activeSection === 'club' || activeSection === 'race'"
        class="club-grid"
      >
        <div v-if="activeSection === 'club'" class="card club-info-card">
          <div class="card-header-line">
            <div class="card-title with-icon">
              <img
                class="card-icon"
                src="/icons/1733492491706152.png"
                alt="俱乐部信息"
              />
              俱乐部信息
            </div>
            <div class="club-header-actions">
              <div class="club-signin" :class="{ active: clubSignedIn }">
                {{ clubSignedIn ? "已签到" : "待签到" }}
              </div>
              <div class="club-badge" :class="{ active: !!clubSummary }">
                {{ clubSummary ? "已加入" : "暂无俱乐部" }}
              </div>
            </div>
          </div>
          <div class="card-main club-info-main">
            <div v-if="clubLoading" class="club-empty">加载中...</div>
            <div v-else-if="!clubSummary" class="club-empty">
              暂无俱乐部信息
            </div>
            <div v-else class="club-info-body">
              <div class="club-info-header">
                <div class="club-name">
                  {{ clubSummary.name || "未命名俱乐部" }}
                </div>
                <div class="club-meta">
                  ID {{ clubSummary.id || "--" }} · Lv.{{
                    clubSummary.level || "--"
                  }}
                  · 服务器 {{ formatServerId(clubSummary.serverId) }}
                </div>
              </div>
              <div class="club-info-grid">
                <div class="club-info-item">
                  <div class="label">战力</div>
                  <div class="value">{{ formatPowerYi(clubSummary.power) }}</div>
                </div>
                <div class="club-info-item">
                  <div class="label">成员数</div>
                  <div class="value">{{ clubSummary.memberCount }}</div>
                </div>
                <div class="club-info-item">
                  <div class="label">红粹</div>
                  <div class="value">
                    {{ formatNumber(clubSummary.redQuench) }}
                  </div>
                </div>
                <div class="club-info-item">
                  <div class="label">公告</div>
                  <div class="value">
                    {{ clubSummary.announcement || "—" }}
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="card-footer-actions">
            <button
              class="card-btn card-btn-secondary"
              :disabled="clubLoading || !tokenRecord"
              @click="loadClubInfo(true)"
            >
              刷新
            </button>
            <button
              class="card-btn"
              :disabled="clubSignLoading || !tokenRecord || clubSignedIn"
              @click="handleClubSignIn"
            >
              {{
                clubSignLoading
                  ? "签到中..."
                  : clubSignedIn
                    ? "已签到"
                    : "俱乐部签到"
              }}
            </button>
          </div>
        </div>

        <div class="card club-car-card">
          <div class="card-header-line">
            <div class="card-title with-icon">
              <img class="card-icon" src="/icons/疯狂赛车.png" alt="俱乐部赛车" />
              俱乐部赛车
            </div>
            <div class="club-badge" :class="{ active: clubCars.length > 0 }">
              {{ clubCars.length > 0 ? `共 ${clubCars.length} 辆` : "暂无数据" }}
            </div>
          </div>
          <div class="card-main club-car-main">
            <div v-if="clubCarsLoading" class="club-empty">加载中...</div>
            <div v-else-if="clubCars.length === 0" class="club-empty">
              暂无车辆数据
            </div>
            <div v-else class="club-car-grid">
              <div
                v-for="(car, index) in clubCars"
                :key="car.id || car.key || index"
                class="club-car-item"
              >
                <div class="club-car-body">
                  <div class="club-car-title">
                    <span class="club-car-name">{{
                      car.name ||
                      car.carName ||
                      car.title ||
                      car.model ||
                      car.carConf?.name ||
                      car.conf?.name ||
                      `车位 ${getCarId(car) || index + 1}`
                    }}</span>
                    <span class="club-car-grade">{{
                      carGradeLabel(car.color)
                    }}</span>
                  </div>
                  <div class="club-car-meta" v-if="car.level != null">
                    等级：{{ car.level }}
                  </div>
                  <div class="club-car-meta" v-if="car.star != null">
                    星级：{{ car.star }}
                  </div>
                  <div
                    class="club-car-meta"
                    v-if="car.helperName || car.helperId"
                  >
                    护卫：{{ car.helperName || car.helperId }}
                  </div>
                  <div class="club-car-meta">
                    状态：{{ carStatusLabel(car) }}
                  </div>
                  <div class="club-car-meta">
                    奖励：{{ carRewardSummary(car) }}
                  </div>
                  <div
                    v-if="Array.isArray(car.rewards) && car.rewards.length"
                    class="club-car-rewards"
                  >
                    <div class="club-car-reward-title">奖励明细</div>
                    <div class="club-car-reward-list">
                      <span
                        v-for="(reward, rIndex) in car.rewards"
                        :key="`${getCarId(car)}-${rIndex}`"
                        class="club-car-reward-chip"
                      >
                        {{ formatRewardLabel(reward) }}
                      </span>
                    </div>
                  </div>
                </div>
                <div class="club-car-actions">
                  <button
                    class="card-btn card-btn-secondary"
                    :disabled="clubCarsLoading || !tokenRecord"
                    @click="handleRefreshCar(car)"
                  >
                    刷新
                  </button>
                  <button
                    class="card-btn"
                    :disabled="clubCarsLoading || !tokenRecord"
                    @click="handleSendCar(car)"
                  >
                    发车
                  </button>
                  <button
                    class="card-btn card-btn-secondary"
                    :disabled="clubCarsLoading || !tokenRecord"
                    @click="handleClaimCar(car)"
                  >
                    收车
                  </button>
                </div>
              </div>
            </div>
          </div>
          <div class="card-footer-actions">
            <button
              class="card-btn card-btn-secondary"
              :disabled="clubCarsLoading || !tokenRecord"
              @click="loadClubCars(true)"
            >
              刷新数据
            </button>
            <button
              class="card-btn"
              :disabled="clubCarsLoading || !tokenRecord || clubCars.length === 0"
              @click="handleSendAllCars"
            >
              智能发车
            </button>
            <button
              class="card-btn card-btn-secondary"
              :disabled="clubCarsLoading || !tokenRecord || clubCars.length === 0"
              @click="handleClaimAllCars"
            >
              一键收车
            </button>
          </div>
        </div>
      </div>

      <div v-else class="section-placeholder">
        <div class="placeholder-title">{{ activeSectionLabel }}</div>
        <div class="placeholder-desc">功能正在迁移中，敬请期待。</div>
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
const sections = [
  { key: "daily", label: "日常" },
  { key: "club", label: "俱乐部" },
  { key: "activity", label: "活动" },
  { key: "tools", label: "工具" },
  { key: "salt", label: "盐场" },
  { key: "stall", label: "摆摊" },
  { key: "arena", label: "竞技场" },
  { key: "boss", label: "军团boss" },
  { key: "race", label: "俱乐部赛车" },
  { key: "fishing", label: "钓鱼" },
];
const activeSection = ref("daily");
const clubInfo = ref(null);
const clubLoading = ref(false);
const clubSignLoading = ref(false);
const clubCars = ref([]);
const clubCarsLoading = ref(false);

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

const activeSectionLabel = computed(() => {
  const match = sections.find((item) => item.key === activeSection.value);
  return match ? match.label : "功能";
});

const clubSummary = computed(() => {
  if (!clubInfo.value || typeof clubInfo.value !== "object") return null;
  const info =
    clubInfo.value.legionInfo ||
    clubInfo.value.info ||
    clubInfo.value.legion ||
    clubInfo.value;
  if (!info || typeof info !== "object") return null;
  const stats = info.statistics || clubInfo.value.statistics || {};
  const members =
    info.members || clubInfo.value.members || clubInfo.value.memberMap || {};
  const memberCount =
    info.memberCount ||
    info.membersCount ||
    info.memberNum ||
    Object.keys(members || {}).length;
  return {
    name: info.name || info.legionName || info.clubName || "",
    id: info.id || info.legionId || info.clubId || "",
    level: info.level || info.legionLevel || info.clubLevel || "",
    serverId: info.serverId || info.server || info.serverID || info.srvId || "",
    power:
      stats["max:power"] ||
      stats["power"] ||
      info.power ||
      info.totalPower ||
      0,
    redQuench:
      stats["battle:red:quench"] ||
      stats["red:quench"] ||
      info.redQuench ||
      0,
    memberCount: memberCount || 0,
    announcement: info.announcement || "",
  };
});

const clubSignedIn = computed(() => {
  const ts = Number(
    roleInfo.value?.role?.statisticsTime?.["legion:sign:in"] || 0
  );
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const todaySec = Math.floor(today.getTime() / 1000);
  return ts > todaySec;
});

const formatNumber = (value) => {
  if (value == null || value === "") return "--";
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return String(value);
  return numeric.toLocaleString();
};

const itemMapping = {
  1001: "招募令",
  1003: "进阶石",
  1006: "精铁",
  1007: "竞技场门票",
  1008: "木柴火把",
  1009: "青铜火把",
  1010: "咸神火把",
  1011: "普通鱼竿",
  1012: "黄金鱼竿",
  1013: "珍珠",
  1014: "军团币",
  1016: "晶石",
  1017: "复活丹",
  1019: "盐靛",
  1020: "皮肤币",
  1021: "扫荡魔毯",
  1022: "白玉",
  1023: "彩玉",
  1026: "扳手",
  1033: "贝壳",
  1035: "金盐靛",
  10002: "蓝玉",
  10003: "红玉",
  10101: "四圣碎片",
  2001: "木制宝箱",
  2002: "青铜宝箱",
  2003: "黄金宝箱",
  2004: "铂金宝箱",
  2005: "钻石宝箱",
  2101: "助威币",
  3001: "金币袋子",
  3002: "金砖袋子",
  3005: "紫色随机碎片",
  3006: "橙色随机碎片",
  3007: "红色随机碎片",
  3008: "精铁袋子",
  3009: "进阶袋子",
  3010: "梦魇袋子",
  3011: "白玉袋子",
  3012: "扳手袋子",
  3020: "聚宝盆",
  3021: "豪华聚宝盆",
  3201: "红色万能碎片",
  3302: "橙色万能碎片",
  35002: "刷新券",
  35009: "零件",
};

const formatRewardNumber = (num) => {
  const n = Number(num);
  if (!Number.isFinite(n)) return "--";
  if (n >= 1e12) return (n / 1e12).toFixed(2) + "兆";
  if (n >= 1e8) return (n / 1e8).toFixed(2) + "亿";
  if (n >= 1e4) return (n / 1e4).toFixed(2) + "万";
  return n.toString();
};

const formatPowerYi = (value) => {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return "--";
  return `${(numeric / 100000000).toFixed(2)}亿`;
};

const formatServerId = (value) => {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return value || "--";
  if (numeric > 27) return numeric - 27;
  return numeric;
};

const carGradeLabel = (color) => {
  const mapping = {
    1: "普通",
    2: "稀有",
    3: "史诗",
    4: "传说",
    5: "神话",
  };
  return mapping[Number(color)] || "未知";
};

const carStatusLabel = (car) => {
  const sendAt = Number(car?.sendAt || 0);
  const claimAt = Number(car?.claimAt || 0);
  const rewards = Array.isArray(car?.rewards) ? car.rewards : [];
  if (sendAt > 0 && (claimAt > 0 || rewards.length > 0)) return "可收车";
  if (sendAt > 0) return "已发车";
  return "未发车";
};

const carRewardSummary = (car) => {
  const rewards = Array.isArray(car?.rewards) ? car.rewards : [];
  if (rewards.length === 0) return "暂无";
  return `共 ${rewards.length} 项`;
};

const formatRewardLabel = (reward) => {
  if (!reward) return "未知奖励";
  if (typeof reward !== "object") return String(reward);
  const rewardType = Number(reward.type ?? reward.rewardType ?? reward.t ?? 0);
  const itemId = Number(
    reward.itemId ?? reward.id ?? reward.itemID ?? reward.item ?? 0
  );
  const value = Number(
    reward.value ?? reward.count ?? reward.num ?? reward.quantity ?? reward.amount ?? 0
  );
  if (rewardType === 1) {
    return `金币: ${formatRewardNumber(value)}`;
  }
  if (rewardType === 2) {
    return `金砖: ${value.toLocaleString()}`;
  }
  if (rewardType === 3) {
    const itemName = itemMapping[itemId] || `未知物品(${itemId})`;
    return `${itemName}: ${value}`;
  }
  if (itemId) {
    const itemName = itemMapping[itemId] || `未知物品(${itemId})`;
    return `${itemName}: ${value}`;
  }
  return `类型${rewardType}: ${value}`;
};

const getCarId = (car) => {
  const raw = car?.id ?? car?.carId ?? car?.key;
  const numeric = Number(raw);
  if (Number.isFinite(numeric)) return numeric;
  return raw ?? null;
};

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

const loadClubInfo = async (refresh = true) => {
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) return;
  clubLoading.value = true;
  try {
    const tokenJsonValue = buildTokenJson();
    const response = await fetch(
      `/api/v1/xyzw/ws/legion/info?token=${encodeURIComponent(
        tokenJsonValue
      )}&refresh=${refresh}`,
      { headers: { ...authHeaders() } }
    );
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || "获取俱乐部信息失败");
    }
    clubInfo.value =
      payload.data?.legionInfo || payload.data?.info || payload.data || null;
  } catch (error) {
    setNote(`获取俱乐部信息失败: ${error.message}`, true);
  } finally {
    clubLoading.value = false;
  }
};

const handleClubSignIn = async () => {
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) {
    setNote("请先加载 Token。", true);
    return;
  }
  clubSignLoading.value = true;
  try {
    const tokenJsonValue = buildTokenJson();
    const resp = await fetch("/api/v1/xyzw/ws/legion/signin", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ token: tokenJsonValue }),
    });
    const payload = await resp.json();
    if (!resp.ok || !payload.success) {
      throw new Error(payload.message || "俱乐部签到失败");
    }
    setNote("俱乐部签到已发送。", false);
    await loadClubInfo(true);
  } catch (error) {
    setNote(`俱乐部签到失败: ${error.message}`, true);
  } finally {
    clubSignLoading.value = false;
  }
};

const normalizeCarList = (raw) => {
  if (!raw) return [];
  const body = raw.body || raw;
  const roleCar = body.roleCar || body.rolecar || {};
  const carMap = roleCar.carDataMap || roleCar.cardatamap;
  if (carMap && typeof carMap === "object") {
    return Object.entries(carMap).map(([id, info], idx) => ({
      key: idx,
      id,
      ...(info || {}),
    }));
  }
  let container =
    body.cars || body.list || body.data || body.carList || body.vehicles || [];
  if (!Array.isArray(container) && container && typeof container === "object") {
    container = Object.values(container);
  }
  if (Array.isArray(body) && container.length === 0) {
    container = body;
  }
  return Array.isArray(container)
    ? container.map((item, idx) => ({ key: idx, ...item }))
    : [];
};

const loadClubCars = async (refresh = true) => {
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) return;
  clubCarsLoading.value = true;
  try {
    const tokenJsonValue = buildTokenJson();
    const response = await fetch(
      `/api/v1/xyzw/ws/clubcar/list?token=${encodeURIComponent(
        tokenJsonValue
      )}&refresh=${refresh}`,
      { headers: { ...authHeaders() } }
    );
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || "获取俱乐部赛车失败");
    }
    const raw = payload.data?.cars || payload.data?.car || payload.data;
    clubCars.value = normalizeCarList(raw);
  } catch (error) {
    setNote(`获取俱乐部赛车失败: ${error.message}`, true);
  } finally {
    clubCarsLoading.value = false;
  }
};

const handleRefreshCar = async (car, refreshAfter = true, toggleLoading = true) => {
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) return;
  const carId = getCarId(car);
  if (carId == null) {
    setNote("刷新车辆失败: 无法识别车位 ID。", true);
    return;
  }
  if (toggleLoading) {
    clubCarsLoading.value = true;
  }
  try {
    const tokenJsonValue = buildTokenJson();
    const resp = await fetch("/api/v1/xyzw/ws/clubcar/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ token: tokenJsonValue, carId }),
    });
    const payload = await resp.json();
    if (!resp.ok || !payload.success) {
      throw new Error(payload.message || "刷新车辆失败");
    }
    if (refreshAfter) {
      await loadClubCars(true);
    }
  } catch (error) {
    setNote(`刷新车辆失败: ${error.message}`, true);
  } finally {
    if (toggleLoading) {
      clubCarsLoading.value = false;
    }
  }
};

const handleSendCar = async (car, refreshAfter = true, toggleLoading = true) => {
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) return;
  const carId = getCarId(car);
  if (carId == null) {
    setNote("发车失败: 无法识别车位 ID。", true);
    return;
  }
  if (toggleLoading) {
    clubCarsLoading.value = true;
  }
  try {
    const tokenJsonValue = buildTokenJson();
    const resp = await fetch("/api/v1/xyzw/ws/clubcar/send", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ token: tokenJsonValue, carId }),
    });
    const payload = await resp.json();
    if (!resp.ok || !payload.success) {
      throw new Error(payload.message || "发车失败");
    }
    if (refreshAfter) {
      await loadClubCars(true);
    }
  } catch (error) {
    setNote(`发车失败: ${error.message}`, true);
  } finally {
    if (toggleLoading) {
      clubCarsLoading.value = false;
    }
  }
};

const handleClaimCar = async (car, refreshAfter = true, toggleLoading = true) => {
  if (!(await ensureAuth())) return;
  if (!tokenRecord.value?.token) return;
  const carId = getCarId(car);
  if (carId == null) {
    setNote("收车失败: 无法识别车位 ID。", true);
    return;
  }
  if (toggleLoading) {
    clubCarsLoading.value = true;
  }
  try {
    const tokenJsonValue = buildTokenJson();
    const resp = await fetch("/api/v1/xyzw/ws/clubcar/claim", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ token: tokenJsonValue, carId }),
    });
    const payload = await resp.json();
    if (!resp.ok || !payload.success) {
      throw new Error(payload.message || "收车失败");
    }
    if (refreshAfter) {
      await loadClubCars(true);
    }
  } catch (error) {
    setNote(`收车失败: ${error.message}`, true);
  } finally {
    if (toggleLoading) {
      clubCarsLoading.value = false;
    }
  }
};

const handleSendAllCars = async () => {
  if (clubCars.value.length === 0) return;
  clubCarsLoading.value = true;
  try {
    for (const car of clubCars.value) {
      if (carStatusLabel(car) === "未发车") {
        await handleSendCar(car, false, false);
      }
    }
    await loadClubCars(true);
  } finally {
    clubCarsLoading.value = false;
  }
};

const handleClaimAllCars = async () => {
  if (clubCars.value.length === 0) return;
  clubCarsLoading.value = true;
  try {
    for (const car of clubCars.value) {
      if (carStatusLabel(car) === "可收车") {
        await handleClaimCar(car, false, false);
      }
    }
    await loadClubCars(true);
  } finally {
    clubCarsLoading.value = false;
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

watch(activeSection, (value) => {
  if (value === "club") {
    if (!clubInfo.value) {
      loadClubInfo(true);
    }
    if (clubCars.value.length === 0) {
      loadClubCars(true);
    }
    return;
  }
  if (value === "race" && clubCars.value.length === 0) {
    loadClubCars(true);
  }
});

watch(
  tokenRecord,
  () => {
    clubInfo.value = null;
    clubCars.value = [];
  },
  { deep: false }
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

.section-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 6px 0 12px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.35);
  margin-bottom: 8px;
}

.tab-button {
  background: transparent;
  border: none;
  padding: 6px 10px;
  border-radius: 999px;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tab-button:hover {
  color: #0f172a;
  background: rgba(148, 163, 184, 0.2);
}

.tab-button.active {
  color: #0f172a;
  font-weight: 600;
  background: rgba(15, 23, 42, 0.08);
  box-shadow: inset 0 0 0 1px rgba(148, 163, 184, 0.4);
}

.section-placeholder {
  padding: 28px 20px;
  text-align: center;
  color: #64748b;
  background: rgba(255, 255, 255, 0.68);
  border: 1px dashed rgba(148, 163, 184, 0.5);
  border-radius: 16px;
  margin-top: 8px;
}

.placeholder-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.placeholder-desc {
  margin-top: 6px;
  font-size: 13px;
}

.club-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: 12px;
  margin-top: 8px;
}

.club-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.club-signin {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.18);
  color: #475569;
  border: 1px solid rgba(148, 163, 184, 0.35);
}

.club-signin.active {
  background: rgba(59, 130, 246, 0.16);
  color: #1d4ed8;
  border-color: rgba(59, 130, 246, 0.35);
}

.club-badge {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.2);
  color: #475569;
}

.club-badge.active {
  background: rgba(16, 185, 129, 0.16);
  color: #0f766e;
}

.club-info-main {
  min-height: 160px;
}

.club-empty {
  padding: 20px 0;
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
}

.club-info-header {
  display: grid;
  gap: 4px;
  margin-bottom: 12px;
}

.club-name {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.club-meta {
  font-size: 12px;
  color: #64748b;
}

.club-info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
}

.club-info-item {
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 12px;
  padding: 10px 12px;
}

.club-info-item .label {
  font-size: 12px;
  color: #64748b;
}

.club-info-item .value {
  margin-top: 4px;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.club-car-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.club-car-item {
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(148, 163, 184, 0.3);
  border-radius: 12px;
  padding: 12px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 16px;
  align-items: start;
}

.club-car-body {
  display: grid;
  gap: 6px;
}

.club-car-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
  min-width: 120px;
}

.club-car-actions .card-btn {
  width: 100%;
  min-width: 0;
}

.club-car-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.club-car-name {
  font-weight: 600;
  color: #0f172a;
  font-size: 13px;
}

.club-car-grade {
  font-size: 12px;
  color: #0f766e;
  background: rgba(16, 185, 129, 0.12);
  padding: 2px 8px;
  border-radius: 999px;
}

.club-car-meta {
  font-size: 12px;
  color: #475569;
}

.club-car-rewards {
  margin-top: 4px;
}

.club-car-reward-title {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.club-car-reward-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.club-car-reward-chip {
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(226, 232, 240, 0.85);
  border: 1px solid rgba(148, 163, 184, 0.35);
  font-size: 11px;
  color: #334155;
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

  .club-grid {
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







