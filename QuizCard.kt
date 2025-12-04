package com.example.quizgpt

import com.google.firebase.firestore.PropertyName

data class QuizCard(
    var id: String = "",
    val question: String = "",
    val answer: String = "",
    val questionImageUri: String? = null,
    val answerImageUri: String? = null,
    val masteryLevel: String = MasteryLevel.NOT_STUDIED.name,

    // --- FIX: Force Firestore to use "isStarred" exactly ---
    @get:PropertyName("isStarred")
    val isStarred: Boolean = false
)

enum class MasteryLevel {
    NOT_STUDIED,
    NEEDS_IMPROVEMENT,
    MASTERED
}