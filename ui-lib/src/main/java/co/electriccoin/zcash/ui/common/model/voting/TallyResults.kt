package co.electriccoin.zcash.ui.common.model.voting

data class TallyResults(
    val roundId: String,
    val proposals: List<ProposalTally>
)

data class ProposalTally(
    val proposalId: Int,
    val options: List<OptionTally>
)

data class OptionTally(
    val optionId: Int,
    val weight: Long
)
