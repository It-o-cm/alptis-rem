<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useProductsStore } from '../../stores/products';
import CodeSelect from './CodeSelect.vue';

/**
 * Product selector fed by the product catalogue (ADM-03): the user picks a product by its name from
 * a list, never types a code. The currently bound code is always offered, even before the catalogue
 * loads, so the selection holds.
 */
const model = defineModel<string>({ required: true });

withDefaults(
  defineProps<{
    /** Whether to offer an empty "all products" option (for filters). */
    allowEmpty?: boolean;
    /** Label of the empty option when offered. */
    emptyLabel?: string;
  }>(),
  { allowEmpty: false, emptyLabel: 'Tous les produits' },
);

const products = useProductsStore();

onMounted(() => {
  products.ensure();
});

/** The known products, always including the currently bound code. */
const options = computed(() => {
  const list = products.options;
  if (model.value && !list.some((o) => o.value === model.value)) {
    return [{ value: model.value, label: model.value }, ...list];
  }
  return list;
});
</script>

<template>
  <CodeSelect v-model="model" :options="options" :allow-empty="allowEmpty" :empty-label="emptyLabel" />
</template>
