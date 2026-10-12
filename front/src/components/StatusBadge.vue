<script setup lang="ts">
import { computed } from "vue";
import { priorityLabels, statusLabels } from "../utils/tickets";

const props = withDefaults(
  defineProps<{ value: string | null; kind?: "status" | "priority" }>(),
  { kind: "status" },
);
const label = computed(() =>
  props.value
    ? (props.kind === "status" ? statusLabels : priorityLabels)[props.value] ||
      props.value
    : "À déterminer",
);
const tone = computed(
  () => props.value?.toLowerCase().replace(/[^a-z_]/g, "") || "unknown",
);
</script>

<template>
  <span class="badge" :class="[kind, `badge-${tone}`]"
    ><span class="badge-dot"></span>{{ label }}</span
  >
</template>
