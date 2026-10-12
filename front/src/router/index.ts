import { createRouter, createWebHistory } from "vue-router";
import TicketsView from "../views/TicketsView.vue";
import TicketView from "../views/TicketView.vue";
import CreateTicketView from "../views/CreateTicketView.vue";
import NotificationsView from "../views/NotificationsView.vue";
import NotFoundView from "../views/NotFoundView.vue";
import { nextTick } from "vue";
import LoginView from "../views/LoginView.vue";
import { useAuth } from "../composables/useAuth";
import { onUnauthorized } from "../api/client";

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: "/login", component: LoginView, meta: { title: "Connexion", public: true } },
    { path: "/register", component: LoginView, meta: { title: "Créer un compte", public: true } },
    { path: "/", redirect: "/tickets" },
    {
      path: "/tickets",
      component: TicketsView,
      meta: { title: "Tous les tickets" },
    },
    {
      path: "/tickets/new",
      component: CreateTicketView,
      meta: { title: "Nouveau ticket" },
    },
    {
      path: "/notifications",
      component: NotificationsView,
      meta: { title: "Notifications" },
    },
    {
      path: "/tickets/:id",
      component: TicketView,
      meta: { title: "Détail du ticket" },
    },
    {
      path: "/:pathMatch(.*)*",
      component: NotFoundView,
      meta: { title: "Page introuvable" },
    },
  ],
});

router.beforeEach(async (to) => {
  const { user, restoreSession } = useAuth();
  await restoreSession();
  if (!user.value && !to.meta.public)
    return { path: "/login", query: { redirect: to.fullPath } };
  if (user.value && to.meta.public) return "/tickets";
});

onUnauthorized(() => {
  const current = router.currentRoute.value;
  if (!current.meta.public)
    void router.replace({ path: "/login", query: { redirect: current.fullPath, reason: "expired" } });
});

router.afterEach(async (to, from) => {
  document.title = `${to.meta.title || "Espace support"} · Kova`;
  if (to.path !== from.path) {
    await nextTick();
    document.getElementById("main-content")?.focus({ preventScroll: true });
  }
});

export default router;
