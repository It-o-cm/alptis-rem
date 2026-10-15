import { test, expect } from '@playwright/test';
import { loadDemo } from '../support/helpers';

// Parcours FLX-03 (section 4.6): each resource of the public read API (section 9.8), called with and
// without the entity parameter, returns its content and enforces the entity partition (cloisonnement).
// Driven straight against /api/v1 as two single-entity operators (ALPTIS and UNEP), so the partition is
// observed from both sides.
//
// @path:FLX-03

/** A distributor of the demonstration carrying accounts, lines, statements and a tax certificate. */
const CODE = 'P20001';
const ALPTIS = 'ALPTIS_ASSURANCES';

/** The HTTP Basic header of a dev user (username = password). */
const basic = (user: string): string => 'Basic ' + Buffer.from(`${user}:${user}`).toString('base64');
const ALPTIS_OP = { Authorization: basic('operator-alptis') };
const UNEP_OP = { Authorization: basic('operator-unep') };

test('chaque ressource de l’API publique §9.8 : contenu et cloisonnement par entité @path:FLX-03', async ({
  request,
}) => {
  await loadDemo(request);

  // The five per-distributor read resources: each returns a JSON array of the caller's entity, the
  // explicit own-entity filter yields the same, and an out-of-scope entity is partitioned off (404).
  const distributorResources = [
    { path: `/api/v1/distributors/${CODE}/accounts`, nonEmpty: true },
    { path: `/api/v1/distributors/${CODE}/commission-lines`, nonEmpty: true },
    { path: `/api/v1/distributors/${CODE}/statements`, nonEmpty: true },
    { path: `/api/v1/distributors/${CODE}/tax-certificates`, nonEmpty: true },
    { path: `/api/v1/distributors/${CODE}/advance-agreements`, nonEmpty: false },
  ];
  for (const res of distributorResources) {
    // Without the entity parameter: the caller's partition (ALPTIS) — content is a JSON array.
    const bare = await request.get(res.path, { headers: ALPTIS_OP });
    expect(bare.status(), res.path).toBe(200);
    const bareBody = await bare.json();
    expect(Array.isArray(bareBody), res.path).toBeTruthy();
    if (res.nonEmpty) {
      expect(bareBody.length, res.path).toBeGreaterThan(0);
    }
    // With the own entity: same content.
    const scoped = await request.get(`${res.path}?entity=${ALPTIS}`, { headers: ALPTIS_OP });
    expect(scoped.status(), res.path).toBe(200);
    expect((await scoped.json()).length, res.path).toBe(bareBody.length);
    // With an out-of-scope entity: partitioned off (cloisonnement).
    const crossed = await request.get(`${res.path}?entity=UNEP`, { headers: ALPTIS_OP });
    expect(crossed.status(), res.path).toBe(404);
  }

  // The periods resource: each operator sees only their own entity's periods, never the other's.
  const alptisPeriods = await request.get('/api/v1/periods', { headers: ALPTIS_OP });
  expect(alptisPeriods.status()).toBe(200);
  const alptisMonths = await alptisPeriods.json();
  expect(alptisMonths.length).toBeGreaterThan(0);
  expect(alptisMonths.every((p: { entity: string }) => p.entity === ALPTIS)).toBeTruthy();
  const unepPeriods = await request.get('/api/v1/periods', { headers: UNEP_OP });
  expect(unepPeriods.status()).toBe(200);
  const unepMonths = await unepPeriods.json();
  expect(unepMonths.every((p: { entity: string }) => p.entity === 'UNEP')).toBeTruthy();
  // The ALPTIS operator cannot read the UNEP periods through the entity parameter.
  expect((await request.get('/api/v1/periods?entity=UNEP', { headers: ALPTIS_OP })).status()).toBe(404);

  // The import deposit resource (section 9.8 write): depositing into the own entity returns a journalled
  // result; depositing into an out-of-scope entity is partitioned off.
  const file =
    'entity_code;partner_code;legal_name;distributor_type\n' +
    'ALPTIS_ASSURANCES;FLX03-PUB;Courtier API publique;COURTIER\n';
  const deposit = await request.post(
    `/api/v1/imports/distributors?entity=${ALPTIS}&sourceSystem=SESAME&fileName=flx03-public.csv`,
    { headers: { ...ALPTIS_OP, 'Content-Type': 'text/plain' }, data: file },
  );
  expect(deposit.status()).toBe(200);
  const result = await deposit.json();
  expect(result.flowCode).toBe('distributors');
  expect(result.read).toBe(1);
  const batchId = result.id;
  const crossedDeposit = await request.post(
    `/api/v1/imports/distributors?entity=UNEP&sourceSystem=SESAME&fileName=flx03-cross.csv`,
    { headers: { ...ALPTIS_OP, 'Content-Type': 'text/plain' }, data: file },
  );
  expect(crossedDeposit.status()).toBe(404);

  // The import result resource: the depositor reads its journalled batch; the other entity's operator is
  // partitioned off the ALPTIS batch.
  const own = await request.get(`/api/v1/imports/${batchId}`, { headers: ALPTIS_OP });
  expect(own.status()).toBe(200);
  expect((await own.json()).id).toBe(batchId);
  const foreign = await request.get(`/api/v1/imports/${batchId}`, { headers: UNEP_OP });
  expect(foreign.status()).toBe(404);
});
