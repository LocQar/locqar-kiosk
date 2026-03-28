package com.locqar.kiosk.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locqar.kiosk.hardware.controller.OpenResult
import com.locqar.kiosk.hardware.controller.SafeOpenResult
import com.locqar.kiosk.hardware.service.LockerDaemonService
import com.locqar.kiosk.network.api.DashboardApiClient
import com.locqar.kiosk.network.api.DashboardApiClient.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordinates between the cloud API and local hardware.
 *
 * This is the "bridge" — it decides when to call the API,
 * when to fire RS485 commands, and in what order.
 */
class KioskViewModel : ViewModel() {

    companion object {
        private const val TAG = "KioskVM"
    }

    // Injected after service binds
    var daemon: LockerDaemonService? = null
    var api: DashboardApiClient? = null
    var lockerSN: String = "" // This kiosk's locker serial number

    // ---------- Session state ----------

    private val _currentScreen = MutableStateFlow(KioskScreen.HOME)
    val currentScreen = _currentScreen.asStateFlow()

    // Agent session
    private val _agentId = MutableStateFlow<String?>(null)
    val agentId = _agentId.asStateFlow()

    // Member session
    private val _memberId = MutableStateFlow<String?>(null)
    val memberId = _memberId.asStateFlow()

    private val _isSubscriber = MutableStateFlow(false)
    val isSubscriber = _isSubscriber.asStateFlow()

    // UI state
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage = _successMessage.asStateFlow()

    // Order state
    private val _orderNumbers = MutableStateFlow<List<String>>(emptyList())
    val orderNumbers = _orderNumbers.asStateFlow()

    private val _currentOrderNumber = MutableStateFlow<String?>(null)
    val currentOrderNumber = _currentOrderNumber.asStateFlow()

    private val _assignedDoor = MutableStateFlow<Int?>(null)
    val assignedDoor = _assignedDoor.asStateFlow()

    // Payment state
    private val _paymentUrl = MutableStateFlow<String?>(null)
    val paymentUrl = _paymentUrl.asStateFlow()

    // Door state
    private val _doorIsOpen = MutableStateFlow(false)
    val doorIsOpen = _doorIsOpen.asStateFlow()

    // ===================================================================
    // AGENT FLOW
    // ===================================================================

    /**
     * Flow 1, Step 1: Agent enters phone + password.
     * Calls API → gets agentId → navigates to order list.
     */
    fun agentLogin(phone: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            when (val result = api?.agentLoginByPhone(phone, password, lockerSN)) {
                is ApiResult.Success -> {
                    _agentId.value = result.data.agentId
                    Log.i(TAG, "Agent logged in: ${result.data.agentId}")
                    // Fetch order list immediately
                    loadAgentOrders()
                    _currentScreen.value = KioskScreen.AGENT_ORDER_LIST
                }
                is ApiResult.ApiFailure -> {
                    _errorMessage.value = result.error.message
                }
                is ApiResult.NetworkError -> {
                    _errorMessage.value = "Network error. Please try again."
                }
                null -> _errorMessage.value = "Service not ready"
            }
            _isLoading.value = false
        }
    }

    private suspend fun loadAgentOrders() {
        val aid = _agentId.value ?: return
        when (val result = api?.agentListOrders(aid, lockerSN)) {
            is ApiResult.Success -> _orderNumbers.value = result.data.orderNumbers
            else -> { /* handled on UI */ }
        }
    }

    /**
     * Flow 1, Step 2: Agent selects or enters order number.
     * Validates order → checks for reuse door → assigns door → opens it.
     */
    fun agentSelectOrder(orderNumber: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _currentOrderNumber.value = orderNumber
            val aid = _agentId.value ?: return@launch

            // Validate order
            when (val result = api?.agentValidateOrder(aid, orderNumber, lockerSN)) {
                is ApiResult.Success -> {
                    Log.i(TAG, "Order validated: $orderNumber")
                }
                is ApiResult.ApiFailure -> {
                    _errorMessage.value = result.error.message
                    _isLoading.value = false
                    return@launch
                }
                else -> {
                    _errorMessage.value = "Network error"
                    _isLoading.value = false
                    return@launch
                }
            }

            // Check for reuse door
            when (val reuse = api?.agentReuseDoor(aid, orderNumber, lockerSN)) {
                is ApiResult.Success -> {
                    if (reuse.data.hasDoor && reuse.data.doorNum != null) {
                        _assignedDoor.value = reuse.data.doorNum
                        Log.i(TAG, "Reusing door ${reuse.data.doorNum}")
                    }
                }
                else -> { /* no reuse, assign new door */ }
            }

            // Navigate to door open screen
            _currentScreen.value = KioskScreen.DOOR_OPEN
            _isLoading.value = false
        }
    }

    /**
     * Flow 1, Step 3: Open the assigned door via RS485.
     * After door closes, report order-dropoff to API.
     */
    fun openDoorForDropoff(doorNum: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _assignedDoor.value = doorNum

            // Fire RS485 open command
            when (daemon?.safeOpenDoor(doorNum)) {
                is SafeOpenResult.Confirmed -> {
                    _doorIsOpen.value = true
                    _successMessage.value = "Door $doorNum is open. Place package and close door."
                    Log.i(TAG, "Door $doorNum opened for dropoff")

                    // Wait for door to close (poll)
                    waitForDoorClose(doorNum)

                    // Report to cloud API
                    api?.orderDropoff(
                        owner = "agent",
                        lockerSN = lockerSN,
                        doorNum = doorNum,
                        orderNumber = _currentOrderNumber.value,
                        agentId = _agentId.value
                    )
                    Log.i(TAG, "Dropoff reported for order ${_currentOrderNumber.value}")
                    _currentScreen.value = KioskScreen.AGENT_DROPOFF_COMPLETE
                }
                is SafeOpenResult.OpenFailed -> {
                    _errorMessage.value = "Door failed to open. Try another compartment."
                }
                is SafeOpenResult.NotConnected -> {
                    _errorMessage.value = "Hardware not connected."
                }
                else -> {
                    _errorMessage.value = "Could not confirm door opened."
                }
            }
            _isLoading.value = false
            _doorIsOpen.value = false
        }
    }

    // ===================================================================
    // MEMBER FLOW
    // ===================================================================

    fun memberLogin(phone: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            when (val result = api?.memberLoginByPhone(phone, password, lockerSN)) {
                is ApiResult.Success -> {
                    _memberId.value = result.data.memberId
                    _isSubscriber.value = result.data.isSubscriber ?: false
                    Log.i(TAG, "Member logged in: ${result.data.memberId}")
                    _currentScreen.value = KioskScreen.MEMBER_MENU
                }
                is ApiResult.ApiFailure -> {
                    _errorMessage.value = result.error.message
                }
                is ApiResult.NetworkError -> {
                    _errorMessage.value = "Network error. Please try again."
                }
                null -> _errorMessage.value = "Service not ready"
            }
            _isLoading.value = false
        }
    }

    /**
     * Member checks if they have a package waiting.
     */
    fun memberCheckPackage() {
        viewModelScope.launch {
            _isLoading.value = true
            val mid = _memberId.value ?: return@launch

            when (val result = api?.memberPackage(mid, lockerSN)) {
                is ApiResult.Success -> {
                    if (result.data.hasPackage && result.data.doorNum != null) {
                        _assignedDoor.value = result.data.doorNum.toIntOrNull()
                        _currentScreen.value = KioskScreen.DOOR_OPEN
                    } else {
                        _errorMessage.value = "No package found at this locker."
                    }
                }
                is ApiResult.ApiFailure -> _errorMessage.value = result.error.message
                else -> _errorMessage.value = "Network error"
            }
            _isLoading.value = false
        }
    }

    /**
     * Open door for member to collect package.
     */
    fun openDoorForCollection(doorNum: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _assignedDoor.value = doorNum

            when (daemon?.safeOpenDoor(doorNum)) {
                is SafeOpenResult.Confirmed -> {
                    _doorIsOpen.value = true
                    _successMessage.value = "Door $doorNum is open. Collect your package."

                    waitForDoorClose(doorNum)

                    api?.orderCollected(
                        owner = if (_agentId.value != null) "agent" else
                            if (_memberId.value != null) "member" else "guest",
                        lockerSN = lockerSN,
                        doorNum = doorNum,
                        orderNumber = _currentOrderNumber.value,
                        agentId = _agentId.value,
                        memberId = _memberId.value
                    )
                    Log.i(TAG, "Collection reported")
                    _currentScreen.value = KioskScreen.COLLECTION_COMPLETE
                }
                else -> {
                    _errorMessage.value = "Door failed to open."
                }
            }
            _isLoading.value = false
            _doorIsOpen.value = false
        }
    }

    // ===================================================================
    // MEMBER STORAGE FLOW
    // ===================================================================

    /**
     * Member creates a storage order.
     * If subscriber → free, door opens immediately.
     * If not subscriber → generate payment URL → show QR.
     */
    fun memberCreateStorage(storageSize: String = "small", durationHours: Int = 24) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val mid = _memberId.value ?: return@launch

            when (val result = api?.memberCreateStorageOrder(mid, lockerSN, storageSize, durationHours)) {
                is ApiResult.Success -> {
                    _currentOrderNumber.value = result.data.orderCode
                    if (result.data.isSubscriber == true || result.data.url == null) {
                        // Free storage — go straight to door
                        _currentScreen.value = KioskScreen.DOOR_OPEN
                    } else {
                        // Payment required
                        _paymentUrl.value = result.data.url
                        _currentScreen.value = KioskScreen.PAYMENT_QR
                    }
                }
                is ApiResult.ApiFailure -> _errorMessage.value = result.error.message
                else -> _errorMessage.value = "Network error"
            }
            _isLoading.value = false
        }
    }

    // ===================================================================
    // GUEST FLOW
    // ===================================================================

    fun guestEnterOrderNumber(orderNumber: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _currentOrderNumber.value = orderNumber

            // Check if payment is required
            when (val result = api?.orderPayment(orderNumber, lockerSN)) {
                is ApiResult.Success -> {
                    if (result.data.hasPayment) {
                        // Payment done — go to pickup
                        _currentScreen.value = KioskScreen.DOOR_OPEN
                    } else {
                        // Need payment — generate URL
                        generatePayment(orderNumber)
                    }
                }
                is ApiResult.ApiFailure -> _errorMessage.value = result.error.message
                else -> _errorMessage.value = "Network error"
            }
            _isLoading.value = false
        }
    }

    private suspend fun generatePayment(orderNumber: String) {
        when (val result = api?.generatePaymentPage(orderNumber, lockerSN)) {
            is ApiResult.Success -> {
                _paymentUrl.value = result.data.url
                _currentScreen.value = KioskScreen.PAYMENT_QR
            }
            is ApiResult.ApiFailure -> _errorMessage.value = result.error.message
            else -> _errorMessage.value = "Failed to generate payment"
        }
    }

    // ===================================================================
    // PAYMENT POLLING
    // ===================================================================

    /**
     * Called periodically from PaymentQrScreen to check if payment is done.
     * When payment completes, navigates to DOOR_OPEN.
     */
    fun checkPaymentStatus() {
        viewModelScope.launch {
            val order = _currentOrderNumber.value ?: return@launch

            when (val result = api?.orderPayment(order, lockerSN)) {
                is ApiResult.Success -> {
                    if (result.data.hasPayment) {
                        Log.i(TAG, "Payment confirmed for $order")
                        _currentScreen.value = KioskScreen.DOOR_OPEN
                    }
                }
                else -> { /* keep polling */ }
            }
        }
    }

    // ===================================================================
    // HELPERS
    // ===================================================================

    /**
     * Poll until the specified door closes (user shut it).
     */
    private suspend fun waitForDoorClose(doorNum: Int) {
        val maxWait = 120_000L // 2 minutes max
        val deadline = System.currentTimeMillis() + maxWait

        while (System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(1000) // check every second
            daemon?.pollOnce()
            val states = daemon?.doorStates?.value ?: continue
            val isOpen = states[doorNum] ?: false
            if (!isOpen) {
                Log.i(TAG, "Door $doorNum closed")
                return
            }
        }
        Log.w(TAG, "Door $doorNum close timeout (${maxWait}ms)")
    }

    /**
     * Reset session and return to home screen.
     */
    fun resetSession() {
        _agentId.value = null
        _memberId.value = null
        _isSubscriber.value = false
        _currentOrderNumber.value = null
        _assignedDoor.value = null
        _paymentUrl.value = null
        _errorMessage.value = null
        _successMessage.value = null
        _doorIsOpen.value = false
        _orderNumbers.value = emptyList()
        _currentScreen.value = KioskScreen.HOME
    }

    fun clearError() { _errorMessage.value = null }
    fun navigateTo(screen: KioskScreen) { _currentScreen.value = screen }
}

enum class KioskScreen {
    HOME,
    SETTINGS,
    AGENT_LOGIN,
    AGENT_ORDER_LIST,
    MEMBER_LOGIN,
    MEMBER_MENU,
    GUEST_ORDER_ENTRY,
    DOOR_OPEN,
    PAYMENT_QR,
    AGENT_DROPOFF_COMPLETE,
    COLLECTION_COMPLETE,
}
