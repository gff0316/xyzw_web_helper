package com.xyzw.webhelper.xyzw.ws;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class XyzwCommandNameRegistry {
    private static final Map<String, String> COMMAND_NAMES;

    static {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("_sys/ack", "心跳确认");
        map.put("_sys/fatal", "系统致命错误");

        map.put("syncresp", "同步回包");
        map.put("syncrewardresp", "奖励同步回包");

        map.put("role_getroleinfo", "获取角色信息");
        map.put("role_getroleinforesp", "获取角色信息回包");

        map.put("system_mysharecallback", "挂机加钟分享回调");
        map.put("system_claimhangupreward", "领取挂机奖励");
        map.put("system_claimhanguprewardresp", "领取挂机奖励回包");
        map.put("system_buygold", "点金购买金币");
        map.put("system_buygoldresp", "点金购买金币回包");

        map.put("friend_batch", "一键赠送友情点");
        map.put("friend_batchresp", "一键赠送友情点回包");
        map.put("hero_recruit", "招募武将");
        map.put("hero_recruitresp", "招募武将回包");

        map.put("bottlehelper_stop", "停止罐子");
        map.put("bottlehelper_stopresp", "停止罐子回包");
        map.put("bottlehelper_start", "启动罐子");
        map.put("bottlehelper_startresp", "启动罐子回包");
        map.put("bottlehelper_claim", "领取罐子奖励");
        map.put("bottlehelper_claimresp", "领取罐子奖励回包");

        map.put("item_openbox", "开启宝箱");
        map.put("item_openboxresp", "开启宝箱回包");

        map.put("mail_claimallattachment", "领取邮件附件");
        map.put("mail_claimallattachmentresp", "领取邮件附件回包");

        map.put("task_claimdailypoint", "领取日常任务积分");
        map.put("task_claimdailypointresp", "领取日常任务积分回包");
        map.put("task_claimdailyreward", "领取日常活跃奖励");
        map.put("task_claimdailyrewardresp", "领取日常活跃奖励回包");
        map.put("task_claimweekreward", "领取周活跃奖励");
        map.put("task_claimweekrewardresp", "领取周活跃奖励回包");

        map.put("store_purchase", "黑市购买");
        map.put("store_buyresp", "黑市购买回包");

        map.put("presetteam_saveteam", "保存阵容");
        map.put("arena_startarea", "进入竞技场");
        map.put("arena_getareatarget", "获取竞技场目标");
        map.put("arena_getareatargetresp", "获取竞技场目标回包");
        map.put("fight_startareaarena", "发起竞技场战斗");
        map.put("fight_startlegionboss", "发起军团Boss");

        COMMAND_NAMES = Collections.unmodifiableMap(map);
    }

    private XyzwCommandNameRegistry() {
    }

    static String getName(String cmd) {
        if (cmd == null) {
            return "未知命令";
        }
        String key = cmd.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return "未知命令";
        }
        String name = COMMAND_NAMES.get(key);
        return name == null ? "未登记命令" : name;
    }
}
