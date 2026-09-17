import {
  generateOtp,
  hashOpaqueToken,
  hashOtp,
  issueResetToken,
  isValidEmail,
  normalizeEmail,
  randomSaltB64,
  verifyOtp,
  verifyResetToken
} from './security.js';

const OTP_TTL_SECONDS = 600;
const OTP_MAX_ATTEMPTS = 5;
const RESET_TOKEN_TTL_SECONDS = 600;
const REQUEST_WINDOW_MS = 15 * 60 * 1000;
const MAX_REQUESTS_PER_WINDOW = 3;
const MAX_BODY_BYTES = 16 * 1024;

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': 'no-store'
    }
  });
}

function noContent(status = 204) {
  return new Response(null, { status, headers: { 'Cache-Control': 'no-store' } });
}

async function readJson(request) {
  const contentLength = Number(request.headers.get('content-length') || 0);
  if (contentLength > MAX_BODY_BYTES) throw new Error('request too large');
  const text = await request.text();
  if (text.length > MAX_BODY_BYTES) throw new Error('request too large');
  try {
    return text ? JSON.parse(text) : {};
  } catch {
    throw new Error('invalid json');
  }
}

function corsHeaders() {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS'
  };
}

function withCors(response) {
  const headers = new Headers(response.headers);
  for (const [key, value] of Object.entries(corsHeaders())) headers.set(key, value);
  return new Response(response.body, { status: response.status, headers });
}

async function sendOtpEmail(env, to, otp) {
  const response = await fetch('https://api.resend.com/emails', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${env.RESEND_API_KEY}`,
      'Content-Type': 'application/json',
      'User-Agent': 'RealCoin-Password-Reset/1.0'
    },
    body: JSON.stringify({
      from: env.RESEND_FROM_EMAIL,
      to: [to],
      subject: 'RealCoin password reset code',
      text: `Your RealCoin password reset code is ${otp}. It expires in 10 minutes. If you did not request a password reset, ignore this email.`,
      html: `<p>Your RealCoin password reset code is <strong>${otp}</strong>.</p><p>It expires in 10 minutes.</p><p>If you did not request a password reset, ignore this email.</p>`
    })
  });
  if (!response.ok) {
    console.error('Resend API error', response.status, await response.text());
    throw new Error('email delivery failed');
  }
}

async function allowRequest(db, email, nowMs, env) {
  const emailKey = await hashOpaqueToken(email, env.RATE_LIMIT_SECRET);
  const existing = await db.prepare(
    `SELECT window_ends_at_ms AS windowEndsAtMs, request_count AS requestCount
       FROM rate_limits WHERE email_key_b64 = ?1`
  ).bind(emailKey).first();

  if (!existing || nowMs >= Number(existing.windowEndsAtMs)) {
    await db.prepare(
      `INSERT INTO rate_limits(email_key_b64, window_ends_at_ms, request_count)
       VALUES (?1, ?2, 1)
       ON CONFLICT(email_key_b64) DO UPDATE SET window_ends_at_ms=excluded.window_ends_at_ms, request_count=1`
    ).bind(emailKey, nowMs + REQUEST_WINDOW_MS).run();
    return true;
  }

  if (Number(existing.requestCount) >= MAX_REQUESTS_PER_WINDOW) return false;

  const updated = await db.prepare(
    `UPDATE rate_limits SET request_count = request_count + 1
       WHERE email_key_b64 = ?1 AND window_ends_at_ms > ?2 AND request_count < ?3`
  ).bind(emailKey, nowMs, MAX_REQUESTS_PER_WINDOW).run();
  return Number(updated.meta?.changes || 0) === 1;
}

async function createResetSession(db, email, otp, nowMs, env) {
  const saltB64 = randomSaltB64();
  const otpHashB64 = await hashOtp(otp, saltB64, env.OTP_HASH_SECRET);
  const sessionId = crypto.randomUUID();
  const expiresAtMs = nowMs + OTP_TTL_SECONDS * 1000;
  await db.prepare(
    `INSERT INTO reset_sessions
      (id, normalized_email, otp_hash_b64, otp_salt_b64, created_at_ms, expires_at_ms, attempts, max_attempts, verified_at_ms, used_at_ms, reset_token_hash_b64)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6, 0, ?7, NULL, NULL, NULL)`
  ).bind(sessionId, email, otpHashB64, saltB64, nowMs, expiresAtMs, OTP_MAX_ATTEMPTS).run();
  return { sessionId, expiresAtMs };
}

async function claimOtpAttempt(db, sessionId, nowMs) {
  const result = await db.prepare(
    `UPDATE reset_sessions
        SET attempts = attempts + 1
      WHERE id = ?1
        AND used_at_ms IS NULL
        AND verified_at_ms IS NULL
        AND expires_at_ms > ?2
        AND attempts < max_attempts`
  ).bind(sessionId, nowMs).run();
  return Number(result.meta?.changes || 0) === 1;
}

async function loadSession(db, sessionId) {
  return db.prepare(
    `SELECT id, normalized_email AS normalizedEmail, otp_hash_b64 AS otpHashB64,
            otp_salt_b64 AS otpSaltB64, expires_at_ms AS expiresAtMs,
            attempts, max_attempts AS maxAttempts, verified_at_ms AS verifiedAtMs,
            used_at_ms AS usedAtMs, reset_token_hash_b64 AS resetTokenHashB64
       FROM reset_sessions WHERE id = ?1`
  ).bind(sessionId).first();
}

async function markVerified(db, sessionId, tokenHashB64, nowMs) {
  const result = await db.prepare(
    `UPDATE reset_sessions
        SET verified_at_ms = ?1, reset_token_hash_b64 = ?2
      WHERE id = ?3
        AND verified_at_ms IS NULL
        AND used_at_ms IS NULL
        AND expires_at_ms > ?1`
  ).bind(nowMs, tokenHashB64, sessionId).run();
  return Number(result.meta?.changes || 0) === 1;
}

async function consumeSession(db, sessionId, tokenHashB64, nowMs) {
  const result = await db.prepare(
    `UPDATE reset_sessions
        SET used_at_ms = ?1
      WHERE id = ?2
        AND verified_at_ms IS NOT NULL
        AND used_at_ms IS NULL
        AND expires_at_ms > ?1
        AND reset_token_hash_b64 = ?3`
  ).bind(nowMs, sessionId, tokenHashB64).run();
  return Number(result.meta?.changes || 0) === 1;
}

async function handle(request, env) {
  const url = new URL(request.url);

  if (request.method === 'OPTIONS') return noContent();
  if (request.method === 'GET' && url.pathname === '/health') return json({ ok: true });
  if (request.method !== 'POST') return json({ error: 'method_not_allowed' }, 405);

  if (url.pathname === '/v1/password-reset/request') {
    const body = await readJson(request);
    const email = normalizeEmail(body.email);
    if (!isValidEmail(email)) return json({ error: 'invalid_email' }, 400);

    if (!await allowRequest(env.DB, email, Date.now(), env)) {
      return json({ error: 'too_many_requests' }, 429);
    }

    const otp = generateOtp();
    const { sessionId, expiresAtMs } = await createResetSession(env.DB, email, otp, Date.now(), env);

    try {
      await sendOtpEmail(env, email, otp);
    } catch {
      // Do not expose provider details. The session expires naturally and the client gets a generic failure.
      return json({ error: 'email_delivery_failed' }, 503);
    }

    return json({ ok: true, resetSessionId: sessionId, expiresAtMs });
  }

  if (url.pathname === '/v1/password-reset/verify') {
    const body = await readJson(request);
    const sessionId = typeof body.resetSessionId === 'string' ? body.resetSessionId.trim() : '';
    const otp = typeof body.otp === 'string' ? body.otp : '';
    if (!sessionId || !/^\d{6}$/.test(otp)) return json({ error: 'invalid_request' }, 400);

    const nowMs = Date.now();
    const claimed = await claimOtpAttempt(env.DB, sessionId, nowMs);
    if (!claimed) return json({ error: 'otp_expired_or_invalid' }, 400);

    const session = await loadSession(env.DB, sessionId);
    if (!session || Number(session.expiresAtMs) <= nowMs) {
      return json({ error: 'otp_expired_or_invalid' }, 400);
    }

    const valid = await verifyOtp(otp, session.otpSaltB64, session.otpHashB64, env.OTP_HASH_SECRET);
    if (!valid) {
      return json({ error: Number(session.attempts) >= Number(session.maxAttempts) ? 'too_many_attempts' : 'otp_expired_or_invalid' }, 400);
    }

    const token = await issueResetToken({
      email: session.normalizedEmail,
      sessionId,
      nowMs,
      ttlSeconds: RESET_TOKEN_TTL_SECONDS
    }, env.RESET_TOKEN_PRIVATE_KEY_PKCS8_B64);
    const tokenHashB64 = await hashOpaqueToken(token, env.RATE_LIMIT_SECRET);
    const marked = await markVerified(env.DB, sessionId, tokenHashB64, nowMs);
    if (!marked) return json({ error: 'otp_expired_or_invalid' }, 400);

    return json({ ok: true, resetAuthorization: token, expiresInSeconds: RESET_TOKEN_TTL_SECONDS });
  }

  if (url.pathname === '/v1/password-reset/consume') {
    const body = await readJson(request);
    const token = typeof body.resetAuthorization === 'string' ? body.resetAuthorization.trim() : '';
    if (!token) return json({ error: 'invalid_request' }, 400);

    const payload = await verifyResetToken(token, env.RESET_TOKEN_PUBLIC_KEY_SPKI_B64, Date.now());
    if (!payload) return json({ error: 'invalid_authorization' }, 400);

    const tokenHashB64 = await hashOpaqueToken(token, env.RATE_LIMIT_SECRET);
    const consumed = await consumeSession(env.DB, payload.sid, tokenHashB64, Date.now());
    if (!consumed) return json({ error: 'authorization_expired_or_used' }, 400);

    return json({ ok: true, email: payload.sub });
  }

  return json({ error: 'not_found' }, 404);
}

export default {
  async fetch(request, env) {
    try {
      return withCors(await handle(request, env));
    } catch (error) {
      console.error(error);
      return withCors(json({ error: 'internal_server_error' }, 500));
    }
  }
};
