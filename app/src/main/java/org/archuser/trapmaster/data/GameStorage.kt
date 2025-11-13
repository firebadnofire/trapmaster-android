package org.archuser.trapmaster.data

import android.content.Context
import org.json.JSONArray

class GameStorage(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadGames(): List<GameRecord> {
        val stored = preferences.getString(KEY_GAMES, null) ?: return emptyList()
        return try {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    val jsonObject = array.optJSONObject(index) ?: continue
                    GameRecord.fromJson(jsonObject)?.let { add(it) }
                }
            }
        } catch (ignored: Exception) {
            emptyList()
        }
    }

    fun saveGame(game: GameRecord) {
        val games = loadGames().toMutableList()
        games.add(game)
        persistGames(games)
    }

    fun saveGames(games: List<GameRecord>) {
        persistGames(games)
    }

    fun clearGames() {
        preferences.edit().remove(KEY_GAMES).apply()
    }

    private fun persistGames(games: List<GameRecord>) {
        val jsonArray = JSONArray()
        games.forEach { jsonArray.put(it.toJson()) }
        preferences.edit().putString(KEY_GAMES, jsonArray.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "trap_coach_prefs"
        private const val KEY_GAMES = "trapCoachGames"
    }
}
