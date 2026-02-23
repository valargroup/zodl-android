package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey

interface IsRebrandAcknowledgedStorageProvider : BooleanStorageProvider

class IsRebrandAcknowledgedStorageProviderImpl(
    override val preferenceHolder: StandardPreferenceProvider,
) : BaseBooleanStorageProvider(
        key = PreferenceKey("is_rebrand_acknowledged"),
        default = false
    ),
    IsRebrandAcknowledgedStorageProvider
