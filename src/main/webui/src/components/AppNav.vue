<script setup lang="ts">
import { computed, onMounted, reactive, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { NAV_SECTIONS, sectionHeadingOf } from '../presentation/navigation';
import { orderItemsForRoles, workSectionsForRoles } from '../presentation/menu';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const route = useRoute();

// Sections with only the entries the current user may see, the acting screens
// first (ERGONOMIE §2.2); empty sections are dropped (§2.2).
const visibleSections = computed(() =>
  NAV_SECTIONS.map((section) => ({
    heading: section.heading,
    items: orderItemsForRoles(
      section.items.filter((item) => session.canSee(item.name)),
      session.roles,
    ),
  })).filter((section) => section.items.length > 0),
);

// The rubric of the current screen, always kept open (§2.1).
const currentSection = computed(() => sectionHeadingOf(route.name as string | undefined));

// Open/closed state per rubric. Seeded from storage or, on first connection,
// from the role's work rubrics (§2.2).
const openState = reactive<Record<string, boolean>>({});

/** Storage key holding the open rubrics for the current user. */
function storageKey(): string {
  return `nav-open:${session.username ?? 'anon'}`;
}

/** Reads the remembered open rubrics, with a clean fallback when storage is unavailable. */
function readStored(): string[] | null {
  try {
    const raw = window.localStorage.getItem(storageKey());
    return raw ? (JSON.parse(raw) as string[]) : null;
  } catch {
    return null;
  }
}

/** Remembers the open rubrics, ignoring any storage failure. */
function persist(): void {
  try {
    const open = Object.keys(openState).filter((heading) => openState[heading]);
    window.localStorage.setItem(storageKey(), JSON.stringify(open));
  } catch {
    // Storage unavailable: the menu still works, it just will not remember.
  }
}

/** Seeds the open state from storage, or from the role's work rubrics on first connection. */
function seed(): void {
  const stored = readStored();
  const open = new Set(stored ?? workSectionsForRoles(session.roles));
  for (const section of visibleSections.value) {
    openState[section.heading] = open.has(section.heading);
  }
}

/**
 * Tells whether a rubric is expanded. The current screen's rubric is always open.
 * @param heading the rubric heading
 */
function isOpen(heading: string): boolean {
  return heading === currentSection.value || Boolean(openState[heading]);
}

/**
 * Toggles a rubric and remembers the new state.
 * @param heading the rubric heading
 */
function toggle(heading: string): void {
  openState[heading] = !openState[heading];
  persist();
}

/** HTML id of a rubric's entry list, for `aria-controls`. */
function panelId(heading: string): string {
  return `nav-panel-${heading.replace(/\s+/g, '-').toLowerCase()}`;
}

onMounted(seed);
// Re-seed when the signed-in user changes (roles or storage key differ).
watch(() => session.username, seed);
// Keep the current screen's rubric open when navigating.
watch(currentSection, (heading) => {
  if (heading) {
    openState[heading] = true;
  }
});
</script>

<template>
  <nav class="nav" aria-label="Navigation principale">
    <div class="nav__brand">SI Rémunération</div>
    <div class="nav__scroll">
      <section v-for="section in visibleSections" :key="section.heading" class="nav__section">
        <button
          class="nav__toggle"
          type="button"
          :aria-expanded="isOpen(section.heading)"
          :aria-controls="panelId(section.heading)"
          :data-testid="`nav-toggle-${section.heading}`"
          @click="toggle(section.heading)"
        >
          <span class="nav__chevron" aria-hidden="true" /> {{ section.heading }}
        </button>
        <ul v-show="isOpen(section.heading)" :id="panelId(section.heading)" class="nav__items">
          <li v-for="item in section.items" :key="item.name">
            <RouterLink :to="item.path" :data-testid="`nav-link-${item.name}`">{{ item.label }}</RouterLink>
          </li>
        </ul>
      </section>
    </div>
  </nav>
</template>
