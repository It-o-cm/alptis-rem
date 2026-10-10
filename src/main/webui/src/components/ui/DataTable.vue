<script setup lang="ts" generic="Row">
import { computed, ref, watch } from 'vue';

/** One column of a {@link DataTable}. */
export interface Column<Row> {
  /** Key of the row field, also the name of the per-cell slot. */
  readonly key: string;
  /** Column header label. */
  readonly label: string;
  /** Right-aligns the cell in monospace digits when set to 'num'. */
  readonly align?: 'num';
  /** Optional accessor when the displayed value is not `row[key]`. */
  readonly value?: (row: Row) => unknown;
}

/**
 * The single list table of the application (ERGONOMIE §3): homogeneous headers,
 * amounts and numbers right-aligned in monospace digits, an explicit empty row,
 * identical pagination everywhere and the action column last. While the list is
 * loading the table says so; "Aucun résultat" only shows after the server has
 * answered.
 */
const props = withDefaults(
  defineProps<{
    /** Column definitions, in display order (action column excluded). */
    columns: readonly Column<Row>[];
    /** The rows to display. */
    rows: readonly Row[];
    /** Field used as the Vue key, or a function; falls back to the index. */
    rowKey?: string | ((row: Row) => string | number);
    /** Message shown when there is no row (after loading). */
    empty?: string;
    /** Whether the list is still loading. */
    loading?: boolean;
    /** Enables client-side pagination at this page size. */
    pageSize?: number;
    /** Makes rows focusable/clickable, emitting `row-click`. */
    clickable?: boolean;
    /** Builds a per-row `data-testid`. */
    rowTestid?: (row: Row) => string;
    /** `data-testid` of the empty-state cell. */
    emptyTestid?: string;
  }>(),
  {
    rowKey: undefined,
    empty: 'Aucun résultat.',
    loading: false,
    pageSize: undefined,
    clickable: false,
    rowTestid: undefined,
    emptyTestid: 'table-empty',
  },
);

const emit = defineEmits<{ (e: 'row-click', row: Row): void }>();

/** Total number of columns including the trailing action column if present. */
const slots = defineSlots<{
  actions?: (props: { row: Row }) => unknown;
  [key: `cell-${string}`]: (props: { row: Row; value: unknown }) => unknown;
}>();

/** Whether an action column slot is provided. */
const hasActions = computed(() => Boolean(slots.actions));
/** Column span covering every column, for the loading and empty rows. */
const colspan = computed(() => props.columns.length + (hasActions.value ? 1 : 0));

/** Current page, 1-based. */
const page = ref(1);
watch(
  () => props.rows,
  () => {
    page.value = 1;
  },
);

/** Number of pages, or 1 when pagination is off. */
const pageCount = computed(() =>
  props.pageSize ? Math.max(1, Math.ceil(props.rows.length / props.pageSize)) : 1,
);

/** Rows of the current page (or all rows when pagination is off). */
const pageRows = computed(() => {
  if (!props.pageSize) {
    return props.rows;
  }
  const start = (page.value - 1) * props.pageSize;
  return props.rows.slice(start, start + props.pageSize);
});

/** Whether to render the pagination bar. */
const paginated = computed(() => Boolean(props.pageSize) && props.rows.length > (props.pageSize ?? 0));

/**
 * Resolves the Vue key for a row.
 * @param row the row
 * @param index its index in the page
 */
function keyOf(row: Row, index: number): string | number {
  if (typeof props.rowKey === 'function') {
    return props.rowKey(row);
  }
  if (typeof props.rowKey === 'string') {
    return (row as Record<string, unknown>)[props.rowKey] as string | number;
  }
  return index;
}

/**
 * Resolves the displayed value of a cell.
 * @param row the row
 * @param column the column
 */
function valueOf(row: Row, column: Column<Row>): unknown {
  return column.value ? column.value(row) : (row as Record<string, unknown>)[column.key];
}

/**
 * Emits a row click when the table is clickable.
 * @param row the clicked row
 */
function onRowClick(row: Row): void {
  if (props.clickable) {
    emit('row-click', row);
  }
}

/** Moves to the previous page, floored at 1. */
function prev(): void {
  page.value = Math.max(1, page.value - 1);
}

/** Moves to the next page, capped at the last page. */
function next(): void {
  page.value = Math.min(pageCount.value, page.value + 1);
}
</script>

<template>
  <div class="ui-table" data-ui="table">
    <div class="ui-table__scroll">
      <table>
        <thead>
          <tr>
            <th v-for="col in columns" :key="col.key" :class="{ num: col.align === 'num' }">{{ col.label }}</th>
            <th v-if="hasActions" class="ui-table__actions-head"><span class="ui-sr">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td :colspan="colspan" class="ui-table__state" data-testid="table-loading">Chargement…</td>
          </tr>
          <tr v-else-if="rows.length === 0">
            <td :colspan="colspan" class="ui-table__state" :data-testid="emptyTestid">{{ empty }}</td>
          </tr>
          <tr
            v-for="(row, index) in pageRows"
            v-else
            :key="keyOf(row, index)"
            :class="{ 'ui-table__row--clickable': clickable }"
            :data-testid="rowTestid ? rowTestid(row) : undefined"
            :tabindex="clickable ? 0 : undefined"
            @click="onRowClick(row)"
            @keyup.enter="onRowClick(row)"
          >
            <td v-for="col in columns" :key="col.key" :class="{ num: col.align === 'num' }">
              <slot :name="`cell-${col.key}`" :row="row" :value="valueOf(row, col)">{{ valueOf(row, col) }}</slot>
            </td>
            <td v-if="hasActions" class="ui-table__actions-cell">
              <slot name="actions" :row="row" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="paginated" class="ui-table__pager" data-testid="table-pager">
      <button class="ui-table__page-btn" type="button" :disabled="page === 1" data-testid="table-prev" @click="prev">
        Précédent
      </button>
      <span class="ui-table__page-info" data-testid="table-page">Page {{ page }} / {{ pageCount }}</span>
      <button
        class="ui-table__page-btn"
        type="button"
        :disabled="page === pageCount"
        data-testid="table-next"
        @click="next"
      >
        Suivant
      </button>
    </div>
  </div>
</template>

<style scoped>
.ui-table__scroll {
  overflow-x: auto;
  /* Containing block so the sr-only action header cannot escape the horizontal
     clip and push the page wider than the viewport on a wide table. */
  position: relative;
}
.ui-table__state {
  color: var(--muted);
}
.ui-table__row--clickable {
  cursor: pointer;
}
.ui-table__row--clickable:hover {
  background: var(--line-soft);
}
.ui-table__actions-head,
.ui-table__actions-cell {
  text-align: right;
  white-space: nowrap;
}
.ui-table__pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--line-soft);
}
.ui-table__page-btn {
  height: auto;
  padding: var(--space-1) var(--space-3);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: #fff;
  color: var(--primary);
  font: 600 var(--fs-small) 'IBM Plex Sans', sans-serif;
  cursor: pointer;
}
.ui-table__page-btn:disabled {
  color: var(--muted);
  cursor: not-allowed;
}
.ui-table__page-info {
  font-size: var(--fs-small);
  color: var(--muted);
}
.ui-sr {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
</style>
