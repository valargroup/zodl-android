package co.electriccoin.zcash.ui.common.usecase

import android.os.Process
import android.util.Log
import kotlin.system.exitProcess

internal object VotingKeystoneCrashTestFlags {
    // LOCAL TESTING ONLY: flip one flag at a time to exercise Keystone recovery after process death.
    val crashAfterPcztStored = false // K1
    val crashAfterScanSignatureTapped = false // K2
    val crashAfterSignedQrScannedBeforeSignatureStored = false // K3
    val crashAfterKeystoneBundleSignatureStored = false // K4
    val crashBetweenKeystoneBundles = false // K5
    val crashAfterAllKeystoneBundleSignaturesCollected = false // K6
    val crashAfterSkipRemainingKeystoneBundles = false // K7
}

internal fun crashIfVotingKeystoneCrashTestEnabled(
    enabled: Boolean,
    flagName: String,
    stage: String
) {
    if (!enabled) {
        return
    }

    Log.e(KEYSTONE_CRASH_TEST_TAG, "LOCAL TESTING: crashing Keystone voting flag=$flagName stage=$stage")
    Process.killProcess(Process.myPid())
    exitProcess(VOTING_KEYSTONE_CRASH_TEST_EXIT_CODE)
}

private const val KEYSTONE_CRASH_TEST_TAG = "VotingKeystoneCrashTest"
private const val VOTING_KEYSTONE_CRASH_TEST_EXIT_CODE = 11
