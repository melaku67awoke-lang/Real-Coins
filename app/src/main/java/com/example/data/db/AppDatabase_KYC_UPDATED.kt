package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserEntity::class,
        WalletEntity::class,
        KycEntity::class,
        KycAuditLogEntity::class,
        DepositEntity::class,
        WithdrawalEntity::class,
        LedgerEntryEntity::class,
        P2PAdEntity::class,
        P2POrderEntity::class,
        RewardClaimEntity::class,
        SpinStateEntity::class,
        PaymentAccountEntity::class,
        AppSettingEntity::class,
        PasswordResetSessionEntity::class,
        HelpRequestEntity::class,
        P2PChatMessageEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun paymentAccountDao(): PaymentAccountDao
    abstract fun walletDao(): WalletDao
    abstract fun kycDao(): KycDao
    abstract fun depositDao(): DepositDao
    abstract fun withdrawalDao(): WithdrawalDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun p2pDao(): P2PDao
    abstract fun rewardDao(): RewardDao
    abstract fun spinStateDao(): SpinStateDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun passwordResetSessionDao(): PasswordResetSessionDao
    abstract fun helpRequestDao(): HelpRequestDao
    abstract fun p2pChatDao(): P2PChatDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS payment_accounts (id TEXT NOT NULL, userId TEXT NOT NULL, paymentName TEXT NOT NULL, paymentMethod TEXT NOT NULL, accountNumber TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("ALTER TABLE p2p_ads ADD COLUMN paymentName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE p2p_ads ADD COLUMN accountNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE p2p_orders ADD COLUMN paymentName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE p2p_orders ADD COLUMN accountNumber TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE p2p_orders ADD COLUMN expiresAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE p2p_orders SET expiresAt = createdAt + 1500000 WHERE expiresAt = 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (`key` TEXT NOT NULL, value REAL NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL("INSERT OR IGNORE INTO app_settings (`key`, value, updatedAt) VALUES ('REAL_COIN_USD_VALUE', 0.0027, strftime('%s','now') * 1000)")
                db.execSQL("INSERT OR IGNORE INTO app_settings (`key`, value, updatedAt) VALUES ('USD_TO_ETB', 186.0, strftime('%s','now') * 1000)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS password_reset_sessions (`id` TEXT NOT NULL, `normalizedEmail` TEXT NOT NULL, `authorizationTokenHash` TEXT NOT NULL, `issuedAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, `usedAt` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_password_reset_sessions_normalizedEmail` ON `password_reset_sessions` (`normalizedEmail`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_password_reset_sessions_expiresAt` ON `password_reset_sessions` (`expiresAt`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE kyc_records ADD COLUMN documentUri TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE p2p_ads ADD COLUMN minOrderReal REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE p2p_ads ADD COLUMN maxOrderReal REAL NOT NULL DEFAULT 0.0")
                db.execSQL("UPDATE p2p_ads SET maxOrderReal = cryptoAmount WHERE maxOrderReal = 0.0")
                db.execSQL("ALTER TABLE p2p_orders ADD COLUMN disputeReason TEXT")
                db.execSQL("ALTER TABLE p2p_orders ADD COLUMN disputedAt INTEGER")
                db.execSQL("ALTER TABLE p2p_orders ADD COLUMN resolvedAt INTEGER")
                db.execSQL("ALTER TABLE p2p_orders ADD COLUMN resolvedByAdminId TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS help_requests (id TEXT NOT NULL, userId TEXT NOT NULL, category TEXT NOT NULL, message TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, resolvedAt INTEGER, resolvedByAdminId TEXT, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_help_requests_userId ON help_requests(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_help_requests_status ON help_requests(status)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE p2p_ads ADD COLUMN minOrderEtb REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE p2p_ads ADD COLUMN maxOrderEtb REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE p2p_orders ADD COLUMN fiatOrderAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("UPDATE p2p_ads SET minOrderEtb = CASE WHEN cryptoAmount > 0 THEN minOrderReal * (fiatPrice / cryptoAmount) ELSE 0.0 END")
                db.execSQL("UPDATE p2p_ads SET maxOrderEtb = CASE WHEN cryptoAmount > 0 THEN maxOrderReal * (fiatPrice / cryptoAmount) ELSE 0.0 END")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE p2p_orders ADD COLUMN paymentProofUri TEXT")
                db.execSQL("ALTER TABLE p2p_orders ADD COLUMN paidAt INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS p2p_chat_messages (id TEXT NOT NULL, orderId TEXT NOT NULL, senderId TEXT NOT NULL, senderName TEXT NOT NULL, message TEXT NOT NULL, attachmentUri TEXT, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_p2p_chat_messages_orderId_createdAt ON p2p_chat_messages(orderId, createdAt)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE p2p_ads ADD COLUMN originalMaxOrderEtb REAL NOT NULL DEFAULT 0.0")
                db.execSQL("UPDATE p2p_ads SET originalMaxOrderEtb = maxOrderEtb WHERE originalMaxOrderEtb = 0.0")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wallets ADD COLUMN bonusRealBalance REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE deposits ADD COLUMN depositUsd REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE deposits ADD COLUMN bonusReal REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN referralCode TEXT")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS spin_states (`userId` TEXT NOT NULL, `lastFreeSpinAt` INTEGER, PRIMARY KEY(`userId`))")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE app_settings SET value = 186.0 WHERE key = 'USD_TO_ETB' AND value = 187.0")
                db.execSQL("INSERT OR IGNORE INTO app_settings (`key`, value, updatedAt) VALUES ('USD_TO_ETB', 186.0, strftime('%s','now') * 1000)")
            }
        }

        // KYC schema update:
        // Adds the selected ID type and the required front/back document URIs.
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE kyc_records ADD COLUMN idType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE kyc_records ADD COLUMN frontIdUri TEXT")
                db.execSQL("ALTER TABLE kyc_records ADD COLUMN backIdUri TEXT")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "realcoin_database.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15
                    )
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
