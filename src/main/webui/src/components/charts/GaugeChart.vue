<script setup lang="ts">
import { computed } from 'vue';
import { gaugeFraction, arcPath, polar } from '../../presentation/charts';
import { formatPercent } from '../../presentation/format';

const props = withDefaults(
  defineProps<{
    title: string;
    caption?: string;
    value: number;
    target: number;
    testid: string;
  }>(),
  { caption: '' },
);

const CX = 110;
const CY = 110;
const R = 90;
const R_INNER = 60;
// A half-turn sweep from −90° (left) to +90° (right), measured clockwise from the top.
const START = -Math.PI / 2;
const END = Math.PI / 2;

const fraction = computed(() => gaugeFraction(props.value, props.target));
const track = computed(() => arcPath(CX, CY, R, R_INNER, START, END));
const fill = computed(() => arcPath(CX, CY, R, R_INNER, START, START + fraction.value * (END - START)));
const targetTick = computed(() => {
  const outer = polar(CX, CY, R + 4, END);
  const inner = polar(CX, CY, R_INNER - 4, END);
  return `M ${outer.x} ${outer.y} L ${inner.x} ${inner.y}`;
});
</script>

<template>
  <figure class="chart" :data-testid="testid">
    <figcaption class="chart__title">{{ title }}<span v-if="caption" class="chart__caption"> — {{ caption }}</span></figcaption>
    <svg viewBox="10 10 200 120" role="img" :aria-label="title" class="chart__svg" style="max-width: 240px">
      <path :d="track" fill="#e6eaf0" />
      <path :data-testid="`${testid}-fill`" :d="fill" fill="#0e5a8a"
            tabindex="0" :aria-label="`Atteinte : ${formatPercent(fraction)}`">
        <title>Atteinte : {{ formatPercent(fraction) }}</title>
      </path>
      <path :d="targetTick" stroke="#16202c" stroke-width="2" />
      <text :x="CX" :y="CY - 6" text-anchor="middle" font-size="22" font-family="monospace" fill="#16202c"
            :data-testid="`${testid}-value`">{{ formatPercent(fraction) }}</text>
      <text :x="CX" :y="CY + 14" text-anchor="middle" font-size="10" fill="#4a5868">objectif atteint à</text>
    </svg>
  </figure>
</template>
