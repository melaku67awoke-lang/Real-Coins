package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        PaymentAccountEntity::class,
        AppSettingEntity::class,
        PasswordResetSessionEntity::class
    ],
    version = 5,
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
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun passwordResetSessionDao(): PasswordResetSessionDao

    companion object {
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE p2p_orders ADD COLUMN expiresAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE p2p_orders SET expiresAt = createdAt + 1500000 WHERE expiresAt = 0")
            }
        }
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS password_reset_sessions (`id` TEXT NOT NULL, `normalizedEmail` TEXT NOT NULL, `authorizationTokenHash` TEXT NOT NULL, `issuedAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, `usedAt` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_password_reset_sessions_normalizedEmail` ON `password_reset_sessions` (`normalizedEmail`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_password_reset_sessions_expiresAt` ON `password_reset_sessions` (`expiresAt`)")
            }
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (`key` TEXT NOT NULL, value REAL NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL("INSERT OR IGNORE INTO app_settings (`key`, value, updatedAt) VALUES ('REAL_COIN_USD_VALUE', 0.0027, strftime('%s','now') * 1000)")
                db.execSQL("INSERT OR IGNORE INTO app_settings (`key`, value, updatedAt) VALUES ('USD_TO_ETB', 187.0, strftime('%s','now') * 1000)")
            }
        }
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS payment_accounts (id TEXT NOT NULL, userId TEXT NOT NULL, paymentName TEXT NOT NULL, paymentMethod TEXT NOT NULL, accountNumber TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("ALTER TABLE p2p_ads ADD COLUMN paymentName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE p2p_ads ADD COLUMN accountNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE p2p_orders ADD COLUMN paymentName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE p2p_orders ADD COLUMN accountNumber TEXT NOT NULL DEFAULT ''")
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
