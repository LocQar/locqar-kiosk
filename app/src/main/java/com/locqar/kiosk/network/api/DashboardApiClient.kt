package com.locqar.kiosk.network.api

import android.util.Log
import com.locqar.kiosk.network.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * HTTP client that bridges the kiosk to the LocQar cloud API.
 *
 * Calls POST /api/winnsen/events with the appropriate action and fields.
 * This replaces what Winnsen's kiosk software was supposed to do.
 *
 * @param baseUrl API base URL (e.g. "https://api.dev.locqar.com")
 * @param apiKey  Static API key for x-api-key header
 */
class DashboardApiClient(
    private val baseUrl: String,
    private val apiKey: String,
) {
    companion object {
        private const val TAG = "DashboardApi"
        private const val EVENTS_PATH = "/api/winnsen/events"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(this@DashboardApiClient.json)
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) { Log.d(TAG, message) }
            }
            level = LogLevel.BODY
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
        defaultRequest {
            header("x-api-key", apiKey)
            contentType(ContentType.Application.Json)
        }
    }

    // ---------- Sealed result type ----------

    sealed class ApiResult<out T> {
        data class Success<T>(val data: T) : ApiResult<T>()
        data class ApiFailure(val error: ApiError) : ApiResult<Nothing>()
        data class NetworkError(val message: String) : ApiResult<Nothing>()
    }

    private suspend inline fun <reified T> postEvent(request: WinnsenRequest): ApiResult<T> {
        return try {
            val response = client.post("$baseUrl$EVENTS_PATH") {
                setBody(request)
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body<T>())
            } else {
                val error = try {
                    response.body<ApiError>()
                } catch (_: Exception) {
                    ApiError(response.status.value, "HTTP ${response.status.value}", null)
                }
                ApiResult.ApiFailure(error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error", e)
            ApiResult.NetworkError(e.message ?: "Network error")
        }
    }

    // ===================================================================
    // AUTHENTICATION ACTIONS
    // ===================================================================

    /**
     * Agent logs in with phone number and password.
     * Returns agentId to store in kiosk session.
     */
    suspend fun agentLoginByPhone(
        phone: String, password: String, lockerSN: String
    ): ApiResult<AgentLoginResponse> = postEvent(
        WinnsenRequest(
            action = "agent-login-by-phone",
            phone = phone, password = password, lockerSN = lockerSN
        )
    )

    /**
     * Agent logs in by scanning QR code.
     */
    suspend fun agentLoginByQr(
        qrToken: String, lockerSN: String
    ): ApiResult<AgentLoginResponse> = postEvent(
        WinnsenRequest(
            action = "agent-login-by-qr",
            qrToken = qrToken, lockerSN = lockerSN
        )
    )

    /**
     * Member logs in with phone + password.
     */
    suspend fun memberLoginByPhone(
        phone: String, password: String, lockerSN: String
    ): ApiResult<MemberLoginResponse> = postEvent(
        WinnsenRequest(
            action = "member-login-by-phone",
            phone = phone, password = password, lockerSN = lockerSN
        )
    )

    /**
     * Member logs in by QR code.
     */
    suspend fun memberLoginByQr(
        qrToken: String, lockerSN: String
    ): ApiResult<MemberLoginResponse> = postEvent(
        WinnsenRequest(
            action = "member-login-by-qr",
            qrToken = qrToken, lockerSN = lockerSN
        )
    )

    /**
     * Subscriber (student) logs in with student ID + password.
     */
    suspend fun subscriberLogin(
        studentId: String, password: String, lockerSN: String
    ): ApiResult<MemberLoginResponse> = postEvent(
        WinnsenRequest(
            action = "subscriber-login",
            studentId = studentId, password = password, lockerSN = lockerSN
        )
    )

    // ===================================================================
    // ORDER / LOCKER QUERY ACTIONS
    // ===================================================================

    /**
     * Get all order numbers assigned to an agent at this locker.
     */
    suspend fun agentListOrders(
        agentId: String, lockerSN: String
    ): ApiResult<OrderListResponse> = postEvent(
        WinnsenRequest(
            action = "agent-list-orders",
            agentId = agentId, lockerSN = lockerSN
        )
    )

    /**
     * Validate that an order is eligible for processing at this locker.
     */
    suspend fun agentValidateOrder(
        agentId: String, orderNumber: String, lockerSN: String
    ): ApiResult<ValidateOrderResponse> = postEvent(
        WinnsenRequest(
            action = "agent-validate-order",
            agentId = agentId, orderNumber = orderNumber, lockerSN = lockerSN
        )
    )

    /**
     * Check if a door is already assigned to an order (for re-opening).
     */
    suspend fun agentReuseDoor(
        agentId: String, orderNumber: String, lockerSN: String
    ): ApiResult<ReuseDoorResponse> = postEvent(
        WinnsenRequest(
            action = "agent-reuse-door",
            agentId = agentId, orderNumber = orderNumber, lockerSN = lockerSN
        )
    )

    /**
     * Check if a member has a package waiting at this locker.
     */
    suspend fun memberPackage(
        memberId: String, lockerSN: String
    ): ApiResult<MemberPackageResponse> = postEvent(
        WinnsenRequest(
            action = "member-package",
            memberId = memberId, lockerSN = lockerSN
        )
    )

    /**
     * Validate an order for a dropbox terminal.
     */
    suspend fun validateBoxOrder(
        orderNumber: String, boxSN: String
    ): ApiResult<ValidateBoxOrderResponse> = postEvent(
        WinnsenRequest(
            action = "validate-box-order",
            orderNumber = orderNumber, boxSN = boxSN
        )
    )

    // ===================================================================
    // PAYMENT ACTIONS
    // ===================================================================

    /**
     * Check if payment has been recorded for an order.
     */
    suspend fun orderPayment(
        orderNumber: String, lockerSN: String
    ): ApiResult<PaymentCheckResponse> = postEvent(
        WinnsenRequest(
            action = "order-payment",
            orderNumber = orderNumber, lockerSN = lockerSN
        )
    )

    /**
     * Generate a Paystack checkout URL for display as QR code.
     */
    suspend fun generatePaymentPage(
        orderNumber: String, lockerSN: String
    ): ApiResult<PaymentPageResponse> = postEvent(
        WinnsenRequest(
            action = "generate-payment-page",
            orderNumber = orderNumber, lockerSN = lockerSN
        )
    )

    /**
     * Create a personal storage order for a member at the kiosk.
     */
    suspend fun memberCreateStorageOrder(
        memberId: String,
        lockerSN: String,
        storageSize: String,
        storageDurationHours: Int? = null,
    ): ApiResult<StorageOrderResponse> = postEvent(
        WinnsenRequest(
            action = "member-create-storage-order",
            memberId = memberId, lockerSN = lockerSN,
            storageSize = storageSize, storageDurationHours = storageDurationHours
        )
    )

    // ===================================================================
    // ORDER EVENT ACTIONS (fire after hardware completes)
    // ===================================================================

    /**
     * Report that a package was placed in a locker compartment.
     * Always returns 200 {} — errors are logged server-side.
     */
    suspend fun orderDropoff(
        owner: String,
        lockerSN: String,
        doorNum: Int,
        timestamp: Long = System.currentTimeMillis(),
        orderNumber: String? = null,
        agentId: String? = null,
        memberId: String? = null,
        doorOpenCode: String? = null,
    ): ApiResult<Unit> = try {
        client.post("$baseUrl$EVENTS_PATH") {
            setBody(WinnsenRequest(
                action = "order-dropoff",
                owner = owner, lockerSN = lockerSN, doorNum = doorNum,
                timestamp = timestamp, orderNumber = orderNumber,
                agentId = agentId, memberId = memberId, doorOpenCode = doorOpenCode
            ))
        }
        // Always treat as success — spec says always returns 200 {}
        ApiResult.Success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "order-dropoff network error (non-blocking)", e)
        ApiResult.NetworkError(e.message ?: "Network error")
    }

    /**
     * Report that a package was removed from a locker compartment.
     * Always returns 200 {} — errors are logged server-side.
     */
    suspend fun orderCollected(
        owner: String,
        lockerSN: String,
        doorNum: Int,
        timestamp: Long = System.currentTimeMillis(),
        orderNumber: String? = null,
        agentId: String? = null,
        memberId: String? = null,
    ): ApiResult<Unit> = try {
        client.post("$baseUrl$EVENTS_PATH") {
            setBody(WinnsenRequest(
                action = "order-collected",
                owner = owner, lockerSN = lockerSN, doorNum = doorNum,
                timestamp = timestamp, orderNumber = orderNumber,
                agentId = agentId, memberId = memberId
            ))
        }
        ApiResult.Success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "order-collected network error (non-blocking)", e)
        ApiResult.NetworkError(e.message ?: "Network error")
    }

    /**
     * member-create-storage-order: Member requests a locker compartment for storage.
     */
    suspend fun memberCreateStorageOrder(
        memberId: String,
        lockerSN: String,
        storageSize: String = "small",
        storageDurationHours: Int = 24,
    ): ApiResult<StorageOrderResponse> = try {
        val response = client.post("$baseUrl$EVENTS_PATH") {
            setBody(WinnsenRequest(
                action = "member-create-storage-order",
                memberId = memberId, lockerSN = lockerSN,
                storageSize = storageSize, storageDurationHours = storageDurationHours
            ))
        }
        parseResponse(response)
    } catch (e: Exception) {
        ApiResult.NetworkError(e.message ?: "Network error")
    }

    fun close() { client.close() }
}
