<script setup lang="ts">
import { computed } from 'vue';
import { domainOf, bar } from '../../presentation/charts';
import { paletteColor } from '../../presentation/palette';
import { formatAmount, formatPercent } from '../../presentation/format';

interface Datum {
  label: string;
  value: number;
}

const props = withDefaults(
  defineProps<{
    title: string;
    caption?: string;
    data: Datum[];
    format?: 'amount' | 'percent' | 'count';
    testid: string;
  }>(),
  { caption: '', format: 'amount' },
);

function show(value: number): string {
  if (props.format === 'percent') {
    return formatPercent(value);
  }
  if (props.format === 'count') {
    return String(value);
  }
  return formatAmount(value);
}

const W = 640;
const ROW = 28;
const PAD_L = 160;
const PAD_R = 60;
const PAD_T = 8;

const empty = computed(() => props.data.length === 0);
const height = computed(() => PAD_T * 2 + Math.max(1, props.data.length) * ROW);
const domain = computed(() => domainOf(props.data.map((d) => d.value)));

interface Row {
  x: number;
  y: number;
  w: number;
  color: string;
  label: string;
  value: number;
  labelX: number;
  testid: string;
}

const rows = computed<Row[]>(() =>
  props.data.map((d, i) => {
    const b = bar(d.value, domain.value, PAD_L, W - PAD_R);
    return {
      x: b.start,
      y: PAD_T + i * ROW + 4,
      w: b.length,
      color: d.value < 0 ? '#8f1d16' : paletteColor(i),
      label: d.label,
      value: d.value,
      labelX: b.start + b.length + 4,
      testid: `${props.testid}-bar-${i}`,
    };
  }),
);
</script>

<template>
  <figure class="chart" :data-testid="testid">
    <figcaption class="chart__title">{{ title }}<span v-if="caption" class="chart__caption"> — {{ caption }}</span></figcaption>
    <p v-if="empty" :data-testid="`${testid}-empty`" class="chart__empty">Aucune donnée à représenter.</p>
    <svg v-else :viewBox="`0 0 ${W} ${height}`" role="img" :aria-label="title" class="chart__svg">
      <g v-for="r in rows" :key="r.testid">
        <text :x="PAD_L - 6" :y="r.y + ROW / 2" text-anchor="end" font-size="11" fill="#16202c">{{ r.label }}</text>
        <rect :data-testid="r.testid" :x="r.x" :y="r.y" :width="r.w" :height="ROW - 10" :fill="r.color"
              tabindex="0" :aria-label="`${r.label} : ${show(r.value)}`">
          <title>{{ r.label }} : {{ show(r.value) }}</title>
        </rect>
        <text :x="r.labelX" :y="r.y + ROW / 2" font-size="10" fill="#4a5868">{{ show(r.value) }}</text>
      </g>
    </svg>
  </figure>
</template>
