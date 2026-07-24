package com.flipmate.app.core.network

class ApiException(val httpCode: Int = -1, val exchangeCode: Int? = null, override val message: String) : Exception(message)
