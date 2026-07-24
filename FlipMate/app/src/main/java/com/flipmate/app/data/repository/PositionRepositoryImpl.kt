package com.flipmate.app.data.repository

import com.flipmate.app.core.network.PrivateApiService
import com.flipmate.app.core.util.toDecimalOrZero
import com.flipmate.app.domain.model.OpenPosition
import com.flipmate.app.domain.model.PositionSide
import com.flipmate.app.domain.repository.PositionRepository
import org.json.JSONObject

class PositionRepositoryImpl(private val api: PrivateApiService): PositionRepository {
    override suspend fun getOpenPositions(): List<OpenPosition> = api.openPositions().let { array ->
        (0 until array.length()).mapNotNull { i ->
            val x=array.optJSONObject(i) ?: return@mapNotNull null
            val side=when(x.optInt("positionType",0)){1->PositionSide.LONG;2->PositionSide.SHORT;else->return@mapNotNull null}
            OpenPosition(
                x.optString("positionId").toLongOrNull() ?: return@mapNotNull null,
                x.optString("symbol"),
                x.opt("holdVol").toDecimalOrZero(),
                side,
                x.opt("openAvgPrice").toDecimalOrZero(),
                x.opt("liquidatePrice").toDecimalOrZero(),
                x.opt("im").toDecimalOrZero(),
                x.optInt("leverage", 10),
                x.opt("unRealizedPnl").toDecimalOrZero()
            )
        }
    }
    override suspend fun createOrder(symbol:String,side:Int,volume:String,price:String,positionId:Long?):String { val body=JSONObject().put("symbol",symbol).put("side",side).put("type",5).put("vol",volume).put("price",price).put("openType",1); if(positionId!=null) body.put("positionId",positionId); return api.createOrder(body).optString("orderId") }
    override suspend fun reverse(symbol:String,positionId:Long,volume:String):Boolean { api.reversePosition(JSONObject().put("symbol",symbol).put("positionId",positionId).put("vol",volume)); return true }
}
