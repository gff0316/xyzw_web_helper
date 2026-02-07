<template>
  <div class="identity-card">
    <div class="card-header">
      <img
        class="icon"
        :src="iconUrl"
        alt="身份牌"
        @error="handleIconError"
      />
      <div class="info">
        <h3>身份牌</h3>
        <p>角色与资源概览</p>
      </div>
      <button v-if="showRefresh" class="refresh-btn" type="button" @click="emit('refresh')">
        刷新
      </button>
    </div>

    <div v-if="loading" class="loading">正在获取角色信息...</div>
    <div v-else-if="!roleData" class="loading">暂无角色信息</div>
    <div v-else class="role-section" :class="rankInfo.class">
      <div class="avatar">
        <img :src="roleAvatar" :alt="roleData.name || '角色'" @error="handleAvatarError" />
      </div>
      <div class="role-main">
        <div class="role-name">{{ roleData.name || "未知角色" }}</div>
        <div class="role-meta">
          <span>Lv.{{ roleData.level || 1 }}</span>
          <span>战力 {{ formatPower(roleData.power) }}</span>
        </div>
        <div v-if="showRankBadge" class="role-rank">
          <span class="rank-icon">{{ rankInfo.icon }}</span>
          <span class="rank-title">{{ rankInfo.title }}</span>
        </div>
      </div>
    </div>

    <div v-if="roleData" class="resources" :class="{ collapsed: !isExpanded }">
      <div v-for="item in resourceList" :key="item.label" class="resource-item">
        <span class="label">{{ item.label }}</span>
        <span class="value">{{ item.value }}</span>
      </div>
    </div>
    <div v-if="roleData && showExpand" class="resources-toggle">
      <button class="toggle-btn" type="button" @click="isExpanded = !isExpanded">
        {{ isExpanded ? "收起" : "展开全部" }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from "vue";

const props = defineProps({
  roleInfo: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  showRefresh: { type: Boolean, default: false },
});

const emit = defineEmits(["refresh"]);
const isExpanded = ref(false);

const iconUrl = ref(`${import.meta.env.BASE_URL || "/"}icons/Ob7pyorzmHiJcbab2c25af264d0758b527bc1b61cc3b.png`);

const roleData = computed(() => {
  const raw = props.roleInfo || null;
  const role =
    raw?.roleInfo?.role ||
    raw?.role ||
    raw?.data?.role ||
    raw?.data?.roleInfo?.role ||
    null;
  const items =
    role?.items ||
    role?.itemList ||
    role?.bag?.items ||
    role?.inventory ||
    role?.itemsMap ||
    raw?.roleInfo?.items ||
    raw?.items ||
    null;
  if (!role) return null;
  return {
    name: role.name,
    level: role.level,
    power: role.power ?? role.fighting ?? 0,
    gold: role.gold ?? 0,
    diamond: role.diamond ?? 0,
    fishing: role.fishing ?? role.fish ?? 0,
    activityCoin: role.activityCoin ?? role.activity_coin ?? null,
    headImg: role.headImg || role.head_img || "",
    serverName: role.serverName || role.server_name || "",
    items,
  };
});

const defaultAvatars = [
  "icons/1733492491706148.png",
  "icons/1733492491706152.png",
  "icons/1736425783912140.png",
  "icons/173746572831736.png",
  "icons/174023274867420.png",
].map((path) => `${import.meta.env.BASE_URL || "/"}${path}`);

const fallbackAvatar = ref(defaultAvatars[0]);

const roleAvatar = computed(() => {
  if (roleData.value?.headImg) return roleData.value.headImg;
  return fallbackAvatar.value;
});

const powerRanks = [
  { min: 0, max: 1_000_000, title: "初出茅庐", icon: "🐣", class: "rank-beginner" },
  { min: 1_000_000, max: 10_000_000, title: "小有名气", icon: "✨", class: "rank-known" },
  { min: 10_000_000, max: 100_000_000, title: "出入江湖", icon: "⚔️", class: "rank-veteran" },
  { min: 100_000_000, max: 500_000_000, title: "纵横四方", icon: "🛡️", class: "rank-master" },
  { min: 500_000_000, max: 2_000_000_000, title: "盖世豪杰", icon: "🏆", class: "rank-hero" },
  { min: 2_000_000_000, max: 4_000_000_000, title: "无双霸主", icon: "👑", class: "rank-overlord" },
  { min: 4_000_000_000, max: Number.MAX_SAFE_INTEGER, title: "传奇至尊", icon: "🔥", class: "rank-legend" },
];

const rankInfo = computed(() => {
  const power = roleData.value?.power || 0;
  return powerRanks.find((rank) => power >= rank.min && power < rank.max) || powerRanks[0];
});

const showRankBadge = computed(() => rankInfo.value?.title !== "传奇至尊");

const items = computed(() => roleData.value?.items);

const getItemCount = (bag, id) => {
  if (!bag) return null;
  if (Array.isArray(bag)) {
    const found = bag.find((it) => Number(it?.id ?? it?.itemId) === id);
    if (!found) return 0;
    return Number(found?.num ?? found?.count ?? found?.quantity ?? 0);
  }
  const direct = bag[String(id)] ?? bag[id];
  if (direct == null) {
    const match = Object.values(bag).find(
      (v) => Number(v?.itemId ?? v?.id) === id,
    );
    if (!match) return 0;
    return Number(match?.num ?? match?.count ?? match?.quantity ?? 0);
  }
  if (typeof direct === "number") return Number(direct);
  if (typeof direct === "object") {
    return Number(direct?.num ?? direct?.count ?? direct?.quantity ?? 0);
  }
  return Number(direct) || 0;
};

const display = (n) => (n == null ? "—" : formatNumber(n));
const rawValue = (n) => (n == null ? null : Number(n));

const resourceList = computed(() => {
  if (!roleData.value) return [];

  const list = [];
  const push = (label, value, raw) => {
    if (raw == null) return;
    list.push({ label, value, raw });
  };

  push("金币", formatNumber(roleData.value.gold), roleData.value.gold);
  push("金砖", formatNumber(roleData.value.diamond), roleData.value.diamond);
  if (roleData.value.activityCoin != null) {
    push(
      "活动币",
      formatNumber(roleData.value.activityCoin),
      roleData.value.activityCoin,
    );
  }

  const normalRod = getItemCount(items.value, 1011);
  const goldRod = getItemCount(items.value, 1012);
  const pearl = getItemCount(items.value, 1013);
  const reviveDan = getItemCount(items.value, 1017);
  const recruit = getItemCount(items.value, 1001);
  const iron = getItemCount(items.value, 1006);
  const jade = getItemCount(items.value, 1023);
  const advanceStone = getItemCount(items.value, 1003);
  const blueJade = getItemCount(items.value, 10002);
  const redJade = getItemCount(items.value, 10003);
  const fourSaintFragment = getItemCount(items.value, 10101);

  const goldBag = getItemCount(items.value, 3001);
  const diamondBag = getItemCount(items.value, 3002);
  const purpleFragment = getItemCount(items.value, 3005);
  const orangeFragment = getItemCount(items.value, 3006);
  const redFragment = getItemCount(items.value, 3007);
  const ironBag = getItemCount(items.value, 3008);
  const advanceBag = getItemCount(items.value, 3009);
  const nightmareBag = getItemCount(items.value, 3010);
  const whiteJadeBag = getItemCount(items.value, 3011);
  const wrenchBag = getItemCount(items.value, 3012);
  const redUniversalFragment = getItemCount(items.value, 3201);
  const orangeUniversalFragment = getItemCount(items.value, 3302);
  const indigo = getItemCount(items.value, 1019);
  const crystal = getItemCount(items.value, 1016);
  const skinCoin = getItemCount(items.value, 1020);
  const sweepCarpet = getItemCount(items.value, 1021);
  const whiteJade = getItemCount(items.value, 1022);
  const shell = getItemCount(items.value, 1033);
  const goldIndigo = getItemCount(items.value, 1035);
  const arenaTicket = getItemCount(items.value, 1007);
  const woodChest = getItemCount(items.value, 2001);
  const bronzeChest = getItemCount(items.value, 2002);
  const goldChest = getItemCount(items.value, 2003);
  const platinumChest = getItemCount(items.value, 2004);
  const diamondChest = getItemCount(items.value, 2005);
  const refreshCoupon = getItemCount(items.value, 35002);
  const parts = getItemCount(items.value, 35009);
  const woodTorch = getItemCount(items.value, 1008);
  const bronzeTorch = getItemCount(items.value, 1009);
  const godTorch = getItemCount(items.value, 1010);
  const legionCoin = getItemCount(items.value, 1014);
  const wrench = getItemCount(items.value, 1026);
  const cheerCoin = getItemCount(items.value, 2101);

  push("普通鱼竿", display(normalRod), rawValue(normalRod));
  push("金鱼竿", display(goldRod), rawValue(goldRod));
  push("珍珠", display(pearl), rawValue(pearl));
  push("复活丹", display(reviveDan), rawValue(reviveDan));
  push("招募令", display(recruit), rawValue(recruit));
  push("精铁", display(iron), rawValue(iron));
  push("彩玉", display(jade), rawValue(jade));
  push("进阶石", display(advanceStone), rawValue(advanceStone));
  push("蓝玉", display(blueJade), rawValue(blueJade));
  push("红玉", display(redJade), rawValue(redJade));
  push("四圣碎片", display(fourSaintFragment), rawValue(fourSaintFragment));

  push("金币袋子", display(goldBag), rawValue(goldBag));
  push("金砖袋子", display(diamondBag), rawValue(diamondBag));
  push("紫色随机碎片", display(purpleFragment), rawValue(purpleFragment));
  push("橙色随机碎片", display(orangeFragment), rawValue(orangeFragment));
  push("红色随机碎片", display(redFragment), rawValue(redFragment));
  push("精铁袋子", display(ironBag), rawValue(ironBag));
  push("进阶袋子", display(advanceBag), rawValue(advanceBag));
  push("梦魇袋子", display(nightmareBag), rawValue(nightmareBag));
  push("白玉袋子", display(whiteJadeBag), rawValue(whiteJadeBag));
  push("扳手袋子", display(wrenchBag), rawValue(wrenchBag));
  push("红色万能碎片", display(redUniversalFragment), rawValue(redUniversalFragment));
  push("橙色万能碎片", display(orangeUniversalFragment), rawValue(orangeUniversalFragment));
  push("盐罐", display(indigo), rawValue(indigo));
  push("晶石", display(crystal), rawValue(crystal));
  push("皮肤币", display(skinCoin), rawValue(skinCoin));
  push("扫荡令", display(sweepCarpet), rawValue(sweepCarpet));
  push("白玉", display(whiteJade), rawValue(whiteJade));
  push("贝壳", display(shell), rawValue(shell));
  push("金盐罐", display(goldIndigo), rawValue(goldIndigo));
  push("竞技场门票", display(arenaTicket), rawValue(arenaTicket));
  push("木制宝箱", display(woodChest), rawValue(woodChest));
  push("青铜宝箱", display(bronzeChest), rawValue(bronzeChest));
  push("黄金宝箱", display(goldChest), rawValue(goldChest));
  push("铂金宝箱", display(platinumChest), rawValue(platinumChest));
  push("钻石宝箱", display(diamondChest), rawValue(diamondChest));
  push("刷新券", display(refreshCoupon), rawValue(refreshCoupon));
  push("零件", display(parts), rawValue(parts));
  push("木柴火把", display(woodTorch), rawValue(woodTorch));
  push("青铜火把", display(bronzeTorch), rawValue(bronzeTorch));
  push("神火把", display(godTorch), rawValue(godTorch));
  push("军团币", display(legionCoin), rawValue(legionCoin));
  push("扳手", display(wrench), rawValue(wrench));
  push("助威币", display(cheerCoin), rawValue(cheerCoin));

  const nonZero = list.filter((res) => res.raw > 0);
  const zero = list.filter((res) => res.raw === 0);
  return [...nonZero, ...zero];
});

const showExpand = computed(() => resourceList.value.length > 12);

const formatPower = (value) => formatNumber(value);

const formatNumber = (value) => {
  if (value == null || Number.isNaN(Number(value))) return "0";
  const num = Number(value);
  if (num >= 1e8) return `${(num / 1e8).toFixed(1)}亿`;
  if (num >= 1e4) return `${(num / 1e4).toFixed(1)}万`;
  return num.toLocaleString();
};

const handleAvatarError = () => {
  const currentIndex = defaultAvatars.indexOf(fallbackAvatar.value);
  fallbackAvatar.value =
    defaultAvatars[(currentIndex + 1) % defaultAvatars.length];
};

const handleIconError = () => {
  iconUrl.value = "";
};
</script>

<style scoped>
.identity-card {
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(248, 250, 252, 0.92));
  border: 1px solid rgba(148, 163, 184, 0.35);
  padding: 18px 20px;
  box-shadow:
    0 20px 50px rgba(15, 23, 42, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.7);
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-header .icon {
  width: 36px;
  height: 36px;
  object-fit: contain;
}

.card-header .info h3 {
  margin: 0;
  font-size: 16px;
}

.card-header .info p {
  margin: 2px 0 0;
  font-size: 12px;
  color: #64748b;
}

.refresh-btn {
  margin-left: auto;
  border: 1px solid rgba(148, 163, 184, 0.6);
  background: rgba(255, 255, 255, 0.5);
  border-radius: 999px;
  padding: 6px 14px;
  cursor: pointer;
  font-size: 12px;
  color: #334155;
  line-height: 1;
}

.loading {
  font-size: 13px;
  color: #64748b;
}

.role-section {
  display: flex;
  align-items: center;
  gap: 14px;
  border-radius: 12px;
  padding: 14px;
  background: linear-gradient(135deg, rgba(248, 250, 252, 0.95), rgba(241, 245, 249, 0.85));
  border: 1px solid rgba(148, 163, 184, 0.3);
}

.avatar {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  overflow: hidden;
  background: rgba(226, 232, 240, 0.8);
  display: grid;
  place-items: center;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.role-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.role-name {
  font-size: 16px;
  font-weight: 700;
}

.role-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #475569;
}

.role-rank {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #0f172a;
  background: rgba(226, 232, 240, 0.7);
  padding: 4px 8px;
  border-radius: 999px;
}

.resources {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 10px;
}

.resources.collapsed {
  max-height: 124px;
  overflow: hidden;
}

.resource-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 12px;
  border: 1px solid rgba(203, 213, 225, 0.6);
  box-shadow: 0 8px 16px rgba(15, 23, 42, 0.08);
  font-size: 12px;
  min-height: 56px;
}

.resource-item .label {
  color: #64748b;
  font-size: 11px;
  line-height: 1.2;
  white-space: normal;
  word-break: break-word;
}

.resource-item .value {
  font-weight: 700;
  color: #0f172a;
  font-size: 12px;
}

.rank-beginner {
  border-color: rgba(99, 102, 241, 0.2);
}

.rank-known {
  border-color: rgba(34, 197, 94, 0.25);
}

.rank-veteran {
  border-color: rgba(251, 191, 36, 0.35);
}

.rank-master {
  border-color: rgba(14, 165, 233, 0.35);
}

.rank-hero {
  border-color: rgba(236, 72, 153, 0.35);
}

.rank-overlord {
  border-color: rgba(248, 113, 113, 0.35);
}

.rank-legend {
  border-color: rgba(148, 163, 184, 0.5);
}

.resources-toggle {
  display: flex;
  justify-content: center;
}

.toggle-btn {
  border: 1px solid rgba(148, 163, 184, 0.6);
  background: rgba(255, 255, 255, 0.6);
  border-radius: 999px;
  padding: 6px 16px;
  font-size: 12px;
  cursor: pointer;
  color: #334155;
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.08);
}
</style>
