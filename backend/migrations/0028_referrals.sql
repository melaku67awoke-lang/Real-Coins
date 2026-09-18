-- One-time referral relationship and reward guard.
ALTER TABLE auth_accounts ADD COLUMN referral_code TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS idx_auth_accounts_referral_code
    ON auth_accounts(referral_code) WHERE referral_code IS NOT NULL;

CREATE TABLE IF NOT EXISTS referrals (
    referred_account_id TEXT PRIMARY KEY,
    referrer_account_id TEXT NOT NULL,
    referral_code TEXT NOT NULL,
    created_at_ms INTEGER NOT NULL,
    first_deposit_rewarded_at_ms INTEGER,
    FOREIGN KEY (referred_account_id) REFERENCES auth_accounts(id),
    FOREIGN KEY (referrer_account_id) REFERENCES auth_accounts(id)
);
CREATE INDEX IF NOT EXISTS idx_referrals_referrer ON referrals(referrer_account_id);
