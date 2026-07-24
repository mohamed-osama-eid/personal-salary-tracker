package com.example.data.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class SessionModel(
    val sessionType: String, // "Normal" or "Camp"
    val kidCount: Int,
    val sessionCount: Int = 1,
    val hoursPerSession: Double = 0.0
)

object SessionJson {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val type = Types.newParameterizedType(List::class.java, SessionModel::class.java)
    private val adapter = moshi.adapter<List<SessionModel>>(type)

    fun toJson(sessions: List<SessionModel>): String {
        return try {
            adapter.toJson(sessions)
        } catch (e: Exception) {
            "[]"
        }
    }

    fun fromJson(json: String): List<SessionModel> {
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
