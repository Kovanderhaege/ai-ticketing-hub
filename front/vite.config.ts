import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const proxy = {
    "/api/auth": {
      target: env.TICKET_API_TARGET || "http://localhost:8083",
      changeOrigin: true,
      rewrite: (path: string) => path.replace(/^\/api\/auth/, "/auth"),
    },
    "/api/tickets": {
      target: env.TICKET_API_TARGET || "http://localhost:8083",
      changeOrigin: true,
      rewrite: (path: string) => path.replace(/^\/api\/tickets/, "/ticket"),
    },
    "/api/notifications": {
      target: env.NOTIFICATION_API_TARGET || "http://localhost:8082",
      changeOrigin: true,
      rewrite: (path: string) =>
        path.replace(/^\/api\/notifications/, "/notification"),
    },
  };
  return { plugins: [vue()], server: { proxy }, preview: { proxy } };
});
