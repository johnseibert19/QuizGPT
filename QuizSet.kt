package com.example.quizgpt

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class QuizSet(
    var id: String = "",
    val title: String = "",
    val description: String = "",
    val isAiGraded: Boolean = false,
    val ownerUid: String = "",

    // --- FIX: Force Firestore to use "isStarred" exactly ---
    @get:PropertyName("isStarred")
    val isStarred: Boolean = false,

    @ServerTimestamp
    val createdAt: Date? = null,
    val lastStudied: Long = 0L // <--- ADD THIS LINE
)