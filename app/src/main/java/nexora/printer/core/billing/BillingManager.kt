package nexora.printer.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import nexora.printer.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Google Play subscription ("Nexora Printer Pro"). Printing is free; Pro removes the Nexora logo window shown while a POS ticket prints.
 * Only enforced in a build made with -PrequireSubscription=true (the Play Store build); every other build is fully unlocked.
 * The last confirmed subscription is remembered for a few days, so it keeps working when the device is offline.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : Entitlement, PurchasesUpdatedListener {

    enum class Status { CHECKING, ACTIVE, NOT_SUBSCRIBED, UNAVAILABLE }

    /** One way to subscribe: a base plan / offer of the subscription product, with its price and any free trial. */
    class Plan(
        val title: String,
        val price: String,
        val per: String,
        val trial: String?,
        internal val details: ProductDetails,
        internal val token: String
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = context.getSharedPreferences("nexora_billing", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(
        if (!BuildConfig.REQUIRE_SUBSCRIPTION || cachedActive()) Status.ACTIVE else Status.CHECKING
    )
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _plans = MutableStateFlow<List<Plan>>(emptyList())
    val plans: StateFlow<List<Plan>> = _plans.asStateFlow()

    /** Pro from a license code instead of a subscription (the owner's own use, a demo, a gift). */
    private val _license = MutableStateFlow(readLicense())
    val license: StateFlow<LicenseInfo?> = _license.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
    }

    init { refresh() }

    override fun isPro(): Boolean = !BuildConfig.REQUIRE_SUBSCRIPTION || cachedActive() || license.value != null

    val manageUrl: String
        get() = "https://play.google.com/store/account/subscriptions?sku=$PRODUCT_ID&package=${context.packageName}"

    /** Asks Google Play for the plans and for what this account has bought (also used by "Restore purchases"). */
    fun refresh() {
        if (!BuildConfig.REQUIRE_SUBSCRIPTION) { _status.value = Status.ACTIVE; return }
        scope.launch {
            if (!connect()) {
                _message.value = "Google Play billing isn't available on this device."
                _status.value = if (cachedActive()) Status.ACTIVE else Status.UNAVAILABLE
                return@launch
            }
            loadPlans()
            checkPurchases()
        }
    }

    fun subscribe(activity: Activity, plan: Plan) {
        scope.launch {
            if (!connect()) { _message.value = "Google Play billing isn't available on this device."; return@launch }
            val product = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(plan.details)
                .setOfferToken(plan.token)
                .build()
            val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build()
            val result = client.launchBillingFlow(activity, flow)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _message.value = "Could not start the purchase: ${result.debugMessage}"
            }
        }
    }

    fun clearMessage() { _message.value = null }

    /** Checks and saves a license code. Returns true when it is valid. */
    fun activateLicense(code: String): Boolean {
        val info = LicenseVerifier.verify(code, today())
        if (info == null) {
            _message.value = "That license code isn't valid or has expired."
            return false
        }
        cache.edit().putString(KEY_LICENSE, code.trim()).apply()
        _license.value = info
        _message.value = null
        return true
    }

    fun removeLicense() {
        cache.edit().remove(KEY_LICENSE).apply()
        _license.value = null
    }

    private fun readLicense(): LicenseInfo? = LicenseVerifier.verify(cache.getString(KEY_LICENSE, null), today())

    private fun today(): Int {
        val c = java.util.Calendar.getInstance()
        return c.get(java.util.Calendar.YEAR) * 10000 + (c.get(java.util.Calendar.MONTH) + 1) * 100 + c.get(java.util.Calendar.DAY_OF_MONTH)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch { handle(purchases.orEmpty()) }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _message.value = "The purchase did not complete: ${result.debugMessage}"
        }
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() { /* reconnected on the next call */ }
            })
        }
    }

    private suspend fun loadPlans() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = client.queryProductDetails(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        )
        val details = result.productDetailsList?.firstOrNull()
        if (details == null) {
            _message.value = "The subscription isn't set up in Google Play yet."
            return
        }
        _plans.value = details.subscriptionOfferDetails.orEmpty().mapNotNull { offer ->
            val phases = offer.pricingPhases.pricingPhaseList
            val recurring = phases.lastOrNull() ?: return@mapNotNull null
            val first = phases.firstOrNull()
            val trial = if (first != null && first.priceAmountMicros == 0L) freeText(first.billingPeriod) else null
            Plan(
                title = details.name.ifBlank { "Nexora Printer Pro" } + " (" + offer.basePlanId + ")",
                price = recurring.formattedPrice,
                per = period(recurring.billingPeriod),
                trial = trial,
                details = details,
                token = offer.offerToken
            )
        }
    }

    private suspend fun checkPurchases() {
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            _status.value = if (cachedActive()) Status.ACTIVE else Status.UNAVAILABLE
            return
        }
        handle(result.purchasesList)
    }

    private suspend fun handle(purchases: List<Purchase>) {
        var active = false
        for (p in purchases) {
            if (!p.products.contains(PRODUCT_ID)) continue
            when (p.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    active = true
                    if (!p.isAcknowledged) {
                        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build())
                    }
                }
                Purchase.PurchaseState.PENDING -> _message.value = "Your payment is pending. Printing unlocks when it completes."
                else -> Unit
            }
        }
        cache.edit().putBoolean(KEY_ACTIVE, active).putLong(KEY_CHECKED, System.currentTimeMillis()).apply()
        _status.value = if (active) Status.ACTIVE else Status.NOT_SUBSCRIBED
    }

    private fun cachedActive(): Boolean =
        cache.getBoolean(KEY_ACTIVE, false) && System.currentTimeMillis() - cache.getLong(KEY_CHECKED, 0L) < GRACE_MS

    private fun period(iso: String): String = when (iso) {
        "P1W" -> "week"
        "P1M" -> "month"
        "P3M" -> "3 months"
        "P6M" -> "6 months"
        "P1Y" -> "year"
        else -> iso
    }

    private fun freeText(iso: String): String = when (iso) {
        "P3D" -> "3 days free"
        "P7D", "P1W" -> "7 days free"
        "P14D", "P2W" -> "14 days free"
        "P1M" -> "1 month free"
        else -> "Free trial"
    }

    companion object {
        val PRODUCT_ID: String = BuildConfig.SUBSCRIPTION_PRODUCT_ID
        private const val KEY_ACTIVE = "active"
        private const val KEY_CHECKED = "checked_at"
        private const val KEY_LICENSE = "license_code"
        /** How long a confirmed subscription is trusted while Google Play can't be reached. */
        private const val GRACE_MS = 3L * 24 * 60 * 60 * 1000
    }
}
