package com.locqar.kiosk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.locqar.kiosk.hardware.service.LockerDaemonService
import com.locqar.kiosk.network.api.DashboardApiClient
import com.locqar.kiosk.ui.screens.agent.AgentLoginScreen
import com.locqar.kiosk.ui.screens.agent.AgentOrderListScreen
import com.locqar.kiosk.ui.screens.common.CompletionScreen
import com.locqar.kiosk.ui.screens.common.DoorOpenScreen
import com.locqar.kiosk.ui.screens.guest.GuestOrderScreen
import com.locqar.kiosk.ui.screens.home.HomeScreen
import com.locqar.kiosk.ui.screens.member.MemberLoginScreen
import com.locqar.kiosk.ui.screens.member.MemberMenuScreen
import com.locqar.kiosk.viewmodel.KioskScreen
import com.locqar.kiosk.viewmodel.KioskViewModel

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var daemon: LockerDaemonService? = null
    private lateinit var apiClient: DashboardApiClient

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            daemon = (service as LockerDaemonService.DaemonBinder).getService()
            daemon?.apply {
                // TODO: Load station number from settings/config
                setStationNumber(1)
                // Start in demo mode by default — toggle off when real hardware is connected
                setDemoMode(true)
                startPolling()
            }
            Log.i(TAG, "Daemon service bound")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            daemon = null
            Log.w(TAG, "Daemon service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on for kiosk mode
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize API client
        apiClient = DashboardApiClient(
            baseUrl = BuildConfig.API_BASE_URL,
            apiKey = BuildConfig.API_KEY,
        )

        // Start and bind to daemon service
        val serviceIntent = Intent(this, LockerDaemonService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val vm: KioskViewModel = viewModel()

                    // Inject dependencies
                    vm.daemon = daemon
                    vm.api = apiClient
                    // TODO: Load from device config / settings
                    vm.lockerSN = "20422469802A-001"

                    // Observe state
                    val screen by vm.currentScreen.collectAsState()
                    val isLoading by vm.isLoading.collectAsState()
                    val error by vm.errorMessage.collectAsState()
                    val success by vm.successMessage.collectAsState()
                    val orderNumbers by vm.orderNumbers.collectAsState()
                    val assignedDoor by vm.assignedDoor.collectAsState()
                    val doorIsOpen by vm.doorIsOpen.collectAsState()
                    val isSub by vm.isSubscriber.collectAsState()

                    when (screen) {
                        KioskScreen.HOME -> HomeScreen(
                            onNavigate = { vm.navigateTo(it) }
                        )

                        KioskScreen.AGENT_LOGIN -> AgentLoginScreen(
                            isLoading = isLoading,
                            errorMessage = error,
                            onLogin = { phone, pwd -> vm.agentLogin(phone, pwd) },
                            onBack = { vm.resetSession() },
                        )

                        KioskScreen.AGENT_ORDER_LIST -> AgentOrderListScreen(
                            orderNumbers = orderNumbers,
                            isLoading = isLoading,
                            errorMessage = error,
                            onSelectOrder = { vm.agentSelectOrder(it) },
                            onBack = { vm.resetSession() },
                        )

                        KioskScreen.MEMBER_LOGIN -> MemberLoginScreen(
                            isLoading = isLoading,
                            errorMessage = error,
                            onLogin = { phone, pwd -> vm.memberLogin(phone, pwd) },
                            onBack = { vm.resetSession() },
                        )

                        KioskScreen.MEMBER_MENU -> MemberMenuScreen(
                            isSubscriber = isSub,
                            onPickupPackage = { vm.memberCheckPackage() },
                            onCreateStorage = { /* TODO: storage flow */ },
                            onBack = { vm.resetSession() },
                        )

                        KioskScreen.GUEST_ORDER_ENTRY -> GuestOrderScreen(
                            isLoading = isLoading,
                            errorMessage = error,
                            onSubmit = { vm.guestEnterOrderNumber(it) },
                            onBack = { vm.resetSession() },
                        )

                        KioskScreen.DOOR_OPEN -> DoorOpenScreen(
                            doorNum = assignedDoor,
                            isLoading = isLoading,
                            doorIsOpen = doorIsOpen,
                            errorMessage = error,
                            successMessage = success,
                            onOpenDoor = { vm.openDoorForDropoff(it) },
                            onBack = { vm.resetSession() },
                        )

                        KioskScreen.PAYMENT_QR -> {
                            // TODO: Payment QR screen with Paystack URL
                            CompletionScreen(
                                title = "Scan to Pay",
                                message = "Payment flow coming soon",
                                onDone = { vm.resetSession() },
                            )
                        }

                        KioskScreen.AGENT_DROPOFF_COMPLETE -> CompletionScreen(
                            title = "Package Deposited",
                            message = "The package has been stored. You may now proceed to the next order.",
                            onDone = { vm.resetSession() },
                        )

                        KioskScreen.COLLECTION_COMPLETE -> CompletionScreen(
                            title = "Package Collected",
                            message = "Thank you! The door has been secured.",
                            onDone = { vm.resetSession() },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        unbindService(serviceConnection)
        apiClient.close()
        super.onDestroy()
    }
}
