import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  fullyParallel: true,
  use: { baseURL: "http://localhost:4174", trace: "retain-on-failure" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "npm run dev -- --host localhost --port 4174 --strictPort",
    url: "http://localhost:4174",
    reuseExistingServer: !process.env.CI,
  },
});
