package com.locqar.kiosk.network.model

import kotlinx.serialization.Serializable

// ---------- Request envelope ----------

@Serializable
data class WinnsenRequest(
    val action: String,
    val phone: String? = null,
    val password: String? = null,
    val qrToken: String? = null,
    val studentId: String? = null,
    val lockerSN: String? = null,
    val boxSN: String? = null,
    val agentId: String? = null,
    val memberId: String? = null,
    val orderNumber: String? = null,
    val owner: String? = null,
    val doorNum: Int? = null,
    val doorOpenCode: String? = null,
    val timestamp: Long? = null,
    val storageSize: String? = null,
    val storageDurationHours: Int? = null,
)

// ---------- Response models ----------

@Serializable
data class AgentLoginResponse(val agentId: String)

@Serializable
data class MemberLoginResponse(
    val memberId: String,
    val isSubscriber: Boolean? = null,
)

@Serializable
data class OrderListResponse(
    val orderNumbers: List<String>,
    val lockerSN: String,
)

@Serializable
data class ValidateOrderResponse(
    val agentId: String? = null,
    val lockerSN: String? = null,
    val orderNumber: String? = null,
)

@Serializable
data class ReuseDoorResponse(
    val hasDoor: Boolean,
    val lockerSN: String,
    val doorNum: Int? = null,
)

@Serializable
data class MemberPackageResponse(
    val hasPackage: Boolean,
    val lockerSN: String,
    val doorNum: String? = null,
)

@Serializable
data class PaymentCheckResponse(val hasPayment: Boolean)

@Serializable
data class PaymentPageResponse(
    val url: String,
    val expiredAt: Long,
    val orderNumber: String,
)

@Serializable
data class StorageOrderResponse(
    val orderCode: String,
    val storageSize: String? = null,
    val lockerCode: String? = null,
    val isSubscriber: Boolean? = null,
    val amountPesewas: Int? = null,
    val amountGHS: Double? = null,
    val currency: String? = null,
    val url: String? = null,
    val expiredAt: Long? = null,
)

@Serializable
data class ValidateBoxOrderResponse(
    val orderNumber: String,
    val boxSN: String,
)

@Serializable
data class ApiError(
    val statusCode: Int,
    val message: String,
    val error: String? = null,
)
