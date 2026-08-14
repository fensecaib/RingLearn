package com.ringlearn.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ringlearn.app.data.local.dao.AiChatDao
import com.ringlearn.app.data.local.dao.ReviewLogDao
import com.ringlearn.app.data.local.dao.WordDao
import com.ringlearn.app.data.local.entity.AiChatEntity
import com.ringlearn.app.data.local.entity.ReviewLogEntity
import com.ringlearn.app.data.local.entity.WordEntity

@Database(
    entities = [WordEntity::class, ReviewLogEntity::class, AiChatEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun aiChatDao(): AiChatDao

    companion object {
        /** v1 → v2：新增 AI 对话消息表（保留单词/复习日志数据，不清库） */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_chat_messages` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` TEXT NOT NULL, " +
                        "`role` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`isError` INTEGER NOT NULL)"
                )
            }
        }

        /** v2 → v3：为 IME 假名前缀 / 到期复习 / 新词查询补二级索引（数据不动，id 不变）。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_words_kana` ON `words` (`kana`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_words_dueAt` ON `words` (`dueAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_words_reviewCount` ON `words` (`reviewCount`)")
            }
        }
    }
}
