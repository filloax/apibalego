package com.ruslan.apibalego.http

interface ResponseSender {
    fun sendSuccess(extraItems: Map<String, Any> = mapOf())
    fun sendFailure(reason: String? = null)
}
