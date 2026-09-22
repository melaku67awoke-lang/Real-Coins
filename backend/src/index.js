import {
  generateSessionToken,
  constantTimeSecretEqual,
  hashOpaqueToken,
  hashPassword,
  isValidEmail,
  isValidUsername,
  normalizeEmail,
  normalizeUsername,
  verifyPassword
} from './security.js';

const REQUEST_WINDOW_MS = 15 * 60 * 1000;
const MAX_REQUESTS_PER_WINDOW = 3;
const MAX_BODY_BYTES = 16 * 1024;
const MAX_ATTACHMENT_BYTES = 2 * 1024 * 1024;
const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const P2P_ORDER_TTL_MS = 20 * 60 * 1000;
const ADMIN_BOOTSTRAP_HEADER = 'x-admin-bootstrap-secret';
const IDEMPOTENCY_KEY_MAX = 128;
const LOGIN_WINDOW_MS = 15 * 60 * 1000;
const MAX_LOGIN_ATTEMPTS = 8;

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
  return new Response(null, {
    status,
    headers: { 'Cache-Control': 'no-store' }
  });
}

function corsHeaders() {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers':
      'Content-Type, Authorization, Idempotency-Key',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS'
  };
}

function withCors(response) {
  const headers = new Headers(response.headers);

  for (const [key, value] of Object.entries(corsHeaders())) {
    headers.set(key, value);
  }

  return new Response(response.body, {
    status: response.status,
    headers
  });
}

async function readJson(request) {
  const contentLength = Number(
    request.headers.get('content-length') || 0
  );

  if (contentLength > MAX_BODY_BYTES) {
    throw new Error('request too large');
  }

  const text = await request.text();

  if (text.length > MAX_BODY_BYTES) {
    throw new Error('request too large');
  }

  try {
    return text ? JSON.parse(text) : {};
  } catch {
    throw new Error('invalid json');
  }
}

async function readJsonWithLimit(request, maxBytes) {
  const contentLength = Number(
    request.headers.get('content-length') || 0
  );

  if (contentLength > maxBytes) {
    throw new Error('request too large');
  }

  const textBody = await request.text();

  if (textBody.length > maxBytes) {
    throw new Error('request too large');
  }

  try {
    return textBody ? JSON.parse(textBody) : {};
  } catch {
    throw new Error('invalid json');
  }
}

function positiveNumber(value) {
  return (
    typeof value === 'number' &&
    Number.isFinite(value) &&
    value > 0
  );
}

function nonNegativeNumber(value) {
  return (
    typeof value === 'number' &&
    Number.isFinite(value) &&
    value >= 0
  );
}

function text(value, max) {
  return typeof value === 'string' &&
    value.trim().length <= max
    ? value.trim()
    : '';
}

async function isOwnedAttachmentUrl(
  db,
  request,
  accountId,
  value
) {
  if (!value || !value.startsWith('https://')) {
    return false;
  }

  let parsed;

  try {
    parsed = new URL(value);
  } catch {
    return false;
  }

  const expectedOrigin = new URL(request.url).origin;

  const match = parsed.pathname.match(
    /^\/v1\/p2p\/attachments\/([^/]+)$/
  );

  if (
    !match ||
    parsed.origin !== expectedOrigin ||
    parsed.search ||
    parsed.hash
  ) {
    return false;
  }

  const row = await db.prepare(
    `SELECT id
       FROM p2p_attachments
      WHERE id = ?1
        AND owner_account_id = ?2`
  ).bind(
    match[1],
    accountId
  ).first();

  return Boolean(row);
}

async function allowRequest(db, email, nowMs, env) {
  const emailKey = await hashOpaqueToken(
    email,
    env.RATE_LIMIT_SECRET || ''
  );

  const existing = await db.prepare(
    `SELECT
       window_ends_at_ms AS windowEndsAtMs,
       request_count AS requestCount
       FROM rate_limits
      WHERE email_key_b64 = ?1`
  ).bind(emailKey).first();

  if (
    !existing ||
    nowMs >= Number(existing.windowEndsAtMs)
  ) {
    await db.prepare(
      `INSERT INTO rate_limits(
         email_key_b64,
         window_ends_at_ms,
         request_count
       )
       VALUES (?1, ?2, 1)
       ON CONFLICT(email_key_b64)
       DO UPDATE SET
         window_ends_at_ms = excluded.window_ends_at_ms,
         request_count = 1`
    ).bind(
      emailKey,
      nowMs + REQUEST_WINDOW_MS
    ).run();

    return true;
  }

  if (
    Number(existing.requestCount) >=
    MAX_REQUESTS_PER_WINDOW
  ) {
    return false;
  }

  const updated = await db.prepare(
    `UPDATE rate_limits
        SET request_count = request_count + 1
      WHERE email_key_b64 = ?1
        AND window_ends_at_ms > ?2
        AND request_count < ?3`
  ).bind(
    emailKey,
    nowMs,
    MAX_REQUESTS_PER_WINDOW
  ).run();

  return Number(updated.meta?.changes || 0) === 1;
}

async function allowLoginAttempt(
  db,
  login,
  request,
  nowMs,
  env
) {
  const clientIp =
    request.headers.get('CF-Connecting-IP') ||
    request.headers.get('X-Forwarded-For') ||
    '';

  const key = `${login}|${clientIp}`;

  const keyB64 = await hashOpaqueToken(
    key,
    env.RATE_LIMIT_SECRET || ''
  );

  const existing = await db.prepare(
    `SELECT
       window_ends_at_ms AS windowEndsAtMs,
       attempt_count AS attemptCount
       FROM auth_login_rate_limits
      WHERE key_b64 = ?1`
  ).bind(keyB64).first();

  if (
    !existing ||
    nowMs >= Number(existing.windowEndsAtMs)
  ) {
    await db.prepare(
      `INSERT INTO auth_login_rate_limits(
         key_b64,
         window_ends_at_ms,
         attempt_count
       )
       VALUES (?1, ?2, 1)
       ON CONFLICT(key_b64)
       DO UPDATE SET
         window_ends_at_ms = excluded.window_ends_at_ms,
         attempt_count = 1`
    ).bind(
      keyB64,
      nowMs + LOGIN_WINDOW_MS
    ).run();

    return true;
  }

  if (
    Number(existing.attemptCount) >=
    MAX_LOGIN_ATTEMPTS
  ) {
    return false;
  }

  const updated = await db.prepare(
    `UPDATE auth_login_rate_limits
        SET attempt_count = attempt_count + 1
      WHERE key_b64 = ?1
        AND window_ends_at_ms > ?2
        AND attempt_count < ?3`
  ).bind(
    keyB64,
    nowMs,
    MAX_LOGIN_ATTEMPTS
  ).run();

  return Number(updated.meta?.changes || 0) === 1;
}

async function createSession(
  db,
  accountId,
  env,
  nowMs = Date.now()
) {
  const token = generateSessionToken();

  const tokenHashB64 = await hashOpaqueToken(
    token,
    env.RATE_LIMIT_SECRET || ''
  );

  const id = crypto.randomUUID();

  await db.prepare(
    `INSERT INTO auth_sessions(
       id,
       account_id,
       token_hash_b64,
       created_at_ms,
       expires_at_ms,
       revoked_at_ms
     )
     VALUES (?1, ?2, ?3, ?4, ?5, NULL)`
  ).bind(
    id,
    accountId,
    tokenHashB64,
    nowMs,
    nowMs + SESSION_TTL_MS
  ).run();

  return token;
}

async function authenticate(request, env) {
  const header =
    request.headers.get('authorization') || '';

  if (!header.startsWith('Bearer ')) {
    return null;
  }

  const token = header.slice(7).trim();

  if (!token || token.length > 256) {
    return null;
  }

  const tokenHashB64 = await hashOpaqueToken(
    token,
    env.RATE_LIMIT_SECRET || ''
  );

  return env.DB.prepare(
    `SELECT
       a.id,
       a.username,
       a.normalized_email AS email,
       a.role,
       a.referral_code
       FROM auth_sessions s
       JOIN auth_accounts a
         ON a.id = s.account_id
      WHERE s.token_hash_b64 = ?1
        AND s.revoked_at_ms IS NULL
        AND s.expires_at_ms > ?2
        AND a.disabled_at_ms IS NULL`
  ).bind(
    tokenHashB64,
    Date.now()
  ).first();
}

function accountJson(account) {
  return {
    id: account.id,
    username: account.username,
    email: account.email,
    role: account.role,
    referralCode:
      account.referral_code ||
      account.referralCode ||
      null
  };
}

function makeReferralCode(username) {
  const base = username
    .replace(/[^a-z0-9]/gi, '')
    .toUpperCase()
    .slice(0, 8) || 'REAL';

  return `RC-${base}-${crypto.randomUUID()
    .replace(/-/g, '')
    .slice(0, 6)
    .toUpperCase()}`;
}

async function requireAuth(request, env) {
  const account = await authenticate(request, env);

  if (!account) {
    return {
      response: json(
        { error: 'unauthorized' },
        401
      )
    };
  }

  return { account };
}

async function requireAdmin(request, env) {
  const auth = await requireAuth(request, env);

  if (auth.response) {
    return auth;
  }

  if (auth.account.role !== 'ADMIN') {
    return {
      response: json(
        { error: 'admin_required' },
        403
      )
    };
  }

  return auth;
}

async function getWallet(db, accountId) {
  const row = await db.prepare(
    `SELECT
       real_balance AS realBalance,
       real_locked_balance AS realLockedBalance
       FROM p2p_wallets
      WHERE account_id = ?1`
  ).bind(accountId).first();

  return row || {
    realBalance: 0,
    realLockedBalance: 0
  };
}

async function ensureWallet(
  db,
  accountId,
  nowMs = Date.now()
) {
  await db.prepare(
    `INSERT INTO p2p_wallets(
       account_id,
       real_balance,
       real_locked_balance,
       updated_at_ms
     )
     VALUES (?1, 0, 0, ?2)
     ON CONFLICT(account_id) DO NOTHING`
  ).bind(
    accountId,
    nowMs
  ).run();
}

async function getAd(db, adId) {
  return db.prepare(
    `SELECT
       id,
       seller_id AS sellerId,
       seller_name AS sellerName,
       type,
       crypto_amount AS cryptoAmount,
       fiat_price AS fiatPrice,
       (crypto_amount * fiat_price) AS fiatOrderAmount,
       fiat_currency AS fiatCurrency,
       payment_method AS paymentMethod,
       payment_name AS paymentName,
       account_number AS accountNumber,
       is_active AS isActive,
       min_order_etb AS minOrderEtb,
       max_order_etb AS maxOrderEtb,
       original_max_order_etb AS originalMaxOrderEtb,
       created_at_ms AS createdAt
       FROM p2p_ads
      WHERE id = ?1`
  ).bind(adId).first();
}

async function getOrder(db, orderId) {
  return db.prepare(
    `SELECT
       id,
       ad_id AS adId,
       seller_id AS sellerId,
       seller_name AS sellerName,
       buyer_id AS buyerId,
       buyer_name AS buyerName,
       crypto_amount AS cryptoAmount,
       fiat_price AS fiatPrice,
       fiat_order_amount AS fiatOrderAmount,
       fiat_currency AS fiatCurrency,
       payment_method AS paymentMethod,
       payment_name AS paymentName,
       account_number AS accountNumber,
       payment_proof_url AS paymentProofUrl,
       paid_at_ms AS paidAt,
       status,
       created_at_ms AS createdAt,
       expires_at_ms AS expiresAt,
       completed_at_ms AS completedAt,
       dispute_reason AS disputeReason,
       disputed_at_ms AS disputedAt,
       resolved_at_ms AS resolvedAt,
       resolved_by_admin_id AS resolvedByAdminId
       FROM p2p_orders
      WHERE id = ?1`
  ).bind(orderId).first();
}

async function expireOrder(db, order) {
  const nowMs = Date.now();

  if (
    !order ||
    order.status !== 'ESCROW_LOCKED' ||
    Number(order.expiresAt) > nowMs
  ) {
    return false;
  }

  try {
    const cancelled = await db.prepare(
      `UPDATE p2p_orders
          SET status = 'CANCELLED'
        WHERE id = ?1
          AND status = 'ESCROW_LOCKED'
          AND expires_at_ms <= ?2`
    ).bind(
      order.id,
      nowMs
    ).run();

    return Number(
      cancelled.meta?.changes || 0
    ) === 1;
  } catch {
    return false;
  }
}

async function handle(request, env) {
  const url = new URL(request.url);
  const path = url.pathname;

  if (request.method === 'OPTIONS') {
    return noContent();
  }

  if (
    request.method === 'GET' &&
    path === '/health'
  ) {
    return json({ ok: true });
  }

  if (
    request.method === 'GET' &&
    path === '/v1/config/pricing'
  ) {
    const row = await env.DB.prepare(
      `SELECT value
         FROM app_price_settings
        WHERE key = 'REAL_COIN_USD_VALUE'`
    ).first();

    return json({
      ok: true,
      realCoinUsdPrice:
        Number(row?.value || 0.0027),
      usdToEtbRate: 186
    });
  }

  if (
    request.method === 'POST' &&
    path === '/v1/admin/pricing'
  ) {
    const auth = await requireAdmin(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const body = await readJson(request);
    const price = Number(
      body.realCoinUsdPrice
    );

    if (
      !Number.isFinite(price) ||
      price <= 0 ||
      price > 1000
    ) {
      return json(
        { error: 'invalid_price' },
        400
      );
    }

    const nowMs = Date.now();

    const result = await env.DB.batch([
      env.DB.prepare(
        `INSERT INTO app_price_settings(
           key,
           value,
           updated_at_ms,
           updated_by_admin_id
         )
         VALUES (
           'REAL_COIN_USD_VALUE',
           ?1,
           ?2,
           ?3
         )
         ON CONFLICT(key)
         DO UPDATE SET
           value = excluded.value,
           updated_at_ms = excluded.updated_at_ms,
           updated_by_admin_id =
             excluded.updated_by_admin_id`
      ).bind(
        price,
        nowMs,
        auth.account.id
      ),

      env.DB.prepare(
        `INSERT INTO admin_audit_log(
           id,
           admin_account_id,
           action,
           operation,
           amount,
           reason,
           created_at_ms
         )
         VALUES (
           ?1,
           ?2,
           'RC_PRICE_CHANGE',
           'SET_RC_USD_PRICE',
           ?3,
           ?4,
           ?5
         )`
      ).bind(
        crypto.randomUUID(),
        auth.account.id,
        price,
        'Admin changed REAL/USD price',
        nowMs
      )
    ]);

    if (
      Number(result[0]?.meta?.changes || 0) !== 1
    ) {
      return json(
        { error: 'price_update_failed' },
        500
      );
    }

    return json({
      ok: true,
      realCoinUsdPrice: price,
      usdToEtbRate: 186
    });
  }

  if (
    request.method === 'POST' &&
    path === '/v1/admin/bootstrap'
  ) {
    const configuredSecret =
      typeof env.ADMIN_BOOTSTRAP_SECRET === 'string'
        ? env.ADMIN_BOOTSTRAP_SECRET
        : '';

    const suppliedSecret =
      request.headers.get(
        ADMIN_BOOTSTRAP_HEADER
      ) || '';

    const secretMatches =
      configuredSecret &&
      suppliedSecret.length >= 16 &&
      await constantTimeSecretEqual(
        suppliedSecret,
        configuredSecret
      );

    if (!secretMatches) {
      return json(
        { error: 'bootstrap_unauthorized' },
        401
      );
    }

    const body = await readJson(request);
    const email = normalizeEmail(body.email);

    if (!isValidEmail(email)) {
      return json(
        { error: 'invalid_email' },
        400
      );
    }

    const result = await env.DB.prepare(
      `UPDATE auth_accounts
          SET role = 'ADMIN',
              updated_at_ms = ?1
        WHERE normalized_email = ?2
          AND role = 'USER'
          AND disabled_at_ms IS NULL
          AND NOT EXISTS (
            SELECT 1
              FROM auth_accounts
             WHERE role = 'ADMIN'
          )`
    ).bind(
      Date.now(),
      email
    ).run();

    if (
      Number(result.meta?.changes || 0) !== 1
    ) {
      return json(
        { error: 'bootstrap_not_available' },
        409
      );
    }

    return json({
      ok: true,
      role: 'ADMIN'
    });
  }

  if (
    request.method === 'POST' &&
    path === '/v1/auth/register'
  ) {
    const body = await readJson(request);

    const username = normalizeUsername(
      body.username
    );

    const email = normalizeEmail(
      body.email
    );

    const password =
      typeof body.password === 'string'
        ? body.password
        : '';

    const referralCode =
      text(body.referralCode, 64)
        .toUpperCase();

    if (
      !isValidUsername(username) ||
      !isValidEmail(email) ||
      password.length < 8 ||
      password.length > 256
    ) {
      return json(
        { error: 'invalid_registration' },
        400
      );
    }

    const existing = await env.DB.prepare(
      `SELECT id
         FROM auth_accounts
        WHERE username = ?1
           OR normalized_email = ?2
        LIMIT 1`
    ).bind(
      username,
      email
    ).first();

    if (existing) {
      return json(
        { error: 'account_exists' },
        409
      );
    }

    let referrer = null;

    if (referralCode) {
      referrer = await env.DB.prepare(
        `SELECT id
           FROM auth_accounts
          WHERE referral_code = ?1
          LIMIT 1`
      ).bind(referralCode).first();

      if (!referrer) {
        return json(
          { error: 'invalid_referral_code' },
          400
        );
      }
    }

    const passwordData =
      await hashPassword(password);

    const id =
      `RC_ACCOUNT_${crypto.randomUUID()}`;

    const nowMs = Date.now();

    const ownReferralCode =
      makeReferralCode(username);

    await env.DB.prepare(
      `INSERT INTO auth_accounts(
         id,
         username,
         normalized_email,
         password_hash_b64,
         password_salt_b64,
         role,
         created_at_ms,
         updated_at_ms,
         disabled_at_ms,
         referral_code
       )
       VALUES (
         ?1,
         ?2,
         ?3,
         ?4,
         ?5,
         'USER',
         ?6,
         ?6,
         NULL,
         ?7
       )`
    ).bind(
      id,
      username,
      email,
      passwordData.hashB64,
      passwordData.saltB64,
      nowMs,
      ownReferralCode
    ).run();

    if (
      referrer &&
      referrer.id !== id
    ) {
      await env.DB.prepare(
        `INSERT INTO referrals(
           referred_account_id,
           referrer_account_id,
           referral_code,
           created_at_ms
         )
         VALUES (?1, ?2, ?3, ?4)`
      ).bind(
        id,
        referrer.id,
        referralCode,
        nowMs
      ).run();
    }

    await ensureWallet(
      env.DB,
      id,
      nowMs
    );

    const token = await createSession(
      env.DB,
      id,
      env,
      nowMs
    );

    return json({
      ok: true,
      account: {
        id,
        username,
        email,
        role: 'USER',
        referralCode: ownReferralCode
      },
      sessionToken: token
    });
  }

  /*
   * LOGIN
   *
   * FIX:
   * Android sends:
   * {
   *   email: "...",
   *   password: "..."
   * }
   *
   * The Worker previously required body.login.
   * It now accepts login, email, or username.
   */
  if (
    request.method === 'POST' &&
    path === '/v1/auth/login'
  ) {
    const body = await readJson(request);

    const login = text(
      body.login ||
      body.email ||
      body.username,
      254
    ).toLowerCase();

    const password =
      typeof body.password === 'string'
        ? body.password
        : '';

    if (
      !login ||
      password.length < 8 ||
      password.length > 256
    ) {
      return json(
        { error: 'invalid_credentials' },
        400
      );
    }

    if (
      !await allowLoginAttempt(
        env.DB,
        login,
        request,
        Date.now(),
        env
      )
    ) {
      return json(
        { error: 'too_many_attempts' },
        429
      );
    }

    const account = await env.DB.prepare(
      `SELECT
         id,
         username,
         normalized_email AS email,
         password_hash_b64 AS passwordHashB64,
         password_salt_b64 AS passwordSaltB64,
         role,
         referral_code
         FROM auth_accounts
        WHERE (
          username = ?1
          OR normalized_email = ?1
        )
          AND disabled_at_ms IS NULL
        LIMIT 1`
    ).bind(login).first();

    if (
      !account ||
      !await verifyPassword(
        password,
        account.passwordSaltB64,
        account.passwordHashB64
      )
    ) {
      return json(
        { error: 'invalid_credentials' },
        401
      );
    }

    await ensureWallet(
      env.DB,
      account.id
    );

    const token = await createSession(
      env.DB,
      account.id,
      env
    );

    return json({
      ok: true,
      account: accountJson(account),
      sessionToken: token
    });
  }

  if (
    request.method === 'POST' &&
    path === '/v1/auth/logout'
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const header =
      request.headers.get('authorization') || '';

    const tokenHashB64 =
      await hashOpaqueToken(
        header.slice(7).trim(),
        env.RATE_LIMIT_SECRET || ''
      );

    await env.DB.prepare(
      `UPDATE auth_sessions
          SET revoked_at_ms = ?1
        WHERE token_hash_b64 = ?2`
    ).bind(
      Date.now(),
      tokenHashB64
    ).run();

    return json({ ok: true });
  }

  if (
    request.method === 'GET' &&
    path === '/v1/auth/me'
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    return json({
      ok: true,
      account: accountJson(auth.account)
    });
  }

  // KYC: server-authoritative so users and admins on different devices share one record.
  if (request.method === 'POST' && path === '/v1/kyc') {
    const auth = await requireAuth(request, env);
    if (auth.response) return auth.response;
    const body = await readJson(request);
    const fullName = text(body.fullName, 200), idType = text(body.idType, 64), idNumber = text(body.idNumber, 128);
    const frontIdUrl = text(body.frontIdUrl, 1024), backIdUrl = text(body.backIdUrl, 1024);
    if (!fullName || !idType || !idNumber || !frontIdUrl || !backIdUrl) return json({ error: 'invalid_kyc_submission' }, 400);
    if (!(await isOwnedAttachmentUrl(env.DB, request, auth.account.id, frontIdUrl)) || !(await isOwnedAttachmentUrl(env.DB, request, auth.account.id, backIdUrl))) return json({ error: 'kyc_documents_not_owned' }, 403);
    const existing = await env.DB.prepare(`SELECT status FROM kyc_records WHERE user_account_id = ?1 LIMIT 1`).bind(auth.account.id).first();
    if (existing?.status === 'VERIFIED') return json({ error: 'kyc_already_verified' }, 409);
    if (existing?.status === 'PENDING') return json({ error: 'kyc_already_pending' }, 409);
    const nowMs = Date.now();
    await env.DB.prepare(`INSERT INTO kyc_records(user_account_id,full_name,id_type,id_number,front_id_url,back_id_url,status,submitted_at_ms,reviewed_at_ms,reviewed_by_admin_id,rejection_reason) VALUES (?1,?2,?3,?4,?5,?6,'PENDING',?7,NULL,NULL,NULL) ON CONFLICT(user_account_id) DO UPDATE SET full_name=excluded.full_name,id_type=excluded.id_type,id_number=excluded.id_number,front_id_url=excluded.front_id_url,back_id_url=excluded.back_id_url,status='PENDING',submitted_at_ms=excluded.submitted_at_ms,reviewed_at_ms=NULL,reviewed_by_admin_id=NULL,rejection_reason=NULL`).bind(auth.account.id,fullName,idType,idNumber,frontIdUrl,backIdUrl,nowMs).run();
    const row = await env.DB.prepare(`SELECT user_account_id AS accountId,full_name AS fullName,id_type AS idType,id_number AS idNumber,front_id_url AS frontIdUrl,back_id_url AS backIdUrl,status,submitted_at_ms AS submittedAt,reviewed_at_ms AS reviewedAt,reviewed_by_admin_id AS reviewedByAdminId,rejection_reason AS rejectionReason FROM kyc_records WHERE user_account_id=?1`).bind(auth.account.id).first();
    return json({ok:true,kyc:row ? {...row,username:auth.account.username,email:auth.account.email} : null},201);
  }
  if (request.method === 'GET' && path === '/v1/kyc/me') {
    const auth = await requireAuth(request, env); if (auth.response) return auth.response;
    const row = await env.DB.prepare(`SELECT user_account_id AS accountId,full_name AS fullName,id_type AS idType,id_number AS idNumber,front_id_url AS frontIdUrl,back_id_url AS backIdUrl,status,submitted_at_ms AS submittedAt,reviewed_at_ms AS reviewedAt,reviewed_by_admin_id AS reviewedByAdminId,rejection_reason AS rejectionReason FROM kyc_records WHERE user_account_id=?1 LIMIT 1`).bind(auth.account.id).first();
    return json({ok:true,kyc:row ? {...row,username:auth.account.username,email:auth.account.email} : null});
  }
  if (request.method === 'GET' && path === '/v1/admin/kyc/pending') {
    const auth = await requireAuth(request, env); if (auth.response) return auth.response;
    if (auth.account.role !== 'ADMIN') return json({error:'forbidden'},403);
    const rows = await env.DB.prepare(`SELECT k.user_account_id AS accountId,a.username,a.normalized_email AS email,k.full_name AS fullName,k.id_type AS idType,k.id_number AS idNumber,k.front_id_url AS frontIdUrl,k.back_id_url AS backIdUrl,k.status,k.submitted_at_ms AS submittedAt,k.reviewed_at_ms AS reviewedAt,k.reviewed_by_admin_id AS reviewedByAdminId,k.rejection_reason AS rejectionReason FROM kyc_records k JOIN auth_accounts a ON a.id=k.user_account_id WHERE k.status='PENDING' ORDER BY k.submitted_at_ms ASC`).all();
    return json({ok:true,kycs:rows.results||[]});
  }
  const kycReviewMatch = path.match(/^\/v1\/admin\/kyc\/([^/]+)\/review$/);
  if (request.method === 'POST' && kycReviewMatch) {
    const auth = await requireAuth(request, env); if (auth.response) return auth.response;
    if (auth.account.role !== 'ADMIN') return json({error:'forbidden'},403);
    const targetUserId=kycReviewMatch[1]; if (targetUserId===auth.account.id) return json({error:'cannot_review_self'},403);
    const body=await readJson(request), approve=body.approve===true, reason=text(body.reason,500)||null;
    const existing=await env.DB.prepare(`SELECT status FROM kyc_records WHERE user_account_id=?1 LIMIT 1`).bind(targetUserId).first();
    if(!existing) return json({error:'kyc_not_found'},404); if(existing.status!=='PENDING') return json({error:'kyc_not_pending'},409);
    const nowMs=Date.now(), newStatus=approve?'VERIFIED':'REJECTED';
    await env.DB.batch([
      env.DB.prepare(`UPDATE kyc_records SET status=?1,reviewed_at_ms=?2,reviewed_by_admin_id=?3,rejection_reason=?4 WHERE user_account_id=?5 AND status='PENDING'`).bind(newStatus,nowMs,auth.account.id,approve?null:reason,targetUserId),
      env.DB.prepare(`INSERT INTO kyc_audit_logs(id,user_account_id,admin_account_id,action,notes,timestamp_ms) VALUES (?1,?2,?3,?4,?5,?6)`).bind(crypto.randomUUID(),targetUserId,auth.account.id,approve?'APPROVED':'REJECTED',reason||'',nowMs)
    ]);
    const row=await env.DB.prepare(`SELECT k.user_account_id AS accountId,a.username,a.normalized_email AS email,k.full_name AS fullName,k.id_type AS idType,k.id_number AS idNumber,k.front_id_url AS frontIdUrl,k.back_id_url AS backIdUrl,k.status,k.submitted_at_ms AS submittedAt,k.reviewed_at_ms AS reviewedAt,k.reviewed_by_admin_id AS reviewedByAdminId,k.rejection_reason AS rejectionReason FROM kyc_records k JOIN auth_accounts a ON a.id=k.user_account_id WHERE k.user_account_id=?1`).bind(targetUserId).first();
    return json({ok:true,kyc:row});
  }
  const kycDocMatch=path.match(/^\/v1\/admin\/kyc\/([^/]+)\/document\/(front|back)$/);
  if(request.method==='GET'&&kycDocMatch){
    const auth=await requireAuth(request,env); if(auth.response)return auth.response; if(auth.account.role!=='ADMIN')return json({error:'forbidden'},403);
    const target=kycDocMatch[1], side=kycDocMatch[2];
    const column=side==='front'?'front_id_url':'back_id_url';
    const row=await env.DB.prepare(`SELECT ${column} AS url FROM kyc_records WHERE user_account_id=?1 LIMIT 1`).bind(target).first();
    if(!row?.url)return json({error:'document_not_found'},404);
    const id=String(row.url).match(/\/v1\/p2p\/attachments\/([^/]+)$/)?.[1]; if(!id)return json({error:'invalid_document_url'},500);
    const file=await env.DB.prepare(`SELECT content_type AS contentType,data_base64 AS dataBase64 FROM p2p_attachments WHERE id=?1 LIMIT 1`).bind(id).first();
    if(!file)return json({error:'document_not_found'},404); return json({ok:true,contentType:file.contentType,dataBase64:file.dataBase64});
  }

  if (
    request.method === 'GET' &&
    path === '/v1/referral'
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const account = await env.DB.prepare(
      `SELECT referral_code AS referralCode
         FROM auth_accounts
        WHERE id = ?1`
    ).bind(auth.account.id).first();

    const count = await env.DB.prepare(
      `SELECT COUNT(*) AS count
         FROM referrals
        WHERE referrer_account_id = ?1`
    ).bind(auth.account.id).first();

    const reward = await env.DB.prepare(
      `SELECT 1
         FROM referrals
        WHERE referrer_account_id = ?1
          AND first_deposit_rewarded_at_ms
              IS NOT NULL
        LIMIT 1`
    ).bind(auth.account.id).first();

    return json({
      ok: true,
      referralCode:
        account?.referralCode || null,
      referredCount:
        Number(count?.count || 0),
      rewardClaimed:
        Boolean(reward)
    });
  }

  if (
    request.method === 'GET' &&
    path === '/v1/p2p/wallet'
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    await ensureWallet(
      env.DB,
      auth.account.id
    );

    const wallet = await getWallet(
      env.DB,
      auth.account.id
    );

    return json({
      ok: true,
      ...wallet
    });
  }

  if (
    request.method === 'POST' &&
    path === '/v1/admin/p2p/wallet-adjustment'
  ) {
    const auth = await requireAdmin(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const body = await readJson(request);

    const accountId = text(
      body.accountId,
      128
    );

    const amount = body.amount;

    const reason = text(
      body.reason,
      500
    );

    const kind =
      body.operation === 'DEBIT'
        ? 'DEBIT'
        : body.operation === 'CREDIT'
          ? 'CREDIT'
          : '';

    const idempotencyKey = text(
      request.headers.get('Idempotency-Key') ||
      body.idempotencyKey,
      IDEMPOTENCY_KEY_MAX
    );

    if (
      !accountId ||
      !positiveNumber(amount) ||
      !reason ||
      !kind ||
      !idempotencyKey
    ) {
      return json(
        { error: 'invalid_wallet_adjustment' },
        400
      );
    }

    if (
      !/^[A-Za-z0-9._:-]{8,128}$/.test(
        idempotencyKey
      )
    ) {
      return json(
        { error: 'invalid_idempotency_key' },
        400
      );
    }

    if (amount > 1000000000) {
      return json(
        { error: 'amount_too_large' },
        400
      );
    }

    const account = await env.DB.prepare(
      `SELECT id
         FROM auth_accounts
        WHERE id = ?1
          AND disabled_at_ms IS NULL`
    ).bind(accountId).first();

    if (!account) {
      return json(
        { error: 'account_not_found' },
        404
      );
    }

    const existingAdjustment =
      await env.DB.prepare(
        `SELECT
           operation,
           amount,
           reason,
           idempotency_key AS idempotencyKey
           FROM p2p_wallet_ledger
          WHERE account_id = ?1
            AND idempotency_key = ?2`
      ).bind(
        accountId,
        idempotencyKey
      ).first();

    if (existingAdjustment) {
      if (
        existingAdjustment.operation !== kind ||
        Number(existingAdjustment.amount) !==
          Number(amount) ||
        existingAdjustment.reason !== reason
      ) {
        return json(
          { error: 'idempotency_key_reused' },
          409
        );
      }

      return json({
        ok: true,
        operation: kind,
        idempotencyKey,
        replay: true,
        ...(await getWallet(
          env.DB,
          accountId
        ))
      });
    }

    await ensureWallet(
      env.DB,
      accountId
    );

    const nowMs = Date.now();

    const delta =
      kind === 'CREDIT'
        ? amount
        : -amount;

    const updateSql =
      kind === 'CREDIT'
        ? `UPDATE p2p_wallets
              SET real_balance =
                    real_balance + ?1,
                  updated_at_ms = ?2
            WHERE account_id = ?3`
        : `UPDATE p2p_wallets
              SET real_balance =
                    real_balance - ?1,
                  updated_at_ms = ?2
            WHERE account_id = ?3
              AND real_balance -
                  real_locked_balance >= ?1`;

    try {
      const results = await env.DB.batch([
        env.DB.prepare(updateSql).bind(
          amount,
          nowMs,
          accountId
        ),

        env.DB.prepare(
          `INSERT INTO p2p_wallet_ledger(
             id,
             account_id,
             admin_account_id,
             operation,
             amount,
             balance_delta,
             reason,
             created_at_ms,
             idempotency_key
           )
           SELECT
             ?1,
             ?2,
             ?3,
             ?4,
             ?5,
             ?6,
             ?7,
             ?8,
             ?9
           WHERE changes() = 1`
        ).bind(
          crypto.randomUUID(),
          accountId,
          auth.account.id,
          kind,
          amount,
          delta,
          reason,
          nowMs,
          idempotencyKey
        ),

        env.DB.prepare(
          `INSERT INTO admin_audit_log(
             id,
             admin_account_id,
             action,
             target_account_id,
             operation,
             amount,
             reason,
             created_at_ms
           )
           SELECT
             ?1,
             ?2,
             'P2P_WALLET_ADJUSTMENT',
             ?3,
             ?4,
             ?5,
             ?6,
             ?7
           WHERE changes() = 1`
        ).bind(
          crypto.randomUUID(),
          auth.account.id,
          accountId,
          kind,
          amount,
          reason,
          nowMs
        )
      ]);

      if (
        Number(
          results[0]?.meta?.changes || 0
        ) !== 1
      ) {
        return json(
          {
            error:
              'insufficient_available_balance'
          },
          409
        );
      }
    } catch (error) {
      const message =
        String(error?.message || '');

      if (
        message.includes('UNIQUE') ||
        message.includes('constraint')
      ) {
        const raced =
          await env.DB.prepare(
            `SELECT
               operation,
               amount,
               reason,
               idempotency_key AS idempotencyKey
               FROM p2p_wallet_ledger
              WHERE account_id = ?1
                AND idempotency_key = ?2`
          ).bind(
            accountId,
            idempotencyKey
          ).first();

        if (
          raced &&
          raced.operation === kind &&
          Number(raced.amount) ===
            Number(amount) &&
          raced.reason === reason
        ) {
          return json({
            ok: true,
            operation: kind,
            idempotencyKey,
            replay: true,
            ...(await getWallet(
              env.DB,
              accountId
            ))
          });
        }
      }

      throw error;
    }

    return json({
      ok: true,
      operation: kind,
      idempotencyKey,
      ...(await getWallet(
        env.DB,
        accountId
      ))
    });
  }

  if (
    request.method === 'POST' &&
    path === '/v1/p2p/wallet-requests'
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const body = await readJson(request);

    const type =
      body.type === 'DEPOSIT' ||
      body.type === 'WITHDRAW'
        ? body.type
        : '';

    const amount = body.amount;

    const reference = text(
      body.reference,
      500
    );

    const idempotencyKey = text(
      request.headers.get('Idempotency-Key') ||
      body.idempotencyKey,
      IDEMPOTENCY_KEY_MAX
    );

    if (
      !type ||
      !positiveNumber(amount) ||
      !idempotencyKey
    ) {
      return json(
        { error: 'invalid_wallet_request' },
        400
      );
    }

    if (
      !/^[A-Za-z0-9._:-]{8,128}$/.test(
        idempotencyKey
      )
    ) {
      return json(
        { error: 'invalid_idempotency_key' },
        400
      );
    }

    if (amount > 1000000000) {
      return json(
        { error: 'amount_too_large' },
        400
      );
    }

    await ensureWallet(
      env.DB,
      auth.account.id
    );

    const existing = await env.DB.prepare(
      `SELECT
         id,
         type,
         amount,
         reference,
         status,
         requested_at_ms AS requestedAt,
         reviewed_at_ms AS reviewedAt
         FROM wallet_financial_requests
        WHERE account_id = ?1
          AND idempotency_key = ?2`
    ).bind(
      auth.account.id,
      idempotencyKey
    ).first();

    if (existing) {
      if (
        existing.type !== type ||
        Number(existing.amount) !==
          Number(amount) ||
        existing.reference !== reference
      ) {
        return json(
          {
            error:
              'idempotency_key_reused_with_different_request'
          },
          409
        );
      }

      return json({
        ok: true,
        request: existing,
        replay: true
      });
    }

    const id =
      `RC_FIN_REQ_${crypto.randomUUID()}`;

    const nowMs = Date.now();

    try {
      await env.DB.prepare(
        `INSERT INTO wallet_financial_requests(
           id,
           account_id,
           type,
           amount,
           reference,
           status,
           idempotency_key,
           requested_at_ms
         )
         VALUES (
           ?1,
           ?2,
           ?3,
           ?4,
           ?5,
           'PENDING',
           ?6,
           ?7
         )`
      ).bind(
        id,
        auth.account.id,
        type,
        amount,
        reference,
        idempotencyKey,
        nowMs
      ).run();
    } catch (error) {
      const message =
        String(error?.message || '');

      if (
        message.includes('UNIQUE') ||
        message.includes('constraint')
      ) {
        const raced =
          await env.DB.prepare(
            `SELECT
               id,
               type,
               amount,
               reference,
               status,
               requested_at_ms AS requestedAt,
               reviewed_at_ms AS reviewedAt
               FROM wallet_financial_requests
              WHERE account_id = ?1
                AND idempotency_key = ?2`
          ).bind(
            auth.account.id,
            idempotencyKey
          ).first();

        if (raced) {
          if (
            raced.type !== type ||
            Number(raced.amount) !==
              Number(amount) ||
            raced.reference !== reference
          ) {
            return json(
              {
                error:
                  'idempotency_key_reused_with_different_request'
              },
              409
            );
          }

          return json({
            ok: true,
            request: raced,
            replay: true
          });
        }
      }

      throw error;
    }

    return json(
      {
        ok: true,
        request: {
          id,
          type,
          amount,
          reference,
          status: 'PENDING',
          requestedAt: nowMs
        }
      },
      201
    );
  }

  const walletRequestMatch =
    path.match(
      /^\/v1\/p2p\/wallet-requests\/([^/]+)$/
    );

  if (
    request.method === 'GET' &&
    walletRequestMatch
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const row = await env.DB.prepare(
      `SELECT
         id,
         type,
         amount,
         reference,
         status,
         requested_at_ms AS requestedAt,
         reviewed_at_ms AS reviewedAt
         FROM wallet_financial_requests
        WHERE id = ?1
          AND account_id = ?2`
    ).bind(
      walletRequestMatch[1],
      auth.account.id
    ).first();

    if (!row) {
      return json(
        { error: 'wallet_request_not_found' },
        404
      );
    }

    return json({
      ok: true,
      request: row
    });
  }

  if (
    request.method === 'GET' &&
    path === '/v1/admin/wallet-requests'
  ) {
    const auth = await requireAdmin(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const status =
      url.searchParams.get('status');

    const rows = await env.DB.prepare(
      `SELECT
         r.id,
         r.account_id AS accountId,
         a.username,
         a.normalized_email AS email,
         r.type,
         r.amount,
         r.reference,
         r.status,
         r.requested_at_ms AS requestedAt,
         r.reviewed_at_ms AS reviewedAt,
         r.reviewed_by_admin_id AS reviewedByAdminId
         FROM wallet_financial_requests r
         JOIN auth_accounts a
           ON a.id = r.account_id
        WHERE (
          ?1 IS NULL
          OR r.status = ?1
        )
        ORDER BY r.requested_at_ms ASC
        LIMIT 500`
    ).bind(
      status === 'PENDING' ||
      status === 'APPROVED' ||
      status === 'REJECTED'
        ? status
        : null
    ).all();

    return json({
      ok: true,
      requests: rows.results || []
    });
  }

  const adminWalletRequestMatch =
    path.match(
      /^\/v1\/admin\/wallet-requests\/([^/]+)\/(approve|reject)$/
    );

  if (
    request.method === 'POST' &&
    adminWalletRequestMatch
  ) {
    const auth = await requireAdmin(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const requestId =
      adminWalletRequestMatch[1];

    const decision =
      adminWalletRequestMatch[2] === 'approve'
        ? 'APPROVED'
        : 'REJECTED';

    const row = await env.DB.prepare(
      `SELECT
         id,
         account_id AS accountId,
         type,
         amount,
         reference,
         status
         FROM wallet_financial_requests
        WHERE id = ?1`
    ).bind(requestId).first();

    if (!row) {
      return json(
        { error: 'wallet_request_not_found' },
        404
      );
    }

    if (row.status !== 'PENDING') {
      return json({
        ok: true,
        status: row.status,
        alreadyReviewed: true
      });
    }

    if (
      row.accountId === auth.account.id
    ) {
      return json(
        { error: 'self_approval_forbidden' },
        403
      );
    }

    const nowMs = Date.now();

    try {
      const results = await env.DB.batch([
        env.DB.prepare(
          `UPDATE wallet_financial_requests
              SET status = ?1,
                  reviewed_at_ms = ?2,
                  reviewed_by_admin_id = ?3
            WHERE id = ?4
              AND status = 'PENDING'`
        ).bind(
          decision,
          nowMs,
          auth.account.id,
          requestId
        ),

        env.DB.prepare(
          `INSERT INTO admin_audit_log(
             id,
             admin_account_id,
             action,
             target_account_id,
             target_request_id,
             operation,
             amount,
             reason,
             created_at_ms
           )
           SELECT
             ?1,
             ?2,
             'WALLET_REQUEST_REVIEW',
             ?3,
             ?4,
             ?5,
             ?6,
             ?7,
             ?8
           WHERE changes() = 1`
        ).bind(
          crypto.randomUUID(),
          auth.account.id,
          row.accountId,
          requestId,
          `${row.type}_${decision}`,
          row.amount,
          row.reference || decision,
          nowMs
        )
      ]);

      if (
        Number(
          results[0]?.meta?.changes || 0
        ) !== 1
      ) {
        return json(
          {
            error:
              'wallet_request_already_reviewed'
          },
          409
        );
      }

      if (
        Number(
          results[1]?.meta?.changes || 0
        ) !== 1
      ) {
        return json(
          { error: 'audit_unavailable' },
          500
        );
      }
    } catch (error) {
      if (
        String(error?.message || '')
          .includes(
            'insufficient_available_balance'
          )
      ) {
        return json(
          {
            error:
              'insufficient_available_balance'
          },
          409
        );
      }

      throw error;
    }

    return json({
      ok: true,
      status: decision,
      requestId
    });
  }

  if (
    request.method === 'GET' &&
    path === '/v1/p2p/ads'
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const requestedType =
      url.searchParams.get('type');

    // The UI tabs represent the action the current user wants to take:
    // BUY shows users' SELL advertisements, and SELL shows users' BUY advertisements.
    // Keep the stored ad type unchanged; only map the read-side filter.
    const visibleAdType =
      requestedType === 'BUY'
        ? 'SELL'
        : requestedType === 'SELL'
          ? 'BUY'
          : null;

    const rows = await env.DB.prepare(
      `SELECT
         id,
         seller_id AS sellerId,
         seller_name AS sellerName,
         type,
         crypto_amount AS cryptoAmount,
         fiat_price AS fiatPrice,
         fiat_currency AS fiatCurrency,
         payment_method AS paymentMethod,
         payment_name AS paymentName,
         account_number AS accountNumber,
         is_active AS isActive,
         min_order_etb AS minOrderEtb,
         max_order_etb AS maxOrderEtb,
         original_max_order_etb AS originalMaxOrderEtb,
         created_at_ms AS createdAt
         FROM p2p_ads
        WHERE is_active = 1
          AND (
            ?1 IS NULL
            OR type = ?1
          )
        ORDER BY created_at_ms DESC`
    ).bind(visibleAdType).all();

    return json({
      ok: true,
      ads: rows.results || []
    });
  }

  if (
    request.method === 'POST' &&
    path === '/v1/p2p/ads'
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    // Only authenticated accounts with an admin-verified KYC record may publish P2P ads.
    // This blocks unknown/unverified accounts without changing the KYC workflow itself.
    const kyc = await env.DB.prepare(
      `SELECT status
         FROM kyc_records
        WHERE user_account_id = ?1
        LIMIT 1`
    ).bind(auth.account.id).first();

    if (kyc?.status !== 'VERIFIED') {
      return json(
        { error: 'kyc_verification_required' },
        403
      );
    }

    const body = await readJson(request);

    const type =
      body.type === 'BUY' ||
      body.type === 'SELL'
        ? body.type
        : '';

    const cryptoAmount =
      body.cryptoAmount;

    const fiatPrice =
      body.fiatPrice;

    const minOrderEtb =
      body.minOrderEtb;

    const maxOrderEtb =
      body.maxOrderEtb;

    // Total ETB value of the advertisement. Older clients may omit it;
    // derive it server-side for backwards compatibility.
    const fiatOrderAmount =
      body.fiatOrderAmount == null
        ? Number(cryptoAmount) * Number(fiatPrice)
        : body.fiatOrderAmount;

    const paymentMethod = text(
      body.paymentMethod,
      128
    );

    const paymentName = text(
      body.paymentName,
      128
    );

    const accountNumber = text(
      body.accountNumber,
      128
    );

    if (
      !type ||
      !positiveNumber(cryptoAmount) ||
      !positiveNumber(fiatPrice) ||
      !positiveNumber(minOrderEtb) ||
      !positiveNumber(maxOrderEtb) ||
      !positiveNumber(fiatOrderAmount) ||
      minOrderEtb > maxOrderEtb ||
      maxOrderEtb > fiatOrderAmount ||
      !paymentMethod
    ) {
      return json(
        { error: 'invalid_ad' },
        400
      );
    }

    const nowMs = Date.now();

    const id =
      `RC_AD_${crypto.randomUUID()}`;

    await ensureWallet(
      env.DB,
      auth.account.id,
      nowMs
    );

    const statements = [];

    if (type === 'SELL') {
      statements.push(
        env.DB.prepare(
          `UPDATE p2p_wallets
              SET real_locked_balance =
                    real_locked_balance + ?1,
                  updated_at_ms = ?2
            WHERE account_id = ?3
              AND real_balance -
                  real_locked_balance >= ?1`
        ).bind(
          cryptoAmount,
          nowMs,
          auth.account.id
        )
      );
    }

    statements.push(
      env.DB.prepare(
        `INSERT INTO p2p_ads(
           id,
           seller_id,
           seller_name,
           type,
           crypto_amount,
           fiat_price,
           fiat_currency,
           payment_method,
           payment_name,
           account_number,
           is_active,
           min_order_etb,
           max_order_etb,
           original_max_order_etb,
           created_at_ms
         )
         SELECT
           ?1,
           ?2,
           ?3,
           ?4,
           ?5,
           ?6,
           'ETB',
           ?7,
           ?8,
           ?9,
           1,
           ?10,
           ?11,
           ?11,
           ?12
         WHERE
           ?4 = 'BUY'
           OR EXISTS (
             SELECT 1
               FROM p2p_wallets
              WHERE account_id = ?2
                AND real_locked_balance >= ?5
           )`
      ).bind(
        id,
        auth.account.id,
        auth.account.username,
        type,
        cryptoAmount,
        fiatPrice,
        paymentMethod,
        paymentName,
        accountNumber,
        minOrderEtb,
        maxOrderEtb,
        nowMs
      )
    );

    try {
      const results =
        await env.DB.batch(statements);

      const adInsert =
        results[results.length - 1];

      if (
        Number(
          adInsert?.meta?.changes || 0
        ) !== 1
      ) {
        return json(
          {
            error:
              type === 'SELL'
                ? 'insufficient_real_balance'
                : 'ad_create_failed'
          },
          400
        );
      }
    } catch (error) {
      throw error;
    }

    return json(
      {
        ok: true,
        ad: await getAd(
          env.DB,
          id
        )
      },
      201
    );
  }

  if (
    request.method === 'POST' &&
    /^\/v1\/p2p\/ads\/[^/]+\/delete$/
      .test(path)
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const adId =
      path.split('/')[4];

    const ad = await getAd(
      env.DB,
      adId
    );

    if (
      !ad ||
      ad.sellerId !== auth.account.id
    ) {
      return json(
        { error: 'ad_not_found' },
        404
      );
    }

    if (!Number(ad.isActive)) {
      return json(
        { error: 'ad_inactive' },
        400
      );
    }

    const nowMs = Date.now();

    try {
      const result = await env.DB.prepare(
        `DELETE FROM p2p_ads
          WHERE id = ?1
            AND seller_id = ?2
            AND is_active = 1
            AND NOT EXISTS (
              SELECT 1
                FROM p2p_orders
               WHERE ad_id = ?1
                 AND status IN (
                   'ESCROW_LOCKED',
                   'PAID',
                   'DISPUTED'
                 )
            )`
      ).bind(
        adId,
        auth.account.id
      ).run();

      if (
        Number(
          result?.meta?.changes || 0
        ) !== 1
      ) {
        const open =
          await env.DB.prepare(
            `SELECT id
               FROM p2p_orders
              WHERE ad_id = ?1
                AND status IN (
                  'ESCROW_LOCKED',
                  'PAID',
                  'DISPUTED'
                )
              LIMIT 1`
          ).bind(adId).first();

        return json(
          {
            error:
              open
                ? 'ad_has_open_order'
                : 'ad_unavailable'
          },
          409
        );
      }
    } catch (error) {
      if (
        String(error?.message || '')
          .includes(
            'wallet_lock_inconsistent'
          )
      ) {
        return json(
          {
            error:
              'wallet_lock_inconsistent'
          },
          409
        );
      }

      throw error;
    }

    return json({
      ok: true,
      deletedAt: nowMs
    });
  }

  if (
    request.method === 'GET' &&
    path === '/v1/p2p/orders'
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const rows = await env.DB.prepare(
      `SELECT
         id,
         ad_id AS adId,
         seller_id AS sellerId,
         seller_name AS sellerName,
         buyer_id AS buyerId,
         buyer_name AS buyerName,
         crypto_amount AS cryptoAmount,
         fiat_price AS fiatPrice,
         fiat_order_amount AS fiatOrderAmount,
         fiat_currency AS fiatCurrency,
         payment_method AS paymentMethod,
         payment_name AS paymentName,
         account_number AS accountNumber,
         payment_proof_url AS paymentProofUrl,
         paid_at_ms AS paidAt,
         status,
         created_at_ms AS createdAt,
         expires_at_ms AS expiresAt,
         completed_at_ms AS completedAt,
         dispute_reason AS disputeReason,
         disputed_at_ms AS disputedAt,
         resolved_at_ms AS resolvedAt,
         resolved_by_admin_id AS resolvedByAdminId
         FROM p2p_orders
        WHERE buyer_id = ?1
           OR seller_id = ?1
        ORDER BY created_at_ms DESC
        LIMIT 100`
    ).bind(
      auth.account.id
    ).all();

    for (
      const order of rows.results || []
    ) {
      await expireOrder(
        env.DB,
        order
      );
    }

    return json({
      ok: true,
      orders: rows.results || []
    });
  }

  if (
    request.method === 'POST' &&
    path === '/v1/p2p/orders'
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const body = await readJson(request);

    const adId = text(
      body.adId,
      128
    );

    const cryptoAmount =
      body.cryptoAmount;

    const fiatAmount =
      body.fiatAmount;

    if (
      !adId ||
      !positiveNumber(cryptoAmount) ||
      !positiveNumber(fiatAmount)
    ) {
      return json(
        { error: 'invalid_order' },
        400
      );
    }

    const ad = await getAd(
      env.DB,
      adId
    );

    if (
      !ad ||
      !Number(ad.isActive)
    ) {
      return json(
        { error: 'ad_unavailable' },
        409
      );
    }

    if (
      ad.sellerId ===
      auth.account.id
    ) {
      return json(
        { error: 'cannot_trade_own_ad' },
        400
      );
    }

    if (
      fiatAmount <
        Number(ad.minOrderEtb) ||
      fiatAmount >
        Number(ad.maxOrderEtb)
    ) {
      return json(
        { error: 'amount_out_of_range' },
        400
      );
    }

    const calculated =
      fiatAmount /
      Number(ad.fiatPrice);

    if (
      Math.abs(
        calculated - cryptoAmount
      ) >
      Math.max(
        0.000001,
        calculated * 0.00001
      )
    ) {
      return json(
        { error: 'amount_mismatch' },
        400
      );
    }

    const nowMs = Date.now();

    const orderId =
      `RC_ORDER_${crypto.randomUUID()}`;

    const sellerId =
      ad.type === 'SELL'
        ? ad.sellerId
        : auth.account.id;

    const sellerName =
      ad.type === 'SELL'
        ? ad.sellerName
        : auth.account.username;

    const buyerId =
      ad.type === 'SELL'
        ? auth.account.id
        : ad.sellerId;

    const buyerName =
      ad.type === 'SELL'
        ? auth.account.username
        : ad.sellerName;

    await ensureWallet(
      env.DB,
      sellerId,
      nowMs
    );

    const statements = [];

    if (ad.type === 'BUY') {
      statements.push(
        env.DB.prepare(
          `UPDATE p2p_wallets
              SET real_locked_balance =
                    real_locked_balance + ?1,
                  updated_at_ms = ?2
            WHERE account_id = ?3
              AND real_balance -
                  real_locked_balance >= ?1`
        ).bind(
          cryptoAmount,
          nowMs,
          sellerId
        )
      );
    }

    statements.push(
      env.DB.prepare(
        `UPDATE p2p_ads
            SET crypto_amount =
                  crypto_amount - ?1,
                max_order_etb =
                  CASE
                    WHEN max_order_etb - ?2 > 0
                    THEN max_order_etb - ?2
                    ELSE 0
                  END,
                is_active = 0
          WHERE id = ?3
            AND is_active = 1
            AND crypto_amount >= ?1
            AND max_order_etb >= ?2`
      ).bind(
        cryptoAmount,
        fiatAmount,
        adId
      )
    );

    statements.push(
      env.DB.prepare(
        `INSERT INTO p2p_orders(
           id,
           ad_id,
           seller_id,
           seller_name,
           buyer_id,
           buyer_name,
           crypto_amount,
           fiat_price,
           fiat_order_amount,
           fiat_currency,
           payment_method,
           payment_name,
           account_number,
           payment_proof_url,
           paid_at_ms,
           status,
           created_at_ms,
           expires_at_ms,
           completed_at_ms,
           dispute_reason,
           disputed_at_ms,
           resolved_at_ms,
           resolved_by_admin_id
         )
         SELECT
           ?1,
           ?2,
           ?3,
           ?4,
           ?5,
           ?6,
           ?7,
           ?8,
           ?9,
           'ETB',
           ?10,
           ?11,
           ?12,
           NULL,
           NULL,
           'ESCROW_LOCKED',
           ?13,
           ?14,
           NULL,
           NULL,
           NULL,
           NULL,
           NULL
         WHERE EXISTS (
           SELECT 1
             FROM p2p_ads
            WHERE id = ?2
              AND is_active = 0
         )`
      ).bind(
        orderId,
        ad.id,
        sellerId,
        sellerName,
        buyerId,
        buyerName,
        cryptoAmount,
        ad.fiatPrice,
        fiatAmount,
        ad.paymentMethod,
        ad.paymentName,
        ad.accountNumber,
        nowMs,
        nowMs + P2P_ORDER_TTL_MS
      )
    );

    if (ad.type === 'BUY') {
      statements.push(
        env.DB.prepare(
          `INSERT INTO p2p_trade_ledger(
             id,
             order_id,
             account_id,
             event,
             real_delta,
             locked_delta,
             created_at_ms
           )
           SELECT
             ?1,
             ?2,
             ?3,
             'ESCROW_LOCK',
             0,
             ?4,
             ?5
           WHERE changes() = 1`
        ).bind(
          `RC_TRADE_LEDGER_${orderId}_LOCK`,
          orderId,
          sellerId,
          cryptoAmount,
          nowMs
        )
      );
    }

    try {
      const results =
        await env.DB.batch(
          statements
        );

      const lockIndex =
        ad.type === 'BUY'
          ? 0
          : -1;

      const adIndex =
        ad.type === 'BUY'
          ? 1
          : 0;

      const orderIndex =
        ad.type === 'BUY'
          ? 2
          : 1;

      const ledgerIndex =
        ad.type === 'BUY'
          ? 3
          : 2;

      if (
        lockIndex >= 0 &&
        Number(
          results[lockIndex]?.meta?.changes || 0
        ) !== 1
      ) {
        return json(
          {
            error:
              'seller_insufficient_real_balance'
          },
          409
        );
      }

      if (
        Number(
          results[adIndex]?.meta?.changes || 0
        ) !== 1 ||
        Number(
          results[orderIndex]?.meta?.changes || 0
        ) !== 1
      ) {
        return json(
          { error: 'ad_unavailable' },
          409
        );
      }

      if (
        Number(
          results[ledgerIndex]?.meta?.changes || 0
        ) !== 1
      ) {
        return json(
          {
            error:
              'trade_ledger_unavailable'
          },
          500
        );
      }
    } catch (error) {
      throw error;
    }

    return json(
      {
        ok: true,
        order: await getOrder(
          env.DB,
          orderId
        )
      },
      201
    );
  }

  const orderPaidMatch =
    path.match(
      /^\/v1\/p2p\/orders\/([^/]+)\/paid$/
    );

  if (
    request.method === 'POST' &&
    orderPaidMatch
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const order = await getOrder(
      env.DB,
      orderPaidMatch[1]
    );

    if (
      !order ||
      order.buyerId !==
        auth.account.id
    ) {
      return json(
        { error: 'order_not_found' },
        404
      );
    }

    if (
      order.status !== 'ESCROW_LOCKED' ||
      Number(order.expiresAt) <=
        Date.now()
    ) {
      return json(
        { error: 'order_not_payable' },
        409
      );
    }

    const body = await readJson(request);

    const proof = text(
      body.paymentProofUrl,
      2048
    );

    if (
      !proof ||
      !(await isOwnedAttachmentUrl(
        env.DB,
        request,
        auth.account.id,
        proof
      ))
    ) {
      return json(
        { error: 'payment_proof_required' },
        400
      );
    }

    const paidResult =
      await env.DB.prepare(
        `UPDATE p2p_orders
            SET status = 'PAID',
                payment_proof_url = ?1,
                paid_at_ms = ?2
          WHERE id = ?3
            AND status = 'ESCROW_LOCKED'`
      ).bind(
        proof,
        Date.now(),
        order.id
      ).run();

    if (
      Number(
        paidResult.meta?.changes || 0
      ) !== 1
    ) {
      return json(
        { error: 'order_not_payable' },
        409
      );
    }

    return json({
      ok: true,
      status: 'PAID'
    });
  }

  const orderCompleteMatch =
    path.match(
      /^\/v1\/p2p\/orders\/([^/]+)\/(?:complete|release)$/
    );

  if (
    request.method === 'POST' &&
    orderCompleteMatch
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const order = await getOrder(
      env.DB,
      orderCompleteMatch[1]
    );

    if (
      !order ||
      order.sellerId !==
        auth.account.id
    ) {
      return json(
        { error: 'order_not_found' },
        404
      );
    }

    if (order.status !== 'PAID') {
      return json(
        { error: 'order_not_ready' },
        409
      );
    }

    const nowMs = Date.now();

    await ensureWallet(
      env.DB,
      order.buyerId,
      nowMs
    );

    const results = await env.DB.batch([
      env.DB.prepare(
        `UPDATE p2p_wallets
            SET real_locked_balance =
                  real_locked_balance - ?1,
                real_balance =
                  real_balance - ?1,
                updated_at_ms = ?2
          WHERE account_id = ?3
            AND real_balance >= ?1
            AND real_locked_balance >= ?1`
      ).bind(
        order.cryptoAmount,
        nowMs,
        order.sellerId
      ),

      env.DB.prepare(
        `UPDATE p2p_wallets
            SET real_balance =
                  real_balance + ?1,
                updated_at_ms = ?2
          WHERE account_id = ?3`
      ).bind(
        order.cryptoAmount,
        nowMs,
        order.buyerId
      ),

      env.DB.prepare(
        `UPDATE p2p_orders
            SET status = 'COMPLETED',
                completed_at_ms = ?1
          WHERE id = ?2
            AND status = 'PAID'`
      ).bind(
        nowMs,
        order.id
      ),

      env.DB.prepare(
        `UPDATE p2p_ads
            SET is_active =
              CASE
                WHEN crypto_amount > 0
                 AND max_order_etb > 0
                THEN 1
                ELSE 0
              END
          WHERE id = ?1`
      ).bind(order.adId),

      env.DB.prepare(
        `INSERT INTO p2p_trade_ledger(
           id,
           order_id,
           account_id,
           event,
           real_delta,
           locked_delta,
           created_at_ms
         )
         VALUES (
           ?1,
           ?2,
           ?3,
           'TRADE_DEBIT',
           ?4,
           ?5,
           ?6
         )`
      ).bind(
        `RC_TRADE_LEDGER_${order.id}_DEBIT`,
        order.id,
        order.sellerId,
        -order.cryptoAmount,
        -order.cryptoAmount,
        nowMs
      ),

      env.DB.prepare(
        `INSERT INTO p2p_trade_ledger(
           id,
           order_id,
           account_id,
           event,
           real_delta,
           locked_delta,
           created_at_ms
         )
         VALUES (
           ?1,
           ?2,
           ?3,
           'TRADE_CREDIT',
           ?4,
           ?5,
           ?6
         )`
      ).bind(
        `RC_TRADE_LEDGER_${order.id}_CREDIT`,
        order.id,
        order.buyerId,
        order.cryptoAmount,
        0,
        nowMs
      )
    ]);

    if (
      Number(results[0]?.meta?.changes || 0) !== 1 ||
      Number(results[1]?.meta?.changes || 0) !== 1 ||
      Number(results[2]?.meta?.changes || 0) !== 1 ||
      Number(results[3]?.meta?.changes || 0) !== 1 ||
      Number(results[4]?.meta?.changes || 0) !== 1 ||
      Number(results[5]?.meta?.changes || 0) !== 1
    ) {
      return json(
        { error: 'escrow_unavailable' },
        409
      );
    }

    return json({
      ok: true,
      status: 'COMPLETED'
    });
  }

  const orderExpireMatch =
    path.match(
      /^\/v1\/p2p\/orders\/([^/]+)\/expire$/
    );

  if (
    request.method === 'POST' &&
    orderExpireMatch
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const order = await getOrder(
      env.DB,
      orderExpireMatch[1]
    );

    if (
      !order ||
      (
        order.buyerId !== auth.account.id &&
        order.sellerId !== auth.account.id
      )
    ) {
      return json(
        { error: 'order_not_found' },
        404
      );
    }

    const expired =
      await expireOrder(
        env.DB,
        order
      );

    return expired
      ? json({
          ok: true,
          status: 'CANCELLED'
        })
      : json(
          { error: 'order_not_expired' },
          409
        );
  }

  const orderDisputeMatch =
    path.match(
      /^\/v1\/p2p\/orders\/([^/]+)\/dispute$/
    );

  if (
    request.method === 'POST' &&
    orderDisputeMatch
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const order = await getOrder(
      env.DB,
      orderDisputeMatch[1]
    );

    if (
      !order ||
      (
        order.buyerId !== auth.account.id &&
        order.sellerId !== auth.account.id
      )
    ) {
      return json(
        { error: 'order_not_found' },
        404
      );
    }

    if (order.status !== 'PAID') {
      return json(
        { error: 'order_not_disputable' },
        409
      );
    }

    const body = await readJson(request);

    const reason = text(
      body.reason,
      1000
    );

    if (!reason) {
      return json(
        { error: 'reason_required' },
        400
      );
    }

    const disputeResult =
      await env.DB.prepare(
        `UPDATE p2p_orders
            SET status = 'DISPUTED',
                dispute_reason = ?1,
                disputed_at_ms = ?2
          WHERE id = ?3
            AND status = 'PAID'`
      ).bind(
        reason,
        Date.now(),
        order.id
      ).run();

    if (
      Number(
        disputeResult.meta?.changes || 0
      ) !== 1
    ) {
      return json(
        { error: 'order_not_disputable' },
        409
      );
    }

    return json({
      ok: true,
      status: 'DISPUTED'
    });
  }

  const orderResolveMatch =
    path.match(
      /^\/v1\/p2p\/orders\/([^/]+)\/resolve$/
    );

  if (
    request.method === 'POST' &&
    orderResolveMatch
  ) {
    const auth = await requireAdmin(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const order = await getOrder(
      env.DB,
      orderResolveMatch[1]
    );

    if (
      !order ||
      order.status !== 'DISPUTED'
    ) {
      return json(
        { error: 'dispute_not_found' },
        404
      );
    }

    const body = await readJson(request);

    const decision =
      body.decision === 'RELEASE' ||
      body.decision === 'REFUND'
        ? body.decision
        : '';

    if (!decision) {
      return json(
        { error: 'invalid_decision' },
        400
      );
    }

    const nowMs = Date.now();

    await ensureWallet(
      env.DB,
      order.buyerId,
      nowMs
    );

    const statements = [
      decision === 'RELEASE'
        ? env.DB.prepare(
            `UPDATE p2p_wallets
                SET real_locked_balance =
                      real_locked_balance - ?1,
                    real_balance =
                      real_balance - ?1,
                    updated_at_ms = ?2
              WHERE account_id = ?3
                AND real_balance >= ?1
                AND real_locked_balance >= ?1`
          ).bind(
            order.cryptoAmount,
            nowMs,
            order.sellerId
          )
        : env.DB.prepare(
            `UPDATE p2p_wallets
                SET real_locked_balance =
                      real_locked_balance - ?1,
                    updated_at_ms = ?2
              WHERE account_id = ?3
                AND real_locked_balance >= ?1`
          ).bind(
            order.cryptoAmount,
            nowMs,
            order.sellerId
          ),

      ...(decision === 'RELEASE'
        ? [
            env.DB.prepare(
              `UPDATE p2p_wallets
                  SET real_balance =
                        real_balance + ?1,
                      updated_at_ms = ?2
                WHERE account_id = ?3`
            ).bind(
              order.cryptoAmount,
              nowMs,
              order.buyerId
            )
          ]
        : [
            env.DB.prepare(
              `UPDATE p2p_ads
                  SET crypto_amount =
                        crypto_amount + ?1,
                      max_order_etb =
                        original_max_order_etb,
                      is_active =
                        CASE
                          WHEN crypto_amount + ?1 > 0
                           AND original_max_order_etb > 0
                          THEN 1
                          ELSE 0
                        END
                WHERE id = ?2`
            ).bind(
              order.cryptoAmount,
              order.adId
            )
          ]),

      env.DB.prepare(
        `UPDATE p2p_orders
            SET status = 'COMPLETED',
                resolved_at_ms = ?1,
                resolved_by_admin_id = ?2,
                completed_at_ms = ?1
          WHERE id = ?3
            AND status = 'DISPUTED'`
      ).bind(
        nowMs,
        auth.account.id,
        order.id
      ),

      ...(decision === 'RELEASE'
        ? [
            env.DB.prepare(
              `UPDATE p2p_ads
                  SET is_active =
                    CASE
                      WHEN crypto_amount > 0
                       AND max_order_etb > 0
                      THEN 1
                      ELSE 0
                    END
                WHERE id = ?1`
            ).bind(order.adId)
          ]
        : []),

      env.DB.prepare(
        `INSERT INTO admin_audit_log(
           id,
           admin_account_id,
           action,
           target_account_id,
           target_order_id,
           operation,
           amount,
           reason,
           created_at_ms
         )
         VALUES (
           ?1,
           ?2,
           'P2P_DISPUTE_RESOLUTION',
           ?3,
           ?4,
           ?5,
           ?6,
           ?7,
           ?8
         )`
      ).bind(
        crypto.randomUUID(),
        auth.account.id,
        order.sellerId,
        order.id,
        decision,
        order.cryptoAmount,
        order.disputeReason ||
          decision,
        nowMs
      ),

      ...(decision === 'RELEASE'
        ? [
            env.DB.prepare(
              `INSERT INTO p2p_trade_ledger(
                 id,
                 order_id,
                 account_id,
                 event,
                 real_delta,
                 locked_delta,
                 created_at_ms
               )
               VALUES (
                 ?1,
                 ?2,
                 ?3,
                 'TRADE_DEBIT',
                 ?4,
                 ?5,
                 ?6
               )`
            ).bind(
              `RC_TRADE_LEDGER_${order.id}_DEBIT`,
              order.id,
              order.sellerId,
              -order.cryptoAmount,
              -order.cryptoAmount,
              nowMs
            ),

            env.DB.prepare(
              `INSERT INTO p2p_trade_ledger(
                 id,
                 order_id,
                 account_id,
                 event,
                 real_delta,
                 locked_delta,
                 created_at_ms
               )
               VALUES (
                 ?1,
                 ?2,
                 ?3,
                 'TRADE_CREDIT',
                 ?4,
                 ?5,
                 ?6
               )`
            ).bind(
              `RC_TRADE_LEDGER_${order.id}_CREDIT`,
              order.id,
              order.buyerId,
              order.cryptoAmount,
              0,
              nowMs
            )
          ]
        : [
            env.DB.prepare(
              `INSERT INTO p2p_trade_ledger(
                 id,
                 order_id,
                 account_id,
                 event,
                 real_delta,
                 locked_delta,
                 created_at_ms
               )
               VALUES (
                 ?1,
                 ?2,
                 ?3,
                 'ESCROW_UNLOCK',
                 0,
                 ?4,
                 ?5
               )`
            ).bind(
              `RC_TRADE_LEDGER_${order.id}_UNLOCK`,
              order.id,
              order.sellerId,
              -order.cryptoAmount,
              nowMs
            )
          ])
    ];

    const results =
      await env.DB.batch(
        statements
      );

    const orderResultIndex = 2;

    const auditResultIndex =
      decision === 'RELEASE'
        ? 4
        : 3;

    const ledgerStartIndex =
      decision === 'RELEASE'
        ? 5
        : 4;

    if (
      Number(
        results[0]?.meta?.changes || 0
      ) !== 1
    ) {
      return json(
        { error: 'escrow_unavailable' },
        409
      );
    }

    if (
      Number(
        results[orderResultIndex]?.meta?.changes ||
        0
      ) !== 1
    ) {
      return json(
        { error: 'dispute_already_resolved' },
        409
      );
    }

    if (
      Number(
        results[auditResultIndex]?.meta?.changes ||
        0
      ) !== 1
    ) {
      return json(
        { error: 'audit_unavailable' },
        500
      );
    }

    if (
      Number(
        results[ledgerStartIndex]?.meta?.changes ||
        0
      ) !== 1 ||
      (
        decision === 'RELEASE' &&
        Number(
          results[
            ledgerStartIndex + 1
          ]?.meta?.changes || 0
        ) !== 1
      )
    ) {
      return json(
        {
          error:
            'trade_ledger_unavailable'
        },
        500
      );
    }

    return json({
      ok: true,
      status: 'COMPLETED'
    });
  }

  if (
    request.method === 'POST' &&
    path === '/v1/p2p/attachments'
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const body =
      await readJsonWithLimit(
        request,
        MAX_ATTACHMENT_BYTES * 2
      );

    const contentType = text(
      body.contentType,
      64
    );

    const data =
      typeof body.dataBase64 === 'string'
        ? body.dataBase64
        : '';

    if (
      !/^image\/(png|jpeg|webp)$/
        .test(contentType) ||
      !data ||
      data.length >
        Math.ceil(
          MAX_ATTACHMENT_BYTES * 4 / 3
        )
    ) {
      return json(
        { error: 'invalid_attachment' },
        400
      );
    }

    const id =
      `RC_FILE_${crypto.randomUUID()}`;

    const nowMs = Date.now();

    await env.DB.prepare(
      `INSERT INTO p2p_attachments(
         id,
         owner_account_id,
         content_type,
         data_base64,
         created_at_ms
       )
       VALUES (?1, ?2, ?3, ?4, ?5)`
    ).bind(
      id,
      auth.account.id,
      contentType,
      data,
      nowMs
    ).run();

    return json(
      {
        ok: true,
        url:
          `${new URL(request.url).origin}` +
          `/v1/p2p/attachments/${id}`
      },
      201
    );
  }

  const attachmentMatch =
    path.match(
      /^\/v1\/p2p\/attachments\/([^/]+)$/
    );

  if (
    request.method === 'GET' &&
    attachmentMatch
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const attachmentId =
      attachmentMatch[1];

    const attachmentUrl =
      `${new URL(request.url).origin}` +
      `/v1/p2p/attachments/${attachmentId}`;

    const row = await env.DB.prepare(
      `SELECT
         a.id,
         a.content_type AS contentType,
         a.data_base64 AS dataBase64
         FROM p2p_attachments a
        WHERE a.id = ?1
          AND (
            a.owner_account_id = ?2
            OR (
              EXISTS (SELECT 1 FROM auth_accounts aa WHERE aa.id = ?2 AND aa.role = 'ADMIN')
              AND EXISTS (SELECT 1 FROM kyc_records k WHERE k.user_account_id = a.owner_account_id AND (k.front_id_url = ?3 OR k.back_id_url = ?3))
            )
            OR EXISTS (
              SELECT 1
                FROM p2p_orders o
               WHERE (
                 o.buyer_id = ?2
                 OR o.seller_id = ?2
               )
                 AND (
                   o.payment_proof_url = ?3
                   OR EXISTS (
                     SELECT 1
                       FROM p2p_chat_messages m
                      WHERE m.order_id = o.id
                        AND m.attachment_url = ?3
                   )
                 )
            )
          )`
    ).bind(
      attachmentId,
      auth.account.id,
      attachmentUrl
    ).first();

    if (!row) {
      return json(
        { error: 'attachment_not_found' },
        404
      );
    }

    return json({
      ok: true,
      contentType: row.contentType,
      dataBase64: row.dataBase64
    });
  }

  const chatMatch =
    path.match(
      /^\/v1\/p2p\/orders\/([^/]+)\/chat$/
    );

  if (
    request.method === 'GET' &&
    chatMatch
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const order = await getOrder(
      env.DB,
      chatMatch[1]
    );

    if (
      !order ||
      (
        order.buyerId !== auth.account.id &&
        order.sellerId !== auth.account.id
      )
    ) {
      return json(
        { error: 'order_not_found' },
        404
      );
    }

    const rows = await env.DB.prepare(
      `SELECT
         id,
         order_id AS orderId,
         sender_id AS senderId,
         sender_name AS senderName,
         message,
         attachment_url AS attachmentUrl,
         created_at_ms AS createdAt
         FROM p2p_chat_messages
        WHERE order_id = ?1
        ORDER BY created_at_ms ASC
        LIMIT 500`
    ).bind(
      order.id
    ).all();

    return json({
      ok: true,
      messages: rows.results || []
    });
  }

  if (
    request.method === 'POST' &&
    chatMatch
  ) {
    const auth = await requireAuth(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const order = await getOrder(
      env.DB,
      chatMatch[1]
    );

    if (
      !order ||
      (
        order.buyerId !== auth.account.id &&
        order.sellerId !== auth.account.id
      )
    ) {
      return json(
        { error: 'order_not_found' },
        404
      );
    }

    const body = await readJson(request);

    const message = text(
      body.message,
      2000
    );

    const attachmentUrl = text(
      body.attachmentUrl,
      2048
    );

    if (
      !message &&
      !attachmentUrl
    ) {
      return json(
        {
          error:
            'message_or_attachment_required'
        },
        400
      );
    }

    if (
      attachmentUrl &&
      !(await isOwnedAttachmentUrl(
        env.DB,
        request,
        auth.account.id,
        attachmentUrl
      ))
    ) {
      return json(
        { error: 'invalid_attachment_url' },
        400
      );
    }

    const id =
      `RC_MSG_${crypto.randomUUID()}`;

    const nowMs = Date.now();

    await env.DB.prepare(
      `INSERT INTO p2p_chat_messages(
         id,
         order_id,
         sender_id,
         sender_name,
         message,
         attachment_url,
         created_at_ms
       )
       VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)`
    ).bind(
      id,
      order.id,
      auth.account.id,
      auth.account.username,
      message,
      attachmentUrl || null,
      nowMs
    ).run();

    return json(
      {
        ok: true,
        message: {
          id,
          orderId: order.id,
          senderId: auth.account.id,
          senderName:
            auth.account.username,
          message,
          attachmentUrl:
            attachmentUrl || null,
          createdAt: nowMs
        }
      },
      201
    );
  }

  if (
    request.method === 'GET' &&
    path === '/v1/admin/password-recovery'
  ) {
    const auth = await requireAdmin(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const rows = await env.DB.prepare(
      `SELECT
         id,
         normalized_email AS email,
         status,
         requested_at_ms AS requestedAtMs,
         reviewed_at_ms AS reviewedAtMs,
         completed_at_ms AS completedAtMs
         FROM password_recovery_requests_v2
        ORDER BY requested_at_ms DESC
        LIMIT 100`
    ).all();

    return json({
      ok: true,
      requests: rows.results || []
    });
  }

  const recoveryReview =
    path.match(
      /^\/v1\/admin\/password-recovery\/([^/]+)$/
    );

  if (
    request.method === 'POST' &&
    recoveryReview
  ) {
    const auth = await requireAdmin(
      request,
      env
    );

    if (auth.response) {
      return auth.response;
    }

    const body = await readJson(request);

    const status =
      body.status === 'APPROVED' ||
      body.status === 'REJECTED'
        ? body.status
        : '';

    if (!status) {
      return json(
        { error: 'invalid_status' },
        400
      );
    }

    const nowMs = Date.now();

    const requestId =
      recoveryReview[1];

    const row = await env.DB.prepare(
      `SELECT
         id,
         normalized_email AS email,
         status
         FROM password_recovery_requests_v2
        WHERE id = ?1`
    ).bind(
      requestId
    ).first();

    if (!row) {
      return json(
        {
          error:
            'recovery_request_not_found'
        },
        404
      );
    }

    if (row.status !== 'PENDING') {
      return json({
        ok: true,
        status: row.status,
        alreadyReviewed: true
      });
    }

    const results = await env.DB.batch([
      env.DB.prepare(
        `UPDATE password_recovery_requests_v2
            SET status = ?1,
                reviewed_at_ms = ?2
          WHERE id = ?3
            AND status = 'PENDING'`
      ).bind(
        status,
        nowMs,
        requestId
      ),

      env.DB.prepare(
        `INSERT INTO admin_audit_log(
           id,
           admin_account_id,
           action,
           target_account_id,
           target_password_recovery_id,
           operation,
           reason,
           created_at_ms
         )
         SELECT
           ?1,
           ?2,
           'PASSWORD_RECOVERY_REVIEW',
           (
             SELECT id
               FROM auth_accounts
              WHERE normalized_email = ?3
              LIMIT 1
           ),
           ?4,
           ?5,
           ?6,
           ?7
         WHERE changes() = 1`
      ).bind(
        crypto.randomUUID(),
        auth.account.id,
        row.email,
        requestId,
        status,
        status,
        nowMs
      )
    ]);

    if (
      Number(
        results[0]?.meta?.changes || 0
      ) !== 1
    ) {
      return json(
        {
          error:
            'recovery_request_not_pending'
        },
        409
      );
    }

    if (
      Number(
        results[1]?.meta?.changes || 0
      ) !== 1
    ) {
      return json(
        { error: 'audit_unavailable' },
        500
      );
    }

    return json({
      ok: true,
      status
    });
  }

  if (
    request.method === 'POST' &&
    path === '/v1/password-recovery/request'
  ) {
    const body = await readJson(request);

    const email = normalizeEmail(
      body.email
    );

    if (!isValidEmail(email)) {
      return json(
        { error: 'invalid_email' },
        400
      );
    }

    const nowMs = Date.now();

    if (
      !await allowRequest(
        env.DB,
        email,
        nowMs,
        env
      )
    ) {
      return json(
        { error: 'too_many_requests' },
        429
      );
    }

    const pending =
      await env.DB.prepare(
        `SELECT id
           FROM password_recovery_requests_v2
          WHERE normalized_email = ?1
            AND status = 'PENDING'
          ORDER BY requested_at_ms DESC
          LIMIT 1`
      ).bind(email).first();

    if (pending?.id) {
      return json({
        ok: true,
        recoveryRequestId:
          pending.id,
        status: 'PENDING'
      });
    }

    const id =
      crypto.randomUUID();

    await env.DB.prepare(
      `INSERT INTO password_recovery_requests_v2(
         id,
         normalized_email,
         status,
         requested_at_ms,
         reviewed_at_ms,
         completed_at_ms
       )
       VALUES (
         ?1,
         ?2,
         'PENDING',
         ?3,
         NULL,
         NULL
       )`
    ).bind(
      id,
      email,
      nowMs
    ).run();

    return json({
      ok: true,
      recoveryRequestId: id,
      status: 'PENDING'
    });
  }

  if (
    request.method === 'POST' &&
    path === '/v1/password-recovery/complete'
  ) {
    const body = await readJson(request);

    const id = text(
      body.recoveryRequestId,
      128
    );

    const password =
      typeof body.newPassword === 'string'
        ? body.newPassword
        : '';

    if (
      !id ||
      password.length < 8 ||
      password.length > 256
    ) {
      return json(
        { error: 'invalid_request' },
        400
      );
    }

    const recovery =
      await env.DB.prepare(
        `SELECT
           normalized_email AS email
           FROM password_recovery_requests_v2
          WHERE id = ?1
            AND status = 'APPROVED'`
      ).bind(id).first();

    if (!recovery) {
      return json(
        { error: 'recovery_not_approved' },
        400
      );
    }

    const passwordData =
      await hashPassword(password);

    const nowMs = Date.now();

    const results = await env.DB.batch([
      env.DB.prepare(
        `UPDATE password_recovery_requests_v2
            SET status = 'COMPLETED',
                completed_at_ms = ?1
          WHERE id = ?2
            AND status = 'APPROVED'`
      ).bind(
        nowMs,
        id
      ),

      env.DB.prepare(
        `UPDATE auth_accounts
            SET password_hash_b64 = ?1,
                password_salt_b64 = ?2,
                updated_at_ms = ?3
          WHERE normalized_email = ?4
            AND disabled_at_ms IS NULL`
      ).bind(
        passwordData.hashB64,
        passwordData.saltB64,
        nowMs,
        recovery.email
      ),

      env.DB.prepare(
        `UPDATE auth_sessions
            SET revoked_at_ms = ?1
          WHERE account_id = (
            SELECT id
              FROM auth_accounts
             WHERE normalized_email = ?2
          )
            AND revoked_at_ms IS NULL`
      ).bind(
        nowMs,
        recovery.email
      )
    ]);

    if (
      Number(
        results[0]?.meta?.changes || 0
      ) !== 1
    ) {
      return json(
        { error: 'recovery_not_approved' },
        409
      );
    }

    if (
      Number(
        results[1]?.meta?.changes || 0
      ) !== 1
    ) {
      return json(
        { error: 'account_not_found' },
        404
      );
    }

    return json({
      ok: true,
      status: 'COMPLETED'
    });
  }

  if (
    request.method === 'POST' &&
    path === '/v1/password-recovery/status'
  ) {
    const body = await readJson(request);

    const id = text(
      body.recoveryRequestId,
      128
    );

    if (!id) {
      return json(
        { error: 'invalid_request' },
        400
      );
    }

    const row = await env.DB.prepare(
      `SELECT
         status,
         normalized_email AS email
         FROM password_recovery_requests_v2
        WHERE id = ?1`
    ).bind(id).first();

    if (!row) {
      return json(
        {
          error:
            'recovery_request_not_found'
        },
        404
      );
    }

    return json({
      ok: true,
      status: row.status,
      email: row.email
    });
  }

  return json(
    { error: 'not_found' },
    404
  );
}

export default {
  async fetch(request, env) {
    try {
      return withCors(
        await handle(request, env)
      );
    } catch (error) {
      console.error(error);

      return withCors(
        json(
          {
            error:
              'internal_server_error'
          },
          500
        )
      );
    }
  }
};
