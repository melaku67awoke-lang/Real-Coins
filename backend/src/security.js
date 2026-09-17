const encoder = new TextEncoder();

const OTP_DIGITS = 6;
const OTP_SALT_BYTES = 16;

function bytesToBase64Url(bytes) {
  let binary = '';
  for (const byte of new Uint8Array(bytes)) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

function bytesToBase64(bytes) {
  let binary = '';
  for (const byte of new Uint8Array(bytes)) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function base64ToBytes(value) {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized + '='.repeat((4 - normalized.length % 4) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

export function normalizeEmail(email) {
  return typeof email === 'string' ? email.trim().toLowerCase() : '';
}

export function isValidEmail(email) {
  return email.length >= 3 && email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

export function generateOtp() {
  const max = 10 ** OTP_DIGITS;
  const limit = Math.floor(0x1_0000_0000 / max) * max;
  const bytes = new Uint32Array(1);
  do {
    crypto.getRandomValues(bytes);
  } while (bytes[0] >= limit);
  return String(bytes[0] % max).padStart(OTP_DIGITS, '0');
}

export function randomSaltB64() {
  const salt = new Uint8Array(OTP_SALT_BYTES);
  crypto.getRandomValues(salt);
  return bytesToBase64(salt);
}

export async function hmacSha256(secret, dataBytes) {
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign', 'verify']
  );
  return new Uint8Array(await crypto.subtle.sign('HMAC', key, dataBytes));
}

export async function hashOtp(otp, saltB64, secret) {
  const salt = base64ToBytes(saltB64);
  const data = new Uint8Array(salt.length + encoder.encode(otp).length);
  data.set(salt, 0);
  data.set(encoder.encode(otp), salt.length);
  return bytesToBase64(await hmacSha256(secret, data));
}

export async function verifyOtp(otp, saltB64, expectedHashB64, secret) {
  const salt = base64ToBytes(saltB64);
  const data = new Uint8Array(salt.length + encoder.encode(otp).length);
  data.set(salt, 0);
  data.set(encoder.encode(otp), salt.length);
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['verify']
  );
  return crypto.subtle.verify('HMAC', key, base64ToBytes(expectedHashB64), data);
}

export async function hashOpaqueToken(token, secret = '') {
  const data = encoder.encode(token);
  if (secret) return bytesToBase64(await hmacSha256(secret, data));
  return bytesToBase64(await crypto.subtle.digest('SHA-256', data));
}

async function importEd25519PrivateKey(base64Pkcs8) {
  return crypto.subtle.importKey(
    'pkcs8',
    base64ToBytes(base64Pkcs8),
    'Ed25519',
    false,
    ['sign']
  );
}

async function importEd25519PublicKey(base64Spki) {
  return crypto.subtle.importKey(
    'spki',
    base64ToBytes(base64Spki),
    'Ed25519',
    false,
    ['verify']
  );
}

function encodeJson(value) {
  return new TextEncoder().encode(JSON.stringify(value));
}

function decodeJsonBase64Url(value) {
  return JSON.parse(new TextDecoder().decode(base64ToBytes(value)));
}

export async function issueResetToken({ email, sessionId, nowMs, ttlSeconds }, privateKeyB64) {
  const header = bytesToBase64Url(encodeJson({ alg: 'EdDSA', typ: 'RC-RESET', v: 1 }));
  const payload = {
    iss: 'realcoin-password-reset',
    aud: 'realcoin-android',
    sub: email,
    sid: sessionId,
    jti: crypto.randomUUID(),
    iat: Math.floor(nowMs / 1000),
    exp: Math.floor(nowMs / 1000) + ttlSeconds
  };
  const body = bytesToBase64Url(encodeJson(payload));
  const signingInput = `${header}.${body}`;
  const key = await importEd25519PrivateKey(privateKeyB64);
  const signature = await crypto.subtle.sign('Ed25519', key, encoder.encode(signingInput));
  return `${signingInput}.${bytesToBase64Url(signature)}`;
}

export async function verifyResetToken(token, publicKeyB64, nowMs = Date.now()) {
  if (typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  const [headerB64, bodyB64, signatureB64] = parts;
  let header;
  let payload;
  try {
    header = decodeJsonBase64Url(headerB64);
    payload = decodeJsonBase64Url(bodyB64);
  } catch {
    return null;
  }
  if (header.alg !== 'EdDSA' || header.typ !== 'RC-RESET' || header.v !== 1) return null;
  if (payload.iss !== 'realcoin-password-reset' || payload.aud !== 'realcoin-android') return null;
  if (typeof payload.sub !== 'string' || typeof payload.sid !== 'string' ||
      typeof payload.jti !== 'string' || !Number.isInteger(payload.iat) ||
      !Number.isInteger(payload.exp) || payload.exp <= payload.iat ||
      nowMs >= payload.exp * 1000 || payload.iat * 1000 > nowMs + 60_000) return null;
  try {
    const key = await importEd25519PublicKey(publicKeyB64);
    const valid = await crypto.subtle.verify(
      'Ed25519',
      key,
      base64ToBytes(signatureB64),
      encoder.encode(`${headerB64}.${bodyB64}`)
    );
    return valid ? payload : null;
  } catch {
    return null;
  }
}
