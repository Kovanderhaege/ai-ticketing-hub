import { createRouter, createWebHistory } from "vue-router"
import TicketsView from  "../views/TicketsView.vue"
import TicketView from "../views/TicketView.vue"
import CreateTicketView from "../views/CreateTicketView.vue"
import NotificationsView from "../views/NotificationsView.vue"

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/tickets" },
    { path: "/tickets", component: TicketsView },
    { path: "/tickets/new", component: CreateTicketView },
    { path: "/notifications", component: NotificationsView },
    { path: "/tickets/:id", component: TicketView }
  ]
})

export default router