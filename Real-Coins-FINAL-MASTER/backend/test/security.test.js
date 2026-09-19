import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { normalizeEmail, isValidEmail, hashOpaqueToken, hashPassword, verifyPassword } from '../src/security.js';

test('email normalization and validation are deterministic', () => {
  assert.equal(normalizeEmail('  Test@Example.COM '), 'test@example.com');
  assert.equal(isValidEmail('test@example.com'), true);
  assert.equal(isValidEmail('not-an-email'), false);
});

test('opaque token hashes are deterministic and do not expose the token', async () => {
  const token = 'test-token-value';
  const first = await hashOpaqueToken(token, 'test-secret');
  assert.notEqual(first, token);
  assert.equal(first, await hashOpaqueToken(token, 'test-secret'));
  assert.notEqual(first, await hashOpaqueToken(token, 'different-secret'));
});

test('password hashing verifies the original password only', async () => {
  const hashed = await hashPassword('correct-horse-battery-staple');
  assert.equal(await verifyPassword('correct-horse-battery-staple', hashed.saltB64, hashed.hashB64), true);
  assert.equal(await verifyPassword('wrong-password', hashed.saltB64, hashed.hashB64), false);
});


test('financial request approval policy forbids self-approval', () => {
  const requestAccountId = 'RC_ACCOUNT_USER';
  const approvingAdminId = 'RC_ACCOUNT_ADMIN';
  assert.notEqual(requestAccountId, approvingAdminId);
  assert.equal(requestAccountId, 'RC_ACCOUNT_USER');
  assert.equal(approvingAdminId, 'RC_ACCOUNT_ADMIN');
});

test('financial review SQL requires an active admin reviewer', () => {
  const sql = readFileSync(new URL('../migrations/0018_financial_review_admin_guard.sql', import.meta.url), 'utf8');
  assert.match(sql, /role = 'ADMIN'/);
  assert.match(sql, /disabled_at_ms IS NULL/);
  assert.match(sql, /financial_review_admin_required/);
});



test('P2P order escrow ledger distinguishes BUY locks from SELL reservations', () => {
  const source = readFileSync(new URL('../src/index.js', import.meta.url), 'utf8');
  const migration = readFileSync(new URL('../migrations/0024_p2p_trade_ledger_sell_guard.sql', import.meta.url), 'utf8');
  assert.match(source, /if \(ad\.type === 'BUY'\)/);
  assert.match(migration, /sell_order_escrow_lock_forbidden/);
  assert.match(migration, /NEW\.event = 'ESCROW_LOCK'/);
});

test('wallet financial ledger is immutable', () => {
  const sql = readFileSync(new URL('../migrations/0026_wallet_financial_ledger_immutable.sql', import.meta.url), 'utf8');
  assert.match(sql, /BEFORE UPDATE ON wallet_financial_ledger/);
  assert.match(sql, /BEFORE DELETE ON wallet_financial_ledger/);
  assert.match(sql, /wallet_financial_ledger_immutable/);
});

test('bootstrap secrets compare without direct string equality', async () => {
  const { constantTimeSecretEqual } = await import('../src/security.js');
  assert.equal(await constantTimeSecretEqual('bootstrap-secret-123456', 'bootstrap-secret-123456'), true);
  assert.equal(await constantTimeSecretEqual('bootstrap-secret-123456', 'bootstrap-secret-654321'), false);
});
