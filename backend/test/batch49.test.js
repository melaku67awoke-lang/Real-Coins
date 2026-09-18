import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const migration = fs.readFileSync(
  path.join(process.cwd(), 'migrations', '0027_financial_numeric_integrity.sql'),
  'utf8'
);

test('batch49 financial request numeric guards reject invalid ranges', () => {
  assert.match(migration, /trg_wallet_financial_request_numeric_insert/);
  assert.match(migration, /NEW\.amount <= 0 OR NEW\.amount > 1000000000/);
  assert.match(migration, /trg_wallet_financial_request_numeric_update/);
});

test('batch49 financial ledgers reject invalid amount and delta ranges', () => {
  assert.match(migration, /trg_wallet_financial_ledger_numeric_insert/);
  assert.match(migration, /trg_p2p_wallet_ledger_numeric_insert/);
  assert.match(migration, /NEW\.balance_delta < -1000000000/);
  assert.match(migration, /NEW\.balance_delta > 1000000000/);
});
