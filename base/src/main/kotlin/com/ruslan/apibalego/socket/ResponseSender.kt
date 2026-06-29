package com.ruslan.apibalego.socket

interface ResponseSender {
    fun sendSuccess(extraItems: Map<String, Any> = mapOf())
    fun sendFailure(reason: String? = null)
}