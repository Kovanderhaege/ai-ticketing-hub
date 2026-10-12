import { createRouter, createWebHistory } from "vue-router";
import TicketsView from "../views/TicketsView.vue";
import TicketView from "../views/TicketView.vue";
import CreateTicketView from "../views/CreateTicketView.vue";
import NotificationsView from "../views/NotificationsView.vue";
import NotFoundView from "../views/NotFoundView.vue";
import { nextTick } from "vue";

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
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

router.afterEach(async (to, from) => {
  document.title = `${to.meta.title || "Espace support"} · Kova`;
  if (to.path !== from.path) {
    await nextTick();
    document.getElementById("main-content")?.focus({ preventScroll: true });
  }
});

export default router;
