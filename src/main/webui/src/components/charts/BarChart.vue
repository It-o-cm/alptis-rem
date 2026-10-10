<script setup lang="ts">
import { computed } from 'vue';
import { domainOf, project, bar, stack, ticks } from '../../presentation/charts';
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
    mode?: 'simple' | 'stacked' | 'grouped';
    testid: string;
  }>(),
  { caption: '', mode: 'simple' },
);

const W = 640;
const H = 300;
const PAD_L = 56;
const PAD_B = 48;
const PAD_T = 12;
const PAD_R = 12;

const empty = computed(() => props.categories.length === 0 || props.series.length === 0);

const allValues = computed(() => {
  const v: number[] = [];
  if (props.mode === 'stacked') {
    props.categories.forEach((_, ci) => {
      let sum = 0;
      props.series.forEach((s) => {
        sum += Math.max(0, s.values[ci] ?? 0);
      });
      v.push(sum);
    });
  } else {
    props.series.forEach((s) => s.values.forEach((x) => v.push(x)));
  }
  return v;
});

const domain = computed(() => domainOf(allValues.value));
const axisTicks = computed(() => ticks(domain.value, 4));

/** One drawn rectangle with its geometry, colour and accessible label. */
interface Cell {
  x: number;
  y: number;
  w: number;
  h: number;
  color: string;
  label: string;
  value: number;
  testid: string;
}

const cells = computed<Cell[]>(() => {
  const out: Cell[] = [];
  const n = props.categories.length;
  if (n === 0) {
    return out;
  }
  const plotW = W - PAD_L - PAD_R;
  const plotH = H - PAD_T - PAD_B;
  const slot = plotW / n;
  props.categories.forEach((cat, ci) => {
    if (props.mode === 'stacked') {
      const segs = stack(props.series.map((s) => Math.max(0, s.values[ci] ?? 0)));
      segs.forEach((seg, si) => {
        const y0 = project(seg.start, domain.value, PAD_T + plotH, PAD_T);
        const y1 = project(seg.end, domain.value, PAD_T + plotH, PAD_T);
        out.push({
          x: PAD_L + ci * slot + slot * 0.15,
          y: Math.min(y0, y1),
          w: slot * 0.7,
          h: Math.abs(y1 - y0),
          color: paletteColor(si),
          label: `${cat} — ${props.series[si].label}`,
          value: props.series[si].values[ci] ?? 0,
          testid: `${props.testid}-bar-${ci}-${si}`,
        });
      });
    } else {
      const m = props.series.length;
      const bw = (slot * 0.7) / m;
      props.series.forEach((s, si) => {
        const value = s.values[ci] ?? 0;
        const b = bar(value, domain.value, PAD_T + plotH, PAD_T);
        out.push({
          x: PAD_L + ci * slot + slot * 0.15 + si * bw,
          y: b.start,
          w: bw * 0.92,
          h: b.length,
          color: props.mode === 'simple' ? paletteColor(ci) : paletteColor(si),
          label: m === 1 ? cat : `${cat} — ${s.label}`,
          value,
          testid: `${props.testid}-bar-${ci}${m === 1 ? '' : '-' + si}`,
        });
      });
    }
  });
  return out;
});

const zeroY = computed(() => project(0, domain.value, PAD_T + (H - PAD_T - PAD_B), PAD_T));

function tickY(t: number): number {
  return project(t, domain.value, PAD_T + (H - PAD_T - PAD_B), PAD_T);
}
</script>

<template>
  <figure class="chart" :data-testid="testid">
    <figcaption class="chart__title">{{ title }}<span v-if="caption" class="chart__caption"> — {{ caption }}</span></figcaption>
    <p v-if="empty" :data-testid="`${testid}-empty`" class="chart__empty">Aucune donnée à représenter.</p>
    <svg v-else :viewBox="`0 0 ${W} ${H}`" role="img" :aria-label="title" class="chart__svg">
      <line v-for="t in axisTicks" :key="t" :x1="PAD_L" :x2="W - PAD_R" :y1="tickY(t)" :y2="tickY(t)" stroke="#e6eaf0" />
      <text v-for="t in axisTicks" :key="`l${t}`" :x="PAD_L - 6" :y="tickY(t) + 4" text-anchor="end" font-size="10" fill="#4a5868">{{ formatAmount(t) }}</text>
      <line :x1="PAD_L" :x2="W - PAD_R" :y1="zeroY" :y2="zeroY" stroke="#4a5868" />
      <g v-for="c in cells" :key="c.testid">
        <rect :data-testid="c.testid" :x="c.x" :y="c.y" :width="c.w" :height="c.h" :fill="c.color"
              tabindex="0" :aria-label="`${c.label} : ${formatAmount(c.value)}`">
          <title>{{ c.label }} : {{ formatAmount(c.value) }}</title>
        </rect>
      </g>
      <text v-for="(cat, ci) in categories" :key="`c${ci}`"
            :x="PAD_L + (ci + 0.5) * ((W - PAD_L - PAD_R) / categories.length)" :y="H - PAD_B + 16"
            text-anchor="middle" font-size="10" fill="#16202c">{{ cat }}</text>
    </svg>
    <ul v-if="!empty && series.length > 1" class="chart__legend">
      <li v-for="(s, si) in series" :key="s.key"><span class="chart__swatch" :style="{ background: paletteColor(si) }"></span>{{ s.label }}</li>
    </ul>
  </figure>
</template>
