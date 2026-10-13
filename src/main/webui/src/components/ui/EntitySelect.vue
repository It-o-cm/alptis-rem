<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useSessionStore } from '../../stores/session';
import { entityName, visibleEntities } from '../../presentation/entities';
import CodeSelect from './CodeSelect.vue';

/**
 * Entity selector fed by the session's visible entities (ADM-03): the user picks a legal entity by
 * its name from a list, never types a code. When the bound value is not among the visible entities
 * and no empty option is offered, it is set to the first entity on mount, so a screen always starts
 * on a real entity.
 */
const model = defineModel<string>({ required: true });

const props = withDefaults(
  defineProps<{
    /** Whether to offer an empty "all entities" option. */
    allowEmpty?: boolean;
    /** Label of the empty option when offered. */
    emptyLabel?: string;
  }>(),
  { allowEmpty: false, emptyLabel: 'Toutes les entités' },
);

const session = useSessionStore();

/** The entities the user may see, as (code, name) options. */
const options = computed(() =>
  visibleEntities(session.entities).map((c) => ({ value: c, label: entityName(c) })),
);

onMounted(() => {
  if (!props.allowEmpty && options.value.length > 0 && !options.value.some((o) => o.value === model.value)) {
    model.value = options.value[0].value;
  }
});
</script>

<template>
  <CodeSelect v-model="model" :options="options" :allow-empty="allowEmpty" :empty-label="emptyLabel" />
</template>
