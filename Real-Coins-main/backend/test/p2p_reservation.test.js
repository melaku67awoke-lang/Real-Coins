import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const migration = fs.readFileSync(new URL('../migrations/0022_p2p_ad_reservation_consumption.sql', import.meta.url), 'utf8');

test('SELL reservation ledger records completed order consumption', () => {
  assert.match(migration, /event TEXT NOT NULL CHECK \(event IN \('RESERVE', 'RELEASE', 'CONSUME'\)\)/);
  assert.match(migration, /OLD\.status = 'PAID'/);
  assert.match(migration, /NEW\.status = 'COMPLETED'/);
  assert.match(migration, /type FROM p2p_ads WHERE id = NEW\.ad_id\) = 'SELL'/);
  assert.match(migration, /NEW\.crypto_amount/);
  assert.match(migration, /event = 'CONSUME'/);
  assert.match(migration, /event = 'RESERVE'/);
  assert.match(migration, /event = 'RELEASE'/);
});
