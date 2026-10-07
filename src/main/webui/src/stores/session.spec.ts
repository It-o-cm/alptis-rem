import { describe, it, expect, beforeEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useSessionStore } from './session';

describe('session store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('starts on the mock-up defaults', () => {
    const session = useSessionStore();
    expect(session.entityName).toBe('Alptis Assurances');
    expect(session.role).toBe('BUSINESS_VALIDATOR');
    expect(session.roleLabelText).toBe('valideur métier');
  });

  it('updates the entity in scope', () => {
    const session = useSessionStore();
    session.setEntity('UNEP');
    expect(session.entityName).toBe('UNEP');
  });

  it('updates the role and its label', () => {
    const session = useSessionStore();
    session.setRole('FINANCE_CONTROLLER');
    expect(session.role).toBe('FINANCE_CONTROLLER');
    expect(session.roleLabelText).toBe('contrôleur Finance');
  });
});
