package com.flipmate.app.data.local.entity
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName="flip_logs")
data class FlipLogEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val createdAt:Long,val mode:String,val symbol:String,val cycleNumber:Int,val flipMode:String,val previousSide:String,val previousVolume:String,val targetSide:String,val targetVolume:String,val estimatedPnl:String,val cyclePnlBefore:String,val cyclePnlAfter:String,val status:String,val orderId:String?,val mexcCode:Int?,val errorMessage:String?)
