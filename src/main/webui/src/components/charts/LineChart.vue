<script setup lang="ts">
import { computed } from 'vue';
import { domainOf, ticks, project, linePoints } from '../../presentation/charts';
import { paletteColor } from '../../presentation/palette';
import { formatAmount } from '../../presentation/format';

interface Series {
  key: string;
  label: string;
  values: number[];
}

const props = withDefaults(
  defineProps<{
    title: string;
    caption?: string;
    categories: string[];
    series: Series[];
    testid: string;
  }>(),
  { caption: '' },
);

const W = 640;
const H = 300;
const PAD_L = 56;
const PAD_B = 48;
const PAD_T = 12;
const PAD_R = 12;

const empty = computed(() => props.categories.length === 0 || props.series.length === 0);
const domain = computed(() => domainOf(props.series.flatMap((s) => s.values)));
const axisTicks = computed(() => ticks(domain.value, 4));

interface Line {
  key: string;
  label: string;
  color: string;
  polyline: string;
  points: Array<{ x: number; y: number; value: number }>;
}

const lines = computed<Line[]>(() =>
  props.series.map((s, si) => {
    const pts = linePoints(s.values, domain.value, PAD_L, W - PAD_R, PAD_T, H - PAD_B);
    return {
      key: s.key,
      label: s.label,
      color: paletteColor(si),
      polyline: pts.map((p) => `${p.x},${p.y}`).join(' '),
      points: pts.map((p, i) => ({ x: p.x, y: p.y, value: s.values[i] })),
    };
  }),
);

function tickY(t: number): number {
  return project(t, domain.value, H - PAD_B, PAD_T);
}
</script>

<template>
  <figure class="chart" :data-testid="testid">
    <figcaption class="chart__title">{{ title }}<span v-if="caption" class="chart__caption"> — {{ caption }}</span></figcaption>
    <p v-if="empty" :data-testid="`${testid}-empty`" class="chart__empty">Aucune donnée à représenter.</p>
    <svg v-else :viewBox="`0 0 ${W} ${H}`" role="img" :aria-label="title" class="chart__svg">
      <line v-for="t in axisTicks" :key="t" :x1="PAD_L" :x2="W - PAD_R" :y1="tickY(t)" :y2="tickY(t)" stroke="#e6eaf0" />
      <text v-for="t in axisTicks" :key="`l${t}`" :x="PAD_L - 6" :y="tickY(t) + 4" text-anchor="end" font-size="10" fill="#4a5868">{{ formatAmount(t) }}</text>
      <g v-for="line in lines" :key="line.key">
        <polyline :points="line.polyline" fill="none" :stroke="line.color" stroke-width="2" />
        <circle v-for="(p, i) in line.points" :key="i" :data-testid="`${testid}-point-${line.key}-${i}`"
                :cx="p.x" :cy="p.y" r="3.5" :fill="line.color" tabindex="0"
                :aria-label="`${line.label} ${categories[i]} : ${formatAmount(p.value)}`">
          <title>{{ line.label }} {{ categories[i] }} : {{ formatAmount(p.value) }}</title>
        </circle>
      </g>
      <text v-for="(cat, ci) in categories" :key="`c${ci}`"
            :x="categories.length === 1 ? (PAD_L + W - PAD_R) / 2 : PAD_L + (ci / (categories.length - 1)) * (W - PAD_L - PAD_R)"
            :y="H - PAD_B + 16" text-anchor="middle" font-size="10" fill="#16202c">{{ cat }}</text>
    </svg>
    <ul v-if="!empty && series.length > 1" class="chart__legend">
      <li v-for="(s, si) in series" :key="s.key"><span class="chart__swatch" :style="{ background: paletteColor(si) }"></span>{{ s.label }}</li>
    </ul>
  </figure>
</template>
