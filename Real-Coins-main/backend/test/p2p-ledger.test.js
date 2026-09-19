import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

test('P2P trade ledger is immutable and one-event-per-order', () => {
  const sql = readFileSync(new URL('../migrations/0020_p2p_trade_ledger.sql', import.meta.url), 'utf8');
  assert.match(sql, /UNIQUE\(order_id, account_id, event\)/);
  assert.match(sql, /p2p_trade_ledger_immutable_update/);
  assert.match(sql, /p2p_trade_ledger_immutable_delete/);
});

test('P2P expiry records BUY escrow unlock atomically', () => {
  const sql = readFileSync(new URL('../migrations/0020_p2p_trade_ledger.sql', import.meta.url), 'utf8');
  assert.match(sql, /OLD\.status = 'ESCROW_LOCKED'/);
  assert.match(sql, /NEW\.status = 'CANCELLED'/);
  assert.match(sql, /'ESCROW_UNLOCK'/);
  assert.match(sql, /locked_delta/);
});
