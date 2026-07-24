package com.flipmate.app.data.local
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.flipmate.app.data.local.dao.FlipLogDao
import com.flipmate.app.data.local.entity.FlipLogEntity
@Database(entities=[FlipLogEntity::class],version=1,exportSchema=false)
abstract class AppDatabase:RoomDatabase(){abstract fun flipLogDao():FlipLogDao; companion object{ @Volatile private var instance:AppDatabase?=null; fun get(context:Context)=instance?: synchronized(this){instance?:Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,"flipmate.db").build().also{instance=it}}}}
