package com.xyzw.webhelper.xyzw.dto;

public class XyzwDailyTaskRequest {
    private String token;
    private String taskName;
    private DailySettings settings;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public DailySettings getSettings() {
        return settings;
    }

    public void setSettings(DailySettings settings) {
        this.settings = settings;
    }

    public static class DailySettings {
        private Integer arenaFormation;
        private Integer bossFormation;
        private Integer bossTimes;
        private Boolean claimBottle;
        private Boolean payRecruit;
        private Boolean openBox;
        private Boolean arenaEnable;
        private Boolean claimHangUp;
        private Boolean claimEmail;
        private Boolean blackMarketPurchase;

        public Integer getArenaFormation() {
            return arenaFormation;
        }

        public void setArenaFormation(Integer arenaFormation) {
            this.arenaFormation = arenaFormation;
        }

        public Integer getBossFormation() {
            return bossFormation;
        }

        public void setBossFormation(Integer bossFormation) {
            this.bossFormation = bossFormation;
        }

        public Integer getBossTimes() {
            return bossTimes;
        }

        public void setBossTimes(Integer bossTimes) {
            this.bossTimes = bossTimes;
        }

        public Boolean getClaimBottle() {
            return claimBottle;
        }

        public void setClaimBottle(Boolean claimBottle) {
            this.claimBottle = claimBottle;
        }

        public Boolean getPayRecruit() {
            return payRecruit;
        }

        public void setPayRecruit(Boolean payRecruit) {
            this.payRecruit = payRecruit;
        }

        public Boolean getOpenBox() {
            return openBox;
        }

        public void setOpenBox(Boolean openBox) {
            this.openBox = openBox;
        }

        public Boolean getArenaEnable() {
            return arenaEnable;
        }

        public void setArenaEnable(Boolean arenaEnable) {
            this.arenaEnable = arenaEnable;
        }

        public Boolean getClaimHangUp() {
            return claimHangUp;
        }

        public void setClaimHangUp(Boolean claimHangUp) {
            this.claimHangUp = claimHangUp;
        }

        public Boolean getClaimEmail() {
            return claimEmail;
        }

        public void setClaimEmail(Boolean claimEmail) {
            this.claimEmail = claimEmail;
        }

        public Boolean getBlackMarketPurchase() {
            return blackMarketPurchase;
        }

        public void setBlackMarketPurchase(Boolean blackMarketPurchase) {
            this.blackMarketPurchase = blackMarketPurchase;
        }
    }
}
