<script setup lang="ts">
withDefaults(defineProps<{ name: string; size?: number }>(), { size: 20 });

const paths: Record<string, string> = {
  layers: "m12 3 9 5-9 5-9-5 9-5Zm-9 9 9 5 9-5M3 16l9 5 9-5",
  inbox: "M4 4h16v16H4V4Zm0 10h5l2 3h2l2-3h5M8 8h8",
  plus: "M12 5v14M5 12h14",
  bell: "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4",
  sparkles:
    "m12 3 2.4 6.6L21 12l-6.6 2.4L12 21l-2.4-6.6L3 12l6.6-2.4L12 3ZM20 2v4m-2-2h4",
  "arrow-right": "M4 12h16m-6-6 6 6-6 6",
  "arrow-left": "M20 12H4m6-6-6 6 6 6",
  "chevron-right": "m9 6 6 6-6 6",
  "chevron-left": "m15 6-6 6 6 6",
  grid: "M3 3h7v7H3V3Zm11 0h7v7h-7V3ZM3 14h7v7H3v-7Zm11 0h7v7h-7v-7Z",
  search: "M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z",
  refresh:
    "M20 7v5h-5M4 17v-5h5M6 5a8 8 0 0 1 13 3l1 4M4 12l1 4a8 8 0 0 0 13 3",
  clock: "M12 8v4l3 2M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z",
  check: "m5 12 4 4L19 6",
  "check-circle": "m8 12 3 3 5-6M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z",
  alert: "m12 3 10 18H2L12 3Zm0 6v5m0 3v.01",
  flag: "M5 21V3m0 1c5-4 9 4 14 0v10c-5 4-9-4-14 0",
  file: "M14 2H4v20h16V8l-6-6Zm0 0v6h6M8 12h8m-8 4h5",
  close: "m6 6 12 12M6 18 18 6",
  filter: "M4 7h16M7 12h10m-7 5h4",
  info: "M12 11v6m0-10v.01M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z",
};
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path :d="paths[name] || paths.file" />
  </svg>
</template>
