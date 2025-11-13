package org.archuser.trapmaster.data

import org.json.JSONArray
import org.json.JSONObject

/** Data object describing a completed trap shooting game. */
data class GameRecord(
    val rounds: List<RoundRecord>,
    val startTimeIso: String
) {
    fun totalHits(): Int = rounds.sumOf { it.hitsCount() }

    fun toJson(): JSONObject {
        val roundsArray = JSONArray()
        rounds.forEach { roundsArray.put(it.toJson()) }
        return JSONObject()
            .put("rounds", roundsArray)
            .put("startTime", startTimeIso)
    }

    fun toCsvRows(): List<String> {
        val rows = mutableListOf<String>()
        rounds.forEachIndexed { roundIndex, round ->
            round.shots.forEachIndexed { shotIndex, shot ->
                rows += listOf(startTimeIso, (roundIndex + 1).toString(), (shotIndex + 1).toString(), shot.toString()).joinToString(",")
            }
        }
        return rows
    }

    companion object {
        fun fromJson(jsonObject: JSONObject): GameRecord? {
            val startTime = jsonObject.optString("startTime", null) ?: return null
            val roundsArray = jsonObject.optJSONArray("rounds") ?: return null
            val rounds = mutableListOf<RoundRecord>()
            for (i in 0 until roundsArray.length()) {
                val roundObject = roundsArray.optJSONObject(i) ?: continue
                RoundRecord.fromJson(roundObject)?.let { rounds += it }
            }
            if (rounds.size != 5) return null
            return GameRecord(rounds, startTime)
        }
    }
}

/** Represents a single round of five shots. */
data class RoundRecord(val shots: List<Int>) {
    fun hitsCount(): Int = shots.count { it == 1 }

    fun toJson(): JSONObject {
        val shotsArray = JSONArray()
        shots.forEach { shotsArray.put(it) }
        return JSONObject().put("shots", shotsArray)
    }

    companion object {
        fun fromJson(jsonObject: JSONObject): RoundRecord? {
            val shotsArray = jsonObject.optJSONArray("shots") ?: return null
            if (shotsArray.length() != 5) return null
            val shots = mutableListOf<Int>()
            for (i in 0 until shotsArray.length()) {
                val shotValue = shotsArray.optInt(i, -1)
                if (shotValue != 0 && shotValue != 1) return null
                shots += shotValue
            }
            return RoundRecord(shots)
        }
    }
}
