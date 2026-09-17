import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync } from 'node:crypto';
import { generateOtp, hashOtp, verifyOtp, normalizeEmail, isValidEmail, hashOpaqueToken, issueResetToken, verifyResetToken, randomSaltB64 } from '../src/security.js';

function keyMaterial() {
  const { publicKey, privateKey } = generateKeyPairSync('ed25519');
  return {
    privateB64: privateKey.export({ type: 'pkcs8', format: 'der' }).toString('base64'),
    publicB64: publicKey.export({ type: 'spki', format: 'der' }).toString('base64')
  };
}

test('OTP is six digits and verifies only with the original code', async () => {
  const otp = generateOtp();
  assert.match(otp, /^\d{6}$/);
  const salt = randomSaltB64();
  const hash = await hashOtp(otp, salt, 'test-otp-secret');
  assert.equal(await verifyOtp(otp, salt, hash, 'test-otp-secret'), true);
  assert.equal(await verifyOtp(otp === '000000' ? '000001' : '000000', salt, hash, 'test-otp-secret'), false);
});

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

test('reset authorization is an Ed25519 signed three-part token', async () => {
  const keys = keyMaterial();
  const nowMs = 1700000000000;
  const token = await issueResetToken({
    email: 'test@example.com',
    sessionId: 'session-1',
    nowMs,
    ttlSeconds: 600
  }, keys.privateB64);
  assert.equal(token.split('.').length, 3);
  assert.ok(await verifyResetToken(token, keys.publicB64, nowMs));
});

test('reset authorization rejects tampering, wrong audience, and expiry', async () => {
  const keys = keyMaterial();
  const nowMs = 1700000000000;
  const token = await issueResetToken({
    email: 'test@example.com',
    sessionId: 'session-1',
    nowMs,
    ttlSeconds: 600
  }, keys.privateB64);
  assert.ok(await verifyResetToken(token, keys.publicB64, nowMs));
  const parts = token.split('.');
  const body = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
  body.aud = 'attacker';
  parts[1] = Buffer.from(JSON.stringify(body)).toString('base64url');
  assert.equal(await verifyResetToken(parts.join('.'), keys.publicB64, nowMs), null);
  assert.equal(await verifyResetToken(token, keys.publicB64, nowMs + 601000), null);
});
