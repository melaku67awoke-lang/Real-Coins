const encoder = new TextEncoder();

const PASSWORD_SALT_BYTES = 16;
const PASSWORD_ITERATIONS = 310000;

function bytesToBase64(bytes) {
  let binary = '';
  for (const byte of new Uint8Array(bytes)) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary);
}

function base64ToBytes(value) {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
  const padded =
    normalized + '='.repeat((4 - (normalized.length % 4)) % 4);

  const binary = atob(padded);

  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

export function normalizeEmail(email) {
  return typeof email === 'string'
    ? email.trim().toLowerCase()
    : '';
}

export function isValidEmail(email) {
  return (
    email.length >= 3 &&
    email.length <= 254 &&
    /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
  );
}

export function normalizeUsername(username) {
  return typeof username === 'string'
    ? username.trim().toLowerCase()
    : '';
}

export function isValidUsername(username) {
  return (
    username.length >= 3 &&
    username.length <= 64 &&
    /^[a-z0-9_.-]+$/.test(username)
  );
}

export function randomPasswordSaltB64() {
  const salt = new Uint8Array(PASSWORD_SALT_BYTES);
  crypto.getRandomValues(salt);
  return bytesToBase64(salt);
}

async function derivePasswordKey(password) {
  return crypto.subtle.importKey(
    'raw',
    encoder.encode(password),
    'PBKDF2',
    false,
    ['deriveBits']
  );
}

export async function hashPassword(
  password,
  saltB64 = randomPasswordSaltB64()
) {
  if (
    typeof password !== 'string' ||
    password.length < 8 ||
    password.length > 256
  ) {
    throw new Error('invalid_password');
  }

  const salt = base64ToBytes(saltB64);
  const key = await derivePasswordKey(password);

  const bits = await crypto.subtle.deriveBits(
    {
      name: 'PBKDF2',
      salt,
      iterations: PASSWORD_ITERATIONS,
      hash: 'SHA-256'
    },
    key,
    256
  );

  return {
    hashB64: bytesToBase64(new Uint8Array(bits)),
    saltB64,
    iterations: PASSWORD_ITERATIONS
  };
}

export async function verifyPassword(
  password,
  saltB64,
  expectedHashB64
) {
  if (
    typeof password !== 'string' ||
    password.length < 8 ||
    password.length > 256
  ) {
    return false;
  }

  try {
    const salt = base64ToBytes(saltB64);
    const key = await derivePasswordKey(password);

    const bits = await crypto.subtle.deriveBits(
      {
        name: 'PBKDF2',
        salt,
        iterations: PASSWORD_ITERATIONS,
        hash: 'SHA-256'
      },
      key,
      256
    );

    const actual = new Uint8Array(bits);
    const expected = base64ToBytes(expectedHashB64);

    if (actual.length !== expected.length) {
      return false;
    }

    let difference = 0;

    for (let i = 0; i < actual.length; i++) {
      difference |= actual[i] ^ expected[i];
    }

    return difference === 0;
  } catch {
    return false;
  }
}

export async function hashOpaqueToken(token, secret = '') {
  const data = encoder.encode(token);

  if (secret) {
    const key = await crypto.subtle.importKey(
      'raw',
      encoder.encode(secret),
      {
        name: 'HMAC',
        hash: 'SHA-256'
      },
      false,
      ['sign']
    );

    const digest = await crypto.subtle.sign(
      'HMAC',
      key,
      data
    );

    return bytesToBase64(new Uint8Array(digest));
  }

  const digest = await crypto.subtle.digest(
    'SHA-256',
    data
  );

  return bytesToBase64(new Uint8Array(digest));
}

export function generateSessionToken() {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);

  return bytesToBase64(bytes)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}

export async function constantTimeSecretEqual(left, right) {
  if (typeof left !== 'string' || typeof right !== 'string') return false;
  const leftDigest = new Uint8Array(await crypto.subtle.digest('SHA-256', encoder.encode(left)));
  const rightDigest = new Uint8Array(await crypto.subtle.digest('SHA-256', encoder.encode(right)));
  let difference = 0;
  for (let i = 0; i < leftDigest.length; i++) {
    difference |= leftDigest[i] ^ rightDigest[i];
  }
  return difference === 0;
}
