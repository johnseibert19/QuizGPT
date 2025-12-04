package com.example.quizgpt

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import kotlin.math.min
// Ensure BuildConfig is imported if your package structure requires it.
// Usually not needed if in the same package, but added here for safety if you move files.

data class TestQuestion(
    val id: String = "",
    val type: QuestionType,
    val questionText: String,
    val options: List<String> = emptyList(),
    val correctAnswer: String,
    val bloomLevel: String = "Recall",
    var userAnswer: String = "",
    var aiFeedback: String = "",
    var isCorrect: Boolean = false,
    var isGraded: Boolean = false
)

enum class QuestionType {
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    SHORT_ANSWER
}

data class TestSessionState(
    val isLoading: Boolean = false,
    val isGrading: Boolean = false,
    val questions: List<TestQuestion> = emptyList(),
    val itemsPerPage: Int = 1,
    val currentPage: Int = 0,
    val isComplete: Boolean = false,
    val score: Int = 0,
    val error: String? = null
)

data class QuizSetUiState(
    val message: String = ""
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class TutorSessionState(
    val isLoading: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val error: String? = null
)

// Sort Options Enum
enum class SortOption {
    CREATION_DATE_DESC,
    CREATION_DATE_ASC,
    ALPHABETICAL_AZ,
    ALPHABETICAL_ZA
}

class QuizSetViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    // --- FIX 1: Remove the static 'userId' variable ---
    // We will fetch the ID dynamically whenever we need it.

    // --- FIX 2: Add listeners variables so we can detach them on logout ---
    private var setListenerRegistration: ListenerRegistration? = null
    private var cardListenerRegistration: ListenerRegistration? = null

    /**
     * I want to acknowledge Reddit user Adventurous-Date9971 for this tip to
     * handle a security issue that does not affect functionality. Their tip
     * I write in complete below:
     *
     * "Don’t ship the Gemini key in the app-put a tiny server in front and
     * call that from Android. For an open-source prototype, stand up a minimal
     * proxy: Cloudflare Workers or Firebase Functions works great.
     * Server reads the key from env, checks auth (Firebase Auth or a simple signed nonce),
     * rate-limits per user/IP, then calls Gemini and streams back. On Android,
     * hit your proxy, not Google directly. This keeps forks from abusing your key
     * and lets you revoke/rotate without shipping an update. I’ve used Firebase
     * Functions and Cloudflare Workers for this; DreamFactory helped when I needed
     * instant REST over an existing DB with RBAC in front of app data.
     *
     * If you still want Secrets Gradle Plugin for local dev: apply the plugin
     * in app/build.gradle.kts (not just root), ensure buildFeatures
     * { buildConfig = true } for the module using it, put GEMINIAPIKEY in
     * local.properties or secrets.properties, then reference it as
     * BuildConfig.GEMINIAPIKEY. Common gotchas: wrong BuildConfig import in
     * a multi-module project, expecting it in a library module without enabling
     * buildConfig, or naming mismatch. Sync, clean, rebuild.
     *
     * Bottom line: keep the key off-device behind your server."
     *
     * As the author of QuizGPT, I am not going to utilize the first option
     * in the BOLT submission for the project, but do I hope to host this on
     * my personal Github with the proxy tip implemented so hosting does not
     * incur costs for utilizing an API key from Google Firebase.
     *
     */
    private val apiKey = BuildConfig.GEMINI_API_KEY

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey
    )

    private val _allSets = MutableStateFlow<List<QuizSet>>(emptyList())
    private val _searchQuery = MutableStateFlow("")

    private val _sortOption = MutableStateFlow(SortOption.CREATION_DATE_DESC)
    val sortOption = _sortOption.asStateFlow()

    val sets = combine(_allSets, _searchQuery, _sortOption) { sets, query, sortOrder ->
        val filtered = if (query.isBlank()) {
            sets
        } else {
            sets.filter { it.title.contains(query, ignoreCase = true) }
        }

        when (sortOrder) {
            SortOption.CREATION_DATE_DESC -> filtered.sortedWith(compareByDescending<QuizSet> { it.isStarred }.thenByDescending { it.createdAt })
            SortOption.CREATION_DATE_ASC -> filtered.sortedWith(compareByDescending<QuizSet> { it.isStarred }.thenBy { it.createdAt })
            SortOption.ALPHABETICAL_AZ -> filtered.sortedWith(compareByDescending<QuizSet> { it.isStarred }.thenBy { it.title.lowercase() })
            SortOption.ALPHABETICAL_ZA -> filtered.sortedWith(compareByDescending<QuizSet> { it.isStarred }.thenByDescending { it.title.lowercase() })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _cards = MutableStateFlow<List<QuizCard>>(emptyList())
    val cards = _cards.asStateFlow()

    private val _uiState = MutableStateFlow(QuizSetUiState())
    val uiState = _uiState.asStateFlow()

    private val _testState = MutableStateFlow(TestSessionState())
    val testState = _testState.asStateFlow()

    private val _tutorState = MutableStateFlow(TutorSessionState())
    val tutorState = _tutorState.asStateFlow()
    private var chatSession: Chat? = null

    private val _isAiEnabled = MutableStateFlow(true)

    init {
        // --- FIX 3: Listen for Auth Changes Dynamically ---
        // This runs automatically whenever the user logs in or out.
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                // User just logged in (or app started with saved session)
                startListeningToSets(user.uid)
            } else {
                // User logged out
                stopListeningToSets()
                _allSets.value = emptyList()
                _cards.value = emptyList()
            }
        }
        checkAvailableModels()
    }

    private fun startListeningToSets(userId: String) {
        // Prevent duplicate listeners
        setListenerRegistration?.remove()

        setListenerRegistration = db.collection("users").document(userId)
            .collection("quizSets")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("QuizSetViewModel", "Listen failed.", e)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val newSets = snapshot.documents.mapNotNull { document ->
                        val set = document.toObject(QuizSet::class.java)
                        set?.id = document.id
                        set
                    }
                    _allSets.value = newSets
                }
            }
    }

    private fun stopListeningToSets() {
        setListenerRegistration?.remove()
        setListenerRegistration = null
    }

    fun updateSortOrder(option: SortOption) {
        _sortOption.value = option
    }

    private fun checkAvailableModels() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Note: This check still hits the public endpoint.
                    // In a production app using a proxy (as per Tip 1),
                    // this URL would also point to your proxy server.
                    URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey").readText()
                }
                Log.d("GEMINI_MODELS", "API OK")
            } catch (e: Exception) {
                Log.e("GEMINI_MODELS", "Failed: ${e.message}")
            }
        }
    }

    // ... (Standard CRUD) ...
    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }
    fun getSetById(setId: String): QuizSet? = _allSets.value.find { it.id == setId }
    fun clearUiMessage() { _uiState.value = QuizSetUiState(message = "") }

    // --- FIX 4: Always fetch 'auth.currentUser?.uid' inside the functions ---

    suspend fun createSet(title: String, description: String, isAiGraded: Boolean): String? {
        val userId = auth.currentUser?.uid ?: return null

        val newSet = QuizSet(title = title, description = description, isAiGraded = isAiGraded, ownerUid = userId)
        return try {
            val documentRef = db.collection("users").document(userId).collection("quizSets").add(newSet).await()
            documentRef.update("id", documentRef.id).await()
            documentRef.id
        } catch (_: Exception) { null }
    }

    fun toggleSetStarred(setId: String, currentStatus: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            db.collection("users").document(userId).collection("quizSets").document(setId).update("isStarred", !currentStatus)
        }
    }

    fun addCardToSet(setId: String, question: String, answer: String, questionImageUri: Uri?, answerImageUri: Uri?) {
        val userId = auth.currentUser?.uid ?: return
        if (setId.isBlank()) return

        val alreadyExists = _cards.value.any { it.question.equals(question, ignoreCase = true) }
        if (alreadyExists) { _uiState.value = QuizSetUiState(message = "Error: Duplicate card."); return }

        val newCard = QuizCard(
            question = question,
            answer = answer,
            questionImageUri = questionImageUri?.toString(),
            answerImageUri = answerImageUri?.toString(),
            masteryLevel = MasteryLevel.NOT_STUDIED.name,
            isStarred = false
        )

        viewModelScope.launch {
            try {
                val ref = db.collection("users").document(userId).collection("quizSets").document(setId).collection("cards").add(newCard).await()
                ref.update("id", ref.id).await()
                _uiState.value = QuizSetUiState(message = "")
            } catch (_: Exception) { _uiState.value = QuizSetUiState(message = "Error adding card") }
        }
    }

    fun getCardsForSet(setId: String) {
        val userId = auth.currentUser?.uid ?: return
        if (setId.isBlank()) return

        // Clean up previous listener if we are switching sets
        cardListenerRegistration?.remove()

        cardListenerRegistration = db.collection("users").document(userId).collection("quizSets").document(setId).collection("cards")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _cards.value = snapshot.toObjects(QuizCard::class.java).mapIndexed { i, c -> c.apply { id = snapshot.documents[i].id } }
                }
            }
    }

    fun clearCardListener() {
        cardListenerRegistration?.remove()
        cardListenerRegistration = null
        _cards.value = emptyList()
    }

    fun deleteSet(setId: String) {
        val userId = auth.currentUser?.uid ?: return
        if (setId.isBlank()) return

        viewModelScope.launch {
            try {
                try {
                    val coll = db.collection("users").document(userId).collection("quizSets").document(setId).collection("cards")
                    val snaps = coll.get().await()
                    val batch = db.batch()
                    snaps.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                } catch (_: Exception) { }
                db.collection("users").document(userId).collection("quizSets").document(setId).delete().await()
            } catch (_: Exception) { }
        }
    }

    fun deleteCard(setId: String, cardId: String) {
        val userId = auth.currentUser?.uid ?: return
        if (setId.isBlank()) return
        viewModelScope.launch {
            try {
                db.collection("users").document(userId).collection("quizSets").document(setId).collection("cards").document(cardId).delete().await()
            } catch (_: Exception) { }
        }
    }

    fun updateCard(setId: String, cardId: String, q: String, a: String, qUri: Uri?, aUri: Uri?) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val updates = mutableMapOf<String, Any>("question" to q, "answer" to a)
                qUri?.let { updates["questionImageUri"] = it.toString() }
                aUri?.let { updates["answerImageUri"] = it.toString() }
                db.collection("users").document(userId).collection("quizSets").document(setId).collection("cards").document(cardId).update(updates).await()
            } catch (_: Exception) { }
        }
    }

    fun updateSet(setId: String, t: String, d: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(userId).collection("quizSets").document(setId).update("title", t, "description", d).await()
            } catch (e: Exception) { }
        }
    }

    fun toggleCardStarred(setId: String, cardId: String, currentStatus: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        if (setId.isBlank() || cardId.isBlank()) return
        viewModelScope.launch {
            try {
                db.collection("users").document(userId)
                    .collection("quizSets").document(setId)
                    .collection("cards").document(cardId)
                    .update("isStarred", !currentStatus)
                    .await()
            } catch (_: Exception) { }
        }
    }

    fun updateCardMastery(setId: String, cardId: String, masteryLevel: MasteryLevel) {
        val userId = auth.currentUser?.uid ?: return
        if (setId.isBlank() || cardId.isBlank()) return
        viewModelScope.launch {
            try {
                db.collection("users").document(userId)
                    .collection("quizSets").document(setId)
                    .collection("cards").document(cardId)
                    .update("masteryLevel", masteryLevel.name)
                    .await()
            } catch (_: Exception) { }
        }
    }

    fun importSetFromText(title: String, rawText: String, delimiter: String = "auto") {
        val userId = auth.currentUser?.uid ?: return
        if (title.isBlank() || rawText.isBlank()) return

        viewModelScope.launch {
            try {
                val newSet = QuizSet(title = title, description = "Imported Set", isAiGraded = false, ownerUid = userId)
                val setRef = db.collection("users").document(userId).collection("quizSets").add(newSet).await()
                setRef.update("id", setRef.id).await()
                val lines = rawText.lines().filter { it.isNotBlank() }
                val batch = db.batch()
                var count = 0
                val actualDelimiter = if (delimiter == "auto") {
                    val firstLine = lines.firstOrNull() ?: ""
                    if (firstLine.contains("\t")) "\t" else ","
                } else delimiter
                for (line in lines) {
                    val parts = line.split(actualDelimiter)
                    if (parts.size >= 2) {
                        val q = parts[0].trim()
                        val a = parts[1].trim()
                        val cardRef = setRef.collection("cards").document()
                        val card = QuizCard(id = cardRef.id, question = q, answer = a)
                        batch.set(cardRef, card)
                        count++
                    }
                }
                if (count > 0) {
                    batch.commit().await()
                    _uiState.value = QuizSetUiState(message = "Successfully imported $count cards.")
                } else {
                    _uiState.value = QuizSetUiState(message = "Could not find any valid term/definition pairs.")
                }
            } catch (e: Exception) {
                _uiState.value = QuizSetUiState(message = "Import failed: ${e.message}")
            }
        }
    }

    // --- AI Test Generation Logic ---
    fun generateTest(
        setId: String,
        questionCount: Int,
        itemsPerPage: Int = 1,
        includeMC: Boolean,
        includeTF: Boolean,
        includeSA: Boolean,
        starredOnly: Boolean = false
    ) {
        if (!_isAiEnabled.value) {
            _testState.value = TestSessionState(error = "AI features are currently disabled.")
            return
        }

        val allCards = _cards.value
        val currentCards = if (starredOnly) allCards.filter { it.isStarred } else allCards

        if (currentCards.isEmpty()) {
            _testState.value = TestSessionState(error = if (starredOnly) "No starred cards found." else "Set has no cards.")
            return
        }
        _testState.value = TestSessionState(isLoading = true)
        viewModelScope.launch {
            try {
                val context = currentCards.joinToString("\n") { "Term: ${it.question} | Def: ${it.answer}" }
                val prompt = """
                    You are an expert teacher creating a rigorous exam. Create exactly $questionCount questions based ONLY on this material:
                    $context
                    CRITICAL INSTRUCTION: Vary the Bloom's Taxonomy levels.
                    Allowed Formats (Mix these):
                    ${if (includeMC) "- Multiple Choice (type: MULTIPLE_CHOICE)" else ""}
                    ${if (includeTF) "- True/False (type: TRUE_FALSE)" else ""}
                    ${if (includeSA) "- Short Answer (type: SHORT_ANSWER)" else ""}
                    Return a JSON ARRAY only.
                    Format: [{"type": "MULTIPLE_CHOICE", "bloom": "Level 1: Recall", "question": "...", "options": ["A", "B", "C", "D"], "answer": "C"}]
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                var jsonString = response.text ?: ""
                jsonString = jsonString.replace("```json", "").replace("```", "").trim()

                if (jsonString.startsWith("[")) {
                    val jsonArray = JSONArray(jsonString)
                    val questions = mutableListOf<TestQuestion>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val typeStr = obj.getString("type")
                        val type = try { QuestionType.valueOf(typeStr) } catch (_: Exception) { QuestionType.SHORT_ANSWER }
                        val bloom = if (obj.has("bloom")) obj.getString("bloom") else "Level 1: Recall"
                        val optionsList = mutableListOf<String>()
                        if (obj.has("options")) {
                            val opts = obj.getJSONArray("options")
                            for (j in 0 until opts.length()) optionsList.add(opts.getString(j))
                        }
                        var correctAnswer = obj.getString("answer")
                        if (type == QuestionType.MULTIPLE_CHOICE && optionsList.isNotEmpty()) {
                            if (correctAnswer.length == 1 && correctAnswer[0] in 'A'..'D') {
                                val index = correctAnswer[0] - 'A'
                                if (index in optionsList.indices) correctAnswer = optionsList[index]
                            }
                        }
                        questions.add(TestQuestion(id = i.toString(), type = type, questionText = obj.getString("question"), bloomLevel = bloom, options = optionsList, correctAnswer = correctAnswer))
                    }
                    _testState.value = TestSessionState(isLoading = false, questions = questions, itemsPerPage = itemsPerPage)
                } else {
                    _testState.value = TestSessionState(isLoading = false, error = "Failed to parse test data.")
                }
            } catch (e: Exception) { _testState.value = TestSessionState(isLoading = false, error = "AI Error: ${e.message}") }
        }
    }

    private suspend fun gradeShortAnswer(question: String, correctAnswer: String, userAnswer: String): Pair<Boolean, String> {
        if (!_isAiEnabled.value) {
            val isMatch = userAnswer.trim().equals(correctAnswer.trim(), ignoreCase = true)
            return Pair(isMatch, "AI Disabled. Exact match: $isMatch")
        }
        return try {
            val prompt = """
                Grade this student answer. Question: "$question" Correct Definition: "$correctAnswer" Student Answer: "$userAnswer"
                Output ONLY a JSON object: { "isCorrect": true/false, "feedback": "Your feedback here..." }
            """.trimIndent()
            val response = generativeModel.generateContent(prompt)
            var jsonString = response.text ?: ""
            jsonString = jsonString.replace("```json", "").replace("```", "").trim()
            val jsonObj = JSONObject(jsonString)
            Pair(jsonObj.getBoolean("isCorrect"), jsonObj.getString("feedback"))
        } catch (_: Exception) {
            val isMatch = userAnswer.trim().equals(correctAnswer.trim(), ignoreCase = true)
            Pair(isMatch, "AI unavailable. Exact match: $isMatch")
        }
    }

    fun gradeWriteModeAnswer(question: String, correctAnswer: String, userAnswer: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val (isCorrect, feedback) = gradeShortAnswer(question, correctAnswer, userAnswer)
            onResult(isCorrect, feedback)
        }
    }

    fun updateLocalAnswer(questionIndex: Int, newAnswer: String) {
        val currentState = _testState.value
        val updatedQuestions = currentState.questions.toMutableList()
        if (questionIndex in updatedQuestions.indices) {
            updatedQuestions[questionIndex] = updatedQuestions[questionIndex].copy(userAnswer = newAnswer)
            _testState.value = currentState.copy(questions = updatedQuestions)
        }
    }

    fun submitPage() {
        val currentState = _testState.value
        if (currentState.isComplete) return

        val startIndex = currentState.currentPage * currentState.itemsPerPage
        val endIndex = min(startIndex + currentState.itemsPerPage, currentState.questions.size)
        val questions = currentState.questions.toMutableList()

        _testState.value = currentState.copy(isGrading = true)

        viewModelScope.launch {
            for (i in startIndex until endIndex) {
                val q = questions[i]
                if (q.type == QuestionType.SHORT_ANSWER) {
                    val (isCorrect, feedback) = gradeShortAnswer(q.questionText, q.correctAnswer, q.userAnswer)
                    questions[i] = q.copy(isCorrect = isCorrect, aiFeedback = feedback, isGraded = true)
                } else {
                    val isCorrect = q.userAnswer.trim().equals(q.correctAnswer.trim(), ignoreCase = true)
                    val feedback = if (isCorrect) "Correct!" else "Incorrect. Answer: ${q.correctAnswer}"
                    questions[i] = q.copy(isCorrect = isCorrect, aiFeedback = feedback, isGraded = true)
                }
            }

            val nextPageIndex = currentState.currentPage + 1
            val totalPages = (currentState.questions.size + currentState.itemsPerPage - 1) / currentState.itemsPerPage

            if (nextPageIndex >= totalPages) {
                val finalScore = questions.count { it.isCorrect }
                _testState.value = currentState.copy(
                    questions = questions,
                    isGrading = false,
                    isComplete = true,
                    score = finalScore
                )
            } else {
                _testState.value = currentState.copy(
                    questions = questions,
                    isGrading = false,
                    currentPage = nextPageIndex
                )
            }
        }
    }

    fun resetTest() { _testState.value = TestSessionState() }

    fun generateMnemonic(question: String, answer: String, onResult: (String) -> Unit) {
        if (!_isAiEnabled.value) { onResult("AI Disabled"); return }
        viewModelScope.launch {
            try {
                val prompt = "Create a short, catchy mnemonic to help remember that '$question' means '$answer'. Keep it brief."
                val response = generativeModel.generateContent(prompt)
                onResult(response.text ?: "Could not generate mnemonic.")
            } catch (e: Exception) { onResult("Error: ${e.message}") }
        }
    }

    fun generateELI5(question: String, answer: String, onResult: (String) -> Unit) {
        if (!_isAiEnabled.value) { onResult("AI Disabled"); return }
        viewModelScope.launch {
            try {
                val prompt = "Explain this concept like I am learning this for the first time: '$question' is '$answer'. Use simple words and maybe an analogy."
                val response = generativeModel.generateContent(prompt)
                onResult(response.text ?: "Could not generate explanation.")
            } catch (e: Exception) { onResult("Error: ${e.message}") }
        }
    }

    fun startTutorSession(setId: String, starredOnly: Boolean = false) {
        val userId = auth.currentUser?.uid ?: return

        if (!_isAiEnabled.value) {
            _tutorState.value = TutorSessionState(error = "AI features are currently disabled.")
            return
        }
        val allCards = _cards.value
        val currentCards = if (starredOnly) allCards.filter { it.isStarred } else allCards

        if (currentCards.isEmpty()) {
            _tutorState.value = TutorSessionState(error = if (starredOnly) "No starred cards found." else "No cards to tutor on.")
            return
        }
        _tutorState.value = TutorSessionState(isLoading = true, messages = emptyList())
        chatSession = generativeModel.startChat()

        viewModelScope.launch {
            var studentName = "Student"
            try {
                val userDoc = db.collection("users").document(userId).get().await()
                val user = userDoc.toObject(User::class.java)
                if (user != null && user.firstName.isNotEmpty()) {
                    studentName = user.firstName
                }
            } catch (e: Exception) { Log.w("QuizSetViewModel", "Failed to fetch user name: ${e.message}") }

            try {
                val context = currentCards.joinToString("\n") { "Term: ${it.question} | Def: ${it.answer}" }
                val initialSystemPrompt = """
                    You are a friendly and Socratic tutor helping $studentName study.
                    THE MATERIAL TO STUDY IS STRICTLY LIMITED TO:
                    $context
                    RULES:
                    1. ONLY ask questions about the terms defined above.
                    2. Do NOT bring in outside knowledge or unrelated topics.
                    3. Act like a tutor. Ask the student a question about one of the terms to start.
                    4. If they get it right, praise them and ask another.
                    5. If they get it wrong, give a hint.
                    6. Keep responses short and conversational.
                """.trimIndent()
                val response = chatSession?.sendMessage(initialSystemPrompt)
                val aiText = response?.text ?: "Hello $studentName! Ready to study? Let's begin."
                val aiMsg = ChatMessage(text = aiText, isUser = false)
                _tutorState.value = TutorSessionState(isLoading = false, messages = listOf(aiMsg))
            } catch (e: Exception) {
                _tutorState.value = TutorSessionState(isLoading = false, error = "Failed to start tutor: ${e.message}")
            }
        }
    }

    fun sendTutorMessage(userMessageText: String) {
        if (userMessageText.isBlank() || chatSession == null) return
        val currentMessages = _tutorState.value.messages.toMutableList()
        val userMsg = ChatMessage(text = userMessageText, isUser = true)
        currentMessages.add(userMsg)
        _tutorState.value = _tutorState.value.copy(messages = currentMessages, isLoading = true)

        viewModelScope.launch {
            try {
                val response = chatSession?.sendMessage(userMessageText)
                val aiMsg = ChatMessage(text = response?.text ?: "...", isUser = false)
                val updatedMessages = _tutorState.value.messages.toMutableList()
                updatedMessages.add(aiMsg)
                _tutorState.value = _tutorState.value.copy(messages = updatedMessages, isLoading = false)
            } catch (e: Exception) {
                _tutorState.value = _tutorState.value.copy(isLoading = false, error = "Error: ${e.message}")
            }
        }
    }
}