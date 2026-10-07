import { defineStore } from 'pinia';
import { roleLabel, type Role } from '../presentation/roles';

/**
 * Current session context shown in the page header: the entity in scope and the
 * connected user's role. Real authentication and entity scoping arrive at step 2;
 * until then the store carries the mock-up defaults.
 */
export const useSessionStore = defineStore('session', {
  state: () => ({
    /** Name of the legal entity currently in scope. */
    entityName: 'Alptis Assurances' as string,
    /** Role of the connected user. */
    role: 'BUSINESS_VALIDATOR' as Role,
  }),
  getters: {
    /** French label of the current role, as displayed in the header. */
    roleLabelText: (state): string => roleLabel(state.role),
  },
  actions: {
    /**
     * Selects the entity in scope.
     * @param name entity name to display
     */
    setEntity(name: string): void {
      this.entityName = name;
    },
    /**
     * Sets the connected user's role.
     * @param role role code
     */
    setRole(role: Role): void {
      this.role = role;
    },
  },
});
