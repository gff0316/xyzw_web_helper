import { createRouter, createWebHistory } from "vue-router";
import LoginPage from "../pages/LoginPage.vue";
import RegisterPage from "../pages/RegisterPage.vue";
import ProfilePage from "../pages/DashboardPage.vue";
import GamePage from "../pages/GamePage.vue";
import UploadBinPage from "../pages/UploadBinPage.vue";
import WxLoginPage from "../pages/WxLoginPage.vue";

const routes = [
  { path: "/", redirect: "/profile" },
  {
    path: "/login",
    name: "Login",
    component: LoginPage,
    meta: { requiresAuth: false },
  },
  {
    path: "/register",
    name: "Register",
    component: RegisterPage,
    meta: { requiresAuth: false },
  },
  {
    path: "/profile",
    name: "Profile",
    component: ProfilePage,
    meta: { requiresAuth: true },
  },
  {
    path: "/upload-bin",
    name: "UploadBin",
    component: UploadBinPage,
    meta: { requiresAuth: true },
  },
  {
    path: "/wx-login",
    name: "WxLogin",
    component: WxLoginPage,
    meta: { requiresAuth: true },
  },
  {
    path: "/game/:tokenId?",
    name: "Game",
    component: GamePage,
    meta: { requiresAuth: true },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem("authToken");
  if (to.meta.requiresAuth && !token) {
    next("/login");
    return;
  }
  if (to.path === "/login" && token) {
    next("/profile");
    return;
  }
  next();
});

export default router;
