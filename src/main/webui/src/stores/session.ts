import { defineStore } from 'pinia';
import { apiGet } from '../api/client';
import { roleLabel } from '../presentation/roles';
import { entityName } from '../presentation/entities';
import { canAccess } from '../presentation/access';

interface Me {
  username: string;
  roles: string[];
  entities: string[];
}

/**
 * Current session: the authenticated user loaded from {@code /api/ui/v1/me}, with its roles and
 * visible entities. Drives the page header and hides what the user may not do (the server stays
 * the authority).
 */
export const useSessionStore = defineStore('session', {
  state: () => ({
    /** User name, or null when not authenticated. */
    username: null as string | null,
    /** Roles granted to the user. */
    roles: [] as string[],
    /** Entity codes the user may see ('*' meaning every entity). */
    entities: [] as string[],
    /** Whether the session has been loaded (success or failure). */
    loaded: false,
    /** Whether the user is authenticated. */
    authenticated: false,
  }),
  getters: {
    /** French label of the user's primary role, or a dash. */
    roleLabelText: (state): string => (state.roles.length > 0 ? roleLabel(state.roles[0]) : '—'),
    /** Display name of the entity in scope, or a dash. */
    primaryEntityName: (state): string => {
      if (state.entities.includes('*')) {
        return 'Toutes les entités';
      }
      return state.entities.length > 0 ? entityName(state.entities[0]) : '—';
    },
  },
  actions: {
    /**
     * Loads the current user from the server. On failure (not authenticated) the session is marked
     * loaded but unauthenticated.
     */
    async load(): Promise<void> {
      try {
        const me = await apiGet<Me>('/api/ui/v1/me');
        this.username = me.username;
        this.roles = me.roles;
        this.entities = me.entities;
        this.authenticated = true;
      } catch {
        this.username = null;
        this.roles = [];
        this.entities = [];
        this.authenticated = false;
      } finally {
        this.loaded = true;
      }
    },
    /**
     * Tells whether the user holds the given role.
     * @param role role code
     */
    hasRole(role: string): boolean {
      return this.roles.includes(role);
    },
    /**
     * Tells whether the user may see the given screen.
     * @param screenName route name of the screen
     */
    canSee(screenName: string): boolean {
      return canAccess(screenName, this.roles);
    },
  },
});
