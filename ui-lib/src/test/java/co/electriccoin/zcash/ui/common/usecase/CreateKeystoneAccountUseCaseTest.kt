package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import com.keystone.module.ZcashAccount
import com.keystone.module.ZcashAccounts
import androidx.navigation.NavBackStackEntry
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CreateKeystoneAccountUseCaseTest {

    private val dummyAccount = Account.new(AccountUuid.new(ByteArray(16)))

    private fun makeAccounts() = ZcashAccounts("aabb", listOf(ZcashAccount("ufvk1", 0, "Test")))

    // Verifies that when birthdayHeight is provided, it is forwarded to
    // AccountDataSource.importKeystoneAccount as-is.
    @Test
    fun `invoke with birthday forwards height to data source`() = runTest {
        val captured = CapturedImportArgs()
        val useCase = CreateKeystoneAccountUseCase(
            accountDataSource = FakeAccountDataSource(captured, dummyAccount),
            navigationRouter = FakeNavigationRouter()
        )

        val accounts = makeAccounts()
        useCase(accounts, accounts.accounts.first(), birthdayHeight = 1_700_000L)

        assertEquals(1_700_000L, captured.birthdayHeight)
        assertEquals("ufvk1", captured.ufvk)
        assertEquals("aabb", captured.seedFingerprint)
        assertEquals(0L, captured.index)
    }

    // Verifies that when birthdayHeight is omitted or explicitly null,
    // null is forwarded to the data source (letting the SDK use its default).
    @Test
    fun `invoke without birthday forwards null to data source`() = runTest {
        val captured = CapturedImportArgs()
        val useCase = CreateKeystoneAccountUseCase(
            accountDataSource = FakeAccountDataSource(captured, dummyAccount),
            navigationRouter = FakeNavigationRouter()
        )

        val accounts = makeAccounts()

        // Explicit null
        useCase(accounts, accounts.accounts.first(), birthdayHeight = null)
        assertNull(captured.birthdayHeight)

        // Default parameter (omitted)
        captured.birthdayHeight = -1L
        useCase(accounts, accounts.accounts.first())
        assertNull(captured.birthdayHeight)
    }
}

private class CapturedImportArgs {
    var ufvk: String? = null
    var seedFingerprint: String? = null
    var index: Long? = null
    var birthdayHeight: Long? = -1L // sentinel to distinguish "not called" from "called with null"
}

@Suppress("TooManyFunctions")
private class FakeAccountDataSource(
    private val captured: CapturedImportArgs,
    private val accountToReturn: Account
) : AccountDataSource {
    override val allAccounts: StateFlow<List<WalletAccount>?>
        get() = MutableStateFlow(null)
    override val selectedAccount: Flow<WalletAccount?>
        get() = emptyFlow()
    override val zashiAccount: Flow<ZashiAccount?>
        get() = emptyFlow()

    override suspend fun getAllAccounts(): List<WalletAccount> = emptyList()
    override suspend fun getSelectedAccount(): WalletAccount = error("not implemented")
    override suspend fun getZashiAccount(): ZashiAccount = error("not implemented")
    override suspend fun selectAccount(account: Account) {}
    override suspend fun selectAccount(account: WalletAccount) {}

    override suspend fun importKeystoneAccount(
        ufvk: String,
        seedFingerprint: String,
        index: Long,
        birthdayHeight: Long?
    ): Account {
        captured.ufvk = ufvk
        captured.seedFingerprint = seedFingerprint
        captured.index = index
        captured.birthdayHeight = birthdayHeight
        return accountToReturn
    }

    override suspend fun requestNextShieldedAddress() {}
}

private class FakeNavigationRouter : NavigationRouter {
    var didBackToRoot = false

    override fun forward(vararg routes: Any) {}
    override fun replace(vararg routes: Any) {}
    override fun replaceAll(vararg routes: Any) {}
    override fun back() {}
    override fun backTo(route: KClass<*>) {}
    override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) {}
    override fun backToRoot() { didBackToRoot = true }
    override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
}
