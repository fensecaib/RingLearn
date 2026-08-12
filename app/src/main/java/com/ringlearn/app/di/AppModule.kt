package com.ringlearn.app.di

import android.content.Context
import androidx.room.Room
import com.ringlearn.app.data.local.AppDatabase
import com.ringlearn.app.data.local.dao.AiChatDao
import com.ringlearn.app.data.local.dao.ReviewLogDao
import com.ringlearn.app.data.local.dao.WordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ringlearn.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            // 不启用破坏性回退：未来漏写 Migration 时开库直接抛异常暴露问题，绝不清空用户学习数据
            .build()

    @Provides
    fun provideWordDao(db: AppDatabase): WordDao = db.wordDao()

    @Provides
    fun provideReviewLogDao(db: AppDatabase): ReviewLogDao = db.reviewLogDao()

    @Provides
    fun provideAiChatDao(db: AppDatabase): AiChatDao = db.aiChatDao()
}
