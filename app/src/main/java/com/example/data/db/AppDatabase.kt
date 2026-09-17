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
        PaymentAccountEntity::class
    ],
    version = 2,
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

    companion object {
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
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
