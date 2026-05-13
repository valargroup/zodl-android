package co.electriccoin.zcash.ui.common.model.voting

import android.util.Log
import java.io.File

object VotingPhaseDiagnostics {
    private const val FILE_NAME = "voting-phase-diagnostics.txt"
    private const val TAG = "VotingPhaseDiagnostics"

    fun reset(votingDbPath: String, header: String) {
        write(votingDbPath, header, append = false)
    }

    fun append(votingDbPath: String, message: String) {
        write(votingDbPath, message, append = true)
    }

    private fun write(
        votingDbPath: String,
        message: String,
        append: Boolean
    ) {
        runCatching {
            val file = File(votingDbPath).parentFile?.resolve(FILE_NAME) ?: return
            val line = "${System.currentTimeMillis()} $message\n"
            if (append) {
                file.appendText(line)
            } else {
                file.writeText(line)
            }
        }.onFailure { exception ->
            Log.w(TAG, "Unable to write voting phase diagnostics", exception)
        }
    }
}
