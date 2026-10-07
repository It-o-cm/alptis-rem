import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useSessionStore } from './session';

function okResponse(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as Response;
}

function errorResponse(status: number) {
  return { ok: false, status, json: async () => ({ detail: 'refus' }) } as unknown as Response;
}

describe('session store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('starts empty and unauthenticated', () => {
    const session = useSessionStore();
    expect(session.authenticated).toBe(false);
    expect(session.roleLabelText).toBe('—');
    expect(session.primaryEntityName).toBe('—');
  });

  it('loads a scoped user', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        okResponse({ username: 'operator-alptis', roles: ['OPERATOR'], entities: ['ALPTIS_ASSURANCES'] }),
      ),
    );
    const session = useSessionStore();
    await session.load();

    expect(session.authenticated).toBe(true);
    expect(session.username).toBe('operator-alptis');
    expect(session.roleLabelText).toBe('gestionnaire');
    expect(session.primaryEntityName).toBe('Alptis Assurances');
    expect(session.hasRole('OPERATOR')).toBe(true);
    expect(session.hasRole('IT_ADMIN')).toBe(false);
    expect(session.canSee('adminEntities')).toBe(true);
    expect(session.canSee('adminRoleMappings')).toBe(false);
    expect(session.canSee('imports')).toBe(true);
    expect(session.loaded).toBe(true);
  });

  it('shows every entity for a group-level user', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(okResponse({ username: 'auditor', roles: ['AUDITOR'], entities: ['*'] })),
    );
    const session = useSessionStore();
    await session.load();
    expect(session.primaryEntityName).toBe('Toutes les entités');
  });

  it('marks the session unauthenticated on 401', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errorResponse(401)));
    const session = useSessionStore();
    await session.load();

    expect(session.authenticated).toBe(false);
    expect(session.roles).toEqual([]);
    expect(session.loaded).toBe(true);
  });
});
