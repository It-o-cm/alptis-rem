<script setup lang="ts">
import { computed } from 'vue';
import { donut, arcPath } from '../../presentation/charts';
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
    testid: string;
  }>(),
  { caption: '' },
);

const CX = 110;
const CY = 110;
const R = 100;
const R_INNER = 56;
const MAX_SLICES = 6;

const empty = computed(() => props.data.length === 0 || props.data.every((d) => d.value === 0));

interface Arc {
  d: string;
  color: string;
  label: string;
  value: number;
  fraction: number;
  testid: string;
}

const arcs = computed<Arc[]>(() =>
  donut(props.data, MAX_SLICES).map((s, i) => ({
    d: arcPath(CX, CY, R, R_INNER, s.startAngle, s.endAngle),
    color: paletteColor(i),
    label: s.label,
    value: s.value,
    fraction: s.fraction,
    testid: `${props.testid}-slice-${i}`,
  })),
);
</script>

<template>
  <figure class="chart" :data-testid="testid">
    <figcaption class="chart__title">{{ title }}<span v-if="caption" class="chart__caption"> — {{ caption }}</span></figcaption>
    <p v-if="empty" :data-testid="`${testid}-empty`" class="chart__empty">Aucune donnée à représenter.</p>
    <div v-else class="chart__donut">
      <svg viewBox="0 0 220 220" role="img" :aria-label="title" class="chart__svg" style="max-width: 220px">
        <path v-for="a in arcs" :key="a.testid" :data-testid="a.testid" :d="a.d" :fill="a.color"
              tabindex="0" :aria-label="`${a.label} : ${formatAmount(a.value)} (${formatPercent(a.fraction)})`">
          <title>{{ a.label }} : {{ formatAmount(a.value) }} ({{ formatPercent(a.fraction) }})</title>
        </path>
      </svg>
      <ul class="chart__legend">
        <li v-for="a in arcs" :key="`l${a.testid}`">
          <span class="chart__swatch" :style="{ background: a.color }"></span>{{ a.label }} — {{ formatPercent(a.fraction) }}
        </li>
      </ul>
    </div>
  </figure>
</template>
