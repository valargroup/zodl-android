package co.electriccoin.zcash.ui.common.model.voting

import org.json.JSONArray
import org.json.JSONObject

fun String.selectVotingBundleNotesJson(witnessesJson: String): String {
    val noteObjectsByCommitment = mutableMapOf<String, JSONObject>()
    val notes = JSONArray(this)
    for (index in 0 until notes.length()) {
        val note = notes.getJSONObject(index)
        noteObjectsByCommitment[note.getString("commitment")] = note
    }

    val witnesses = JSONArray(witnessesJson)
    return JSONArray(
        buildList {
            for (index in 0 until witnesses.length()) {
                val witness = witnesses.getJSONObject(index)
                val commitment = witness.getString("note_commitment")
                add(
                    noteObjectsByCommitment[commitment]
                        ?: error("Missing note for witness commitment $commitment")
                )
            }
        }
    ).toString()
}
