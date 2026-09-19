import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const sql = await readFile(new URL('../migrations/0025_p2p_finite_numeric_guard.sql', import.meta.url), 'utf8');
test('finite numeric guard covers ads and orders', () => {
  assert.match(sql, /trg_p2p_ads_finite_numeric_insert/);
  assert.match(sql, /trg_p2p_ads_finite_numeric_update/);
  assert.match(sql, /trg_p2p_orders_finite_numeric_insert/);
  assert.match(sql, /trg_p2p_orders_finite_numeric_update/);
  assert.match(sql, /1000000000000/);
});
test('finite numeric guard rejects non-numeric storage classes', () => {
  assert.match(sql, /typeof\(NEW\.crypto_amount\) NOT IN \('integer', 'real'\)/);
  assert.match(sql, /typeof\(NEW\.fiat_price\) NOT IN \('integer', 'real'\)/);
});
