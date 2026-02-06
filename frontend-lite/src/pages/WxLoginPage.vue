<template>
  <div class="dashboard-page">
    <button v-if="authUser" class="logout-fab" type="button" @click="handleLogout">
      退出登录
    </button>
    <div class="content">
      <div class="header">
        <div>
          <h1>微信扫码登录</h1>
          <p v-if="authUser">欢迎，{{ authUser.username }}，扫码后将导入 Bin。</p>
          <p v-else>请先登录。</p>
        </div>
        <div class="header-actions">
          <button class="ghost" type="button" @click="router.push('/upload-bin')">
            返回上传
          </button>
          <button class="ghost" type="button" @click="router.push('/profile')">
            个人信息管理
          </button>
        </div>
      </div>

      <div class="flow-card">
        <h3>微信扫码登录流程</h3>
        <ol>
          <li>点击下方按钮获取微信登录二维码</li>
          <li>使用微信扫码并确认登录</li>
          <li>系统将尝试获取所有角色 Bin 并上传</li>
        </ol>
      </div>

      <div class="qrcode-container">
        <div
          v-if="!qrcodeUrl"
          class="qr-placeholder"
          @click="generateQRCode"
        >
          <n-icon size="48" color="rgba(15, 23, 42, 0.5)">
            <Scan />
          </n-icon>
          <p>点击获取微信登录二维码</p>
        </div>
        <img
          v-else
          :src="qrcodeUrl"
          alt="微信登录二维码"
          class="qr-image"
        />

        <div class="qr-status" :class="statusType">{{ statusMessage }}</div>
      </div>

      <div class="account-name-input">
        <n-input
          v-model:value="accountName"
          placeholder="请输入账号名称（例如：微信账号）"
          :disabled="isProcessing"
        >
          <template #prefix>
            <n-icon>
              <PersonCircleOutline />
            </n-icon>
          </template>
        </n-input>
      </div>

      <div class="actions">
        <n-button type="primary" :loading="isProcessing" @click="generateQRCode">
          <template #icon>
            <n-icon>
              <Refresh />
            </n-icon>
          </template>
          {{ qrcodeUrl ? "刷新二维码" : "获取二维码" }}
        </n-button>
        <n-button :disabled="isProcessing" @click="router.push('/upload-bin')">
          返回上传
        </n-button>
      </div>

      <p v-if="statusMessage" class="status-message" :class="{ error: statusType === 'error' }">
        {{ statusMessage }}
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { Scan, PersonCircleOutline, Refresh } from "@vicons/ionicons5";
import { NIcon, NInput, NButton, useMessage } from "naive-ui";

const router = useRouter();
const message = useMessage();

const authToken = ref(localStorage.getItem("authToken") || "");
const authUser = ref(
  localStorage.getItem("authUser")
    ? JSON.parse(localStorage.getItem("authUser"))
    : null,
);

const qrcodeUrl = ref(null);
const qrcodeUUID = ref(null);
const isProcessing = ref(false);
const statusMessage = ref("点击获取微信登录二维码");
const statusType = ref("info");
const accountName = ref("");
const isScanning = ref(false);
const scanInterval = ref(null);
const timeout = 120000;
const startTime = ref(null);
let bundlePromise = null;

const WECHAT_QR_URL = "/api/weixin/connect/app/qrconnect";
const WECHAT_SCAN_URL = "/api/weixin/connect/l/qrconnect";
const HORTOR_LOGIN_URL = "/api/hortor/comb-login-server/api/v1/login";

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

const updateStatus = (text, type = "info") => {
  statusMessage.value = text;
  statusType.value = type;
};

const resetQRCode = () => {
  stopScanMonitoring();
  qrcodeUUID.value = null;
  qrcodeUrl.value = null;
  updateStatus("点击获取微信登录二维码", "info");
};

const generateQRCode = async () => {
  try {
    isProcessing.value = true;
    updateStatus("正在获取二维码...", "info");
    resetQRCode();
    const success = await tryGetWeixinQR();
    if (!success) throw new Error("二维码获取失败");
  } catch (error) {
    updateStatus(`二维码获取失败：${error.message}`, "error");
  } finally {
    isProcessing.value = false;
  }
};

const tryGetWeixinQR = async () => {
  try {
    const qrPageUrl =
      `${WECHAT_QR_URL}` +
      "?appid=wxfb0d5667e5cb1c44" +
      "&bundleid=com.hortor.games.xyzw" +
      "&scope=snsapi_base,snsapi_userinfo,snsapi_friend,snsapi_message" +
      "&state=weixin";

    const response = await new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open("GET", qrPageUrl, true);
      xhr.timeout = 15000;
      xhr.setRequestHeader("Accept", "text/html");
      xhr.onload = () => resolve(xhr);
      xhr.onerror = () => reject(new Error("网络错误"));
      xhr.ontimeout = () => reject(new Error("请求超时"));
      xhr.send();
    });

    if (response.status !== 200) {
      throw new Error("HTTP 状态码：" + response.status);
    }

    const html = response.responseText;
    const doc = new DOMParser().parseFromString(html, "text/html");
    let qrUrl = doc.querySelector("img.auth_qrcode")?.src;
    if (!qrUrl) {
      const m = html.match(/https:\/\/[^"']*qrcode[^"']*/i);
      if (m) qrUrl = m[0];
    }
    if (!qrUrl) throw new Error("未找到二维码图片地址");

    qrcodeUUID.value = qrUrl.split("/").pop().split("?")[0];
    qrcodeUrl.value = qrUrl;
    updateStatus("请使用微信扫码登录", "success");
    startScanMonitoring();
    return true;
  } catch (err) {
    updateStatus("二维码获取失败：" + err.message, "error");
    return false;
  }
};

const startScanMonitoring = () => {
  if (isScanning.value) return;
  isScanning.value = true;
  startTime.value = Date.now();
  scanInterval.value = setInterval(() => {
    checkScanStatus();
  }, 5000);
};

const stopScanMonitoring = () => {
  isScanning.value = false;
  if (scanInterval.value) {
    clearInterval(scanInterval.value);
    scanInterval.value = null;
  }
};

const checkScanStatus = async () => {
  try {
    if (!qrcodeUUID.value) return;
    const elapsed = Date.now() - startTime.value;
    if (elapsed > timeout) {
      updateStatus("二维码已超时，请重新获取", "error");
      stopScanMonitoring();
      resetQRCode();
      return;
    }

    const url =
      `${WECHAT_SCAN_URL}?uuid=${qrcodeUUID.value}` +
      `&f=url&_=${Date.now()}`;

    const res = await new Promise((resolve) => {
      const xhr = new XMLHttpRequest();
      xhr.open("GET", url, true);
      xhr.timeout = 5000;
      xhr.setRequestHeader("Accept", "*/*");
      xhr.onload = () => resolve(xhr);
      xhr.onerror = () => resolve({ status: 0 });
      xhr.ontimeout = () => resolve({ status: 0 });
      xhr.send();
    });

    if (res.status === 200) {
      const text = res.responseText;
      if (text.includes("window.wx_errcode=405")) {
        const codeMatch = text.match(
          /wx_redirecturl='[^']*code=([a-zA-Z0-9]+)/,
        );
        const nicknameMatch = text.match(
          /window\.wx_nickname\s*=\s*['"]([^'"]+)['"]/,
        );
        if (codeMatch) {
          const code = codeMatch[1];
          const nickname = nicknameMatch ? nicknameMatch[1] : "";
          stopScanMonitoring();
          updateStatus(
            `扫码成功，正在登录... 用户：${nickname || "未知用户"}`,
            "success",
          );
          await handleScanSuccess(code, nickname);
          return;
        }
      }

      if (text.includes("window.wx_errcode=408")) {
        updateStatus("二维码已过期，请重新生成", "error");
        stopScanMonitoring();
        resetQRCode();
        return;
      }
    }

    const remain = Math.ceil((timeout - elapsed) / 1000);
    if (remain % 30 === 0) {
      updateStatus(`请扫码，剩余 ${remain} 秒`, "info");
    }
  } catch (err) {
    updateStatus("扫码状态检查失败：" + err.message, "error");
  }
};

const handleScanSuccess = async (code, nickname = "") => {
  try {
    if (!(await ensureAuth())) return;
    isProcessing.value = true;
    const batchId = Date.now();
    const { bins, errors } = await getEncryptedData(code);
    if (!bins.length) {
      throw new Error(errors[0]?.message || "生成 Bin 失败");
    }
    if (errors.length) {
      updateStatus(`部分角色生成失败：${errors.length}/${bins.length + errors.length}`, "error");
    }
    for (let i = 0; i < bins.length; i += 1) {
      const item = bins[i];
      updateStatus(`正在上传第 ${i + 1}/${bins.length} 个 Bin...`, "info");
      await uploadBin(item.bin, nickname, item, i, batchId);
    }
    updateStatus("Bin 已保存，正在返回个人信息管理。", "success");
    message.success("Bin 已上传");
    setTimeout(() => router.push("/profile"), 600);
  } catch (err) {
    updateStatus("处理失败：" + err.message, "error");
  } finally {
    isProcessing.value = false;
  }
};

const getRoleId = (role) =>
  role?.roleId ??
  role?.role_id ??
  role?.roleID ??
  role?.roleid ??
  role?.id ??
  null;

const getRoleName = (role) =>
  role?.roleName ??
  role?.name ??
  role?.nickName ??
  role?.nickname ??
  null;

const getServerId = (role) =>
  role?.serverId ??
  role?.server_id ??
  role?.server ??
  role?.zoneId ??
  role?.regionId ??
  role?.areaId ??
  role?.area ??
  null;

const scoreRoleObj = (role) => {
  let score = 0;
  if (getRoleId(role)) score += 3;
  if (getRoleName(role)) score += 2;
  if (getServerId(role)) score += 1;
  return score;
};

const collectRoleArrays = (obj, depth = 0, out = []) => {
  if (!obj || typeof obj !== "object" || depth > 4) return out;
  for (const [key, value] of Object.entries(obj)) {
    if (Array.isArray(value) && value.length && value.every((v) => v && typeof v === "object")) {
      const score = value.reduce((sum, item) => sum + scoreRoleObj(item), 0);
      if (score > 0) {
        out.push({ key, list: value, score });
      }
    } else if (value && typeof value === "object") {
      collectRoleArrays(value, depth + 1, out);
    }
  }
  return out;
};

const collectRolesFromCombUser = (combUser) => {
  const arrays = collectRoleArrays(combUser);
  if (!arrays.length) return [];
  arrays.sort((a, b) => {
    if (b.score !== a.score) return b.score - a.score;
    return b.list.length - a.list.length;
  });
  const seen = new Set();
  const roles = [];
  for (const arr of arrays) {
    for (const role of arr.list) {
      const roleId = getRoleId(role);
      const serverId = getServerId(role);
      const roleName = getRoleName(role);
      const key = `${roleId ?? ""}|${serverId ?? ""}|${roleName ?? ""}`;
      if (key === "||") continue;
      if (seen.has(key)) continue;
      seen.add(key);
      roles.push(role);
    }
  }
  return roles;
};

const buildCombUserVariants = (combUser) => {
  const roles = collectRolesFromCombUser(combUser);
  if (!roles.length) {
    return [{ combUser, role: null }];
  }
  return roles.map((role) => {
    const clone = JSON.parse(JSON.stringify(combUser));
    const roleId = getRoleId(role);
    const roleName = getRoleName(role);
    const serverId = getServerId(role);

    if (clone.role && typeof clone.role === "object") {
      clone.role = { ...clone.role, ...role };
    }
    if (clone.roleInfo && typeof clone.roleInfo === "object") {
      clone.roleInfo = { ...clone.roleInfo, ...role };
    }
    if (roleId != null) {
      clone.roleId = roleId;
      clone.role_id = roleId;
    }
    if (roleName) {
      clone.roleName = roleName;
    }
    if (serverId != null) {
      clone.serverId = serverId;
      clone.server = serverId;
    }
    return { combUser: clone, role };
  });
};

const getEncryptedData = async (code) => {
  const payload = {
    gameId: "xyzwapp",
    code,
    gameTp: "app",
    sysInfo:
      '{"system":"Android","hortorSDKVersion":"4.0.6-cn","model":"22081212C","brand":"Redmi"}',
    channel: "android",
    appFrom: "com.tencent.mm",
    noLogin: "2",
    distinctId: "DID-a38175b7-14ce-4b36-aa89-3e092ea03ea6",
    state: "hortor",
    packageName: "com.hortor.games.xyzw",
    tp: "app-we",
    signPrint: "E6:F7:FE:A9:EC:8E:24:D0:4F:2A:32:50:28:78:E1:C5:5E:70:81:13",
  };

  const rawJson = JSON.stringify(payload);
  const encoded = encodePayload(rawJson);

  const loginUrl =
    `${HORTOR_LOGIN_URL}` +
    "?gameId=xyzwapp" +
    "&timestamp=" +
    Date.now() +
    "&version=android-4.2.1-cn-release" +
    "&cryptVersion=1.1.0" +
    "&gameTp=app&system=android" +
    "&deviceUniqueId=DID-0e782e88-2f3b-4f5b-9020-47f5e5a5a026" +
    "&packageName=com.hortorgames.xyzw";

  const res = await new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", loginUrl, true);
    xhr.timeout = 15000;
    xhr.setRequestHeader("Accept", "*/*");
    xhr.setRequestHeader("Content-Type", "text/plain; charset=utf-8");
    xhr.onload = () => resolve(xhr);
    xhr.onerror = () => reject(new Error("登录失败"));
    xhr.ontimeout = () => reject(new Error("登录超时"));
    xhr.send(encoded);
  });

  if (res.status !== 200) {
    throw new Error("HTTP 状态码：" + res.status);
  }

  const json = JSON.parse(res.responseText);
  if (json.meta?.errCode !== 0) {
    throw new Error("登录失败：" + json.meta?.errMsg);
  }

  const combUser = json.data?.combUser;
  if (!combUser) {
    throw new Error("登录响应结构异常");
  }

  const variants = buildCombUserVariants(combUser);
  if (variants.length > 1) {
    updateStatus(`检测到 ${variants.length} 个角色，开始生成 Bin...`, "info");
  }
  const bins = [];
  const errors = [];
  for (const variant of variants) {
    try {
      const bin = await generateBinFromCombUser(variant.combUser);
      bins.push({ bin, role: variant.role });
    } catch (err) {
      errors.push({ role: variant.role, message: err.message || String(err) });
    }
  }
  return { bins, errors };
};

const stripEntryModules = (bundleText) => {
  const pattern = "}, {}, [";
  const start = bundleText.lastIndexOf(pattern);
  if (start < 0) return bundleText;
  const end = bundleText.lastIndexOf("]);");
  if (end < start) return bundleText;
  return bundleText.slice(0, start) + "}, {}, []);" + "\n";
};

const loadScriptOnce = (url, mark) =>
  new Promise((resolve, reject) => {
    const attr = "data-xyzw-script";
    if (document.querySelector(`script[${attr}='${mark}']`)) {
      resolve();
      return;
    }
    const script = document.createElement("script");
    script.src = url;
    script.async = true;
    script.setAttribute(attr, mark);
    script.onload = () => resolve();
    script.onerror = () => reject(new Error(`脚本加载失败: ${url}`));
    document.head.appendChild(script);
  });

const ensureGameBundle = () => {
  if (window.__require) {
    return Promise.resolve();
  }
  if (bundlePromise) return bundlePromise;
  bundlePromise = (async () => {
    const base = import.meta.env.BASE_URL || "/";
    const defineUrl = `${base}xyzw/game-defines.js`;
    const cocosUrl = `${base}xyzw/cocos2d-js-min.js`;
    const bundleUrl = `${base}xyzw/index.js`;

    await loadScriptOnce(defineUrl, "game-defines");
    await loadScriptOnce(cocosUrl, "cocos2d");

    const resp = await fetch(bundleUrl, { cache: "no-store" });
    if (!resp.ok) {
      throw new Error(`index.js 加载失败（${resp.status}），请检查: ${bundleUrl}`);
    }
    const rawText = await resp.text();
    if (!rawText.includes("window.__require")) {
      throw new Error(`index.js 内容异常，未包含加密模块入口: ${bundleUrl}`);
    }
    const sanitized = stripEntryModules(rawText);
    const bootstrap =
      "window.cc = window.cc || {};" +
      "window.cc._RF = window.cc._RF || { push: function(){}, pop: function(){} };";

    const tryEval = () => {
      try {
        const fn = new Function("window", `${bootstrap}\n${sanitized}\nreturn window.__require;`);
        const req = fn(window);
        if (!window.__require && req) {
          window.__require = req;
        }
        return !!window.__require;
      } catch (err) {
        return false;
      }
    };

    if (tryEval()) return;

    const blob = new Blob([bootstrap + "\n" + sanitized], { type: "text/javascript" });
    const blobUrl = URL.createObjectURL(blob);
    await new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = blobUrl;
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error(`index.js 执行失败: ${bundleUrl}`));
      document.head.appendChild(script);
    });
    URL.revokeObjectURL(blobUrl);
    if (!window.__require) {
      throw new Error(`index.js 已执行但未暴露加密模块: ${bundleUrl}`);
    }
  })();
  return bundlePromise;
};

const generateBinFromCombUser = async (combUser) => {
  await ensureGameBundle();
  if (!window.__require) {
    throw new Error("游戏加密模块未加载");
  }
  let dm;
  const candidates = ["13", 13, "@o4e/core"];
  for (const id of candidates) {
    try {
      dm = window.__require(id);
      if (dm) break;
    } catch (err) {
      // try next
    }
  }
  if (!dm?.encMsg || !dm?.lz4XorEncode) {
    throw new Error("游戏加密模块未加载");
  }
  const payload = {
    platform: "hortor",
    platformExt: "mix",
    info: combUser,
    serverId: null,
    scene: 0,
    referrerInfo: "",
  };
  const encryptedBuffer = dm.encMsg(payload, {
    decrypt: dm.lz4XorDecode,
    encrypt: dm.lz4XorEncode,
  });
  return new Uint8Array(encryptedBuffer);
};

const uploadBin = async (bin, nickname, item = {}, index = 0, batchId = Date.now()) => {
  const role = item.role || {};
  let baseName = accountName.value || nickname || "微信账号";
  const roleName = getRoleName(role);
  const roleId = getRoleId(role);
  const serverId = getServerId(role);
  if (roleName) {
    baseName += `-${roleName}`;
  } else if (roleId) {
    baseName += `-${roleId}`;
  }
  if (serverId) {
    baseName += `-S${serverId}`;
  }
  const suffix = typeof index === "number" ? `-${index + 1}` : "";
  const name = `${baseName}-${batchId}${suffix}`;
  const file = new File([bin], `${name}.bin`, {
    type: "application/octet-stream",
  });
  const formData = new FormData();
  formData.append("file", file);
  formData.append("name", name);

  const response = await fetch("/api/v1/xyzw/bins", {
    method: "POST",
    headers: { ...authHeaders() },
    body: formData,
  });
  const payload = await response.json();
  if (!response.ok || !payload.success) {
    throw new Error(payload.message || "上传 Bin 失败");
  }
};

const encodePayload = (text) => {
  const cipherTable =
    "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";

  const xorShift = 1;
  const shuffleTimes = 6;
  const step = 3;

  const mid = codeBase64(text, cipherTable, shuffleTimes, step, xorShift);
  const final = encodeBase64(mid);
  return final;
};

const codeBase64 = (text, cipherTable, shuffleTimes, step, xorShift) => {
  const base64Text = encodeBase64(text);
  if (cipherTable) {
    const shuffled = transCode(cipherTable, shuffleTimes);
    const key = getCodeKey(shuffled, step);
    return dealWithString(base64Text, key, xorShift);
  }
  return null;
};

const encodeBase64 = (text) => {
  if (!text) return null;
  return btoa(unescape(encodeURIComponent(text)));
};

const transCode = (str, times) => {
  if (times <= 0) return str;
  if (str.length % 2 !== 0) return null;
  const right = rightSide(str);
  const left = leftSide(str);
  return transCode(right, times - 1) + transCode(left, times - 1);
};

const rightSide = (str) => {
  if (str.length % 2 !== 0) return null;
  return str.substring(Math.floor(str.length / 2));
};

const leftSide = (str) => {
  if (str.length % 2 !== 0) return null;
  return str.substring(0, Math.floor(str.length / 2));
};

const getCodeKey = (str, step) => {
  const chars = str.split("");
  const result = [];
  const count = Math.floor(str.length / step);
  for (let i = 0; i < count; i++) {
    result.push(chars[i * step]);
  }
  return result.join("");
};

const dealWithString = (src, key, shift) => {
  if (!src || !key) return null;
  const v = src.split("");
  const w = key.split("");
  const out = new Array(v.length);
  let idx = w.length >> shift;
  for (let i = 0; i < v.length; i++) {
    if (idx >= w.length) idx = 0;
    out[i] = String.fromCharCode(v[i].charCodeAt(0) ^ w[idx].charCodeAt(0));
    idx++;
  }
  return out.join("");
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

onUnmounted(() => {
  stopScanMonitoring();
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
  max-width: 900px;
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

.flow-card {
  margin-top: 20px;
  padding: 16px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(148, 163, 184, 0.45);
}

.flow-card h3 {
  margin: 0 0 8px;
  font-size: 16px;
}

.flow-card ol {
  margin: 0;
  padding-left: 18px;
  color: #475569;
  font-size: 13px;
}

.qrcode-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 18px 0;
}

.qr-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 200px;
  height: 200px;
  border: 2px dashed rgba(148, 163, 184, 0.6);
  border-radius: 12px;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.7);
}

.qr-placeholder p {
  margin: 8px 0 0;
  font-size: 12px;
  color: #64748b;
}

.qr-image {
  width: 200px;
  height: 200px;
  border: 2px solid rgba(148, 163, 184, 0.6);
  border-radius: 12px;
}

.qr-status {
  font-size: 12px;
  color: #475569;
  background: rgba(255, 255, 255, 0.7);
  padding: 4px 10px;
  border-radius: 999px;
}

.qr-status.success {
  color: #0f766e;
}

.qr-status.error {
  color: #b91c1c;
}

.account-name-input {
  margin-top: 8px;
}

.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 16px;
}

.status-message {
  margin: 8px 0 0;
  font-size: 12px;
  color: #0f172a;
}

.status-message.error {
  color: #b91c1c;
}

@media (max-width: 720px) {
  .header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
