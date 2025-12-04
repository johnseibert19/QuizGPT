package com.example.quizgpt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObject // Import for toObject
import com.google.firebase.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthUiState(
    val message: String = "",
    val isLoading: Boolean = false,
    val userProfile: User? = null // <-- ADDED: To store the fetched user data
)

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = Firebase.auth
    private val db = Firebase.firestore

    val currentUser: StateFlow<FirebaseUser?> = auth.authStateFlow()

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    // --- ADDED THIS INIT BLOCK ---
    init {
        // If a user is already logged in when the app starts, fetch their profile
        if (auth.currentUser != null) {
            fetchUserProfile()
        }
    }

    // --- ADDED THIS FUNCTION ---
    fun fetchUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val snapshot = db.collection("users").document(uid).get().await()
                val user = snapshot.toObject<User>()
                if (user != null) {
                    // Update state with the user profile, keeping other state same
                    _uiState.value = _uiState.value.copy(userProfile = user)
                }
            } catch (e: Exception) {
                // Handle error silently or update message
            }
        }
    }
    // --- END OF ADDITION ---

    fun createUserAndSaveDetails(
        email: String,
        pass: String,
        firstName: String,
        lastName: String
    ) {
        if (email.isBlank() || pass.isBlank() || firstName.isBlank() || lastName.isBlank()) {
            _uiState.value = AuthUiState(message = "Error: All fields must be filled.")
            return
        }

        viewModelScope.launch {
            try {
                val authResult = auth.createUserWithEmailAndPassword(email, pass).await()
                val firebaseUser = authResult.user

                if (firebaseUser != null) {
                    firebaseUser.sendEmailVerification().await()

                    val newUser = User(
                        uid = firebaseUser.uid,
                        firstName = firstName,
                        lastName = lastName,
                        email = email
                    )
                    db.collection("users")
                        .document(firebaseUser.uid)
                        .set(newUser)
                        .await()

                    _uiState.value = AuthUiState(message = "Success! Please check your email to verify your account.")
                    auth.signOut()
                } else {
                    _uiState.value = AuthUiState(message = "Error: Could not create user.")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState(message = "Error: ${e.message}")
            }
        }
    }

    fun signIn(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState(message = "Error: Email and password cannot be blank.")
            return
        }
        _uiState.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val authResult = auth.signInWithEmailAndPassword(email, pass).await()

                if (authResult.user?.isEmailVerified == true) {
                    _uiState.value = AuthUiState(isLoading = false, message = "Success! Signed in.")
                    fetchUserProfile() // <-- ADDED: Fetch profile on successful sign in
                } else {
                    _uiState.value = AuthUiState(isLoading = false, message = "Error: Please verify your email before signing in.")
                    auth.signOut()
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState(isLoading = false, message = "Error: ${e.message}")
            }
        }
    }

    fun signOut() {
        auth.signOut()
        _uiState.value = AuthUiState() // Reset state on sign out
    }

    // ... (sendPasswordReset and resendVerificationEmail unchanged) ...
    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _uiState.value = AuthUiState(message = "Error: Email cannot be blank.")
            return
        }
        _uiState.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email).await()
                _uiState.value = AuthUiState(isLoading = false, message = "Success! Check your email for a reset link.")
            } catch (e: Exception) {
                _uiState.value = AuthUiState(isLoading = false, message = "Error: ${e.message}")
            }
        }
    }

    fun resendVerificationEmail(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState(message = "Error: Email/Password required to resend.")
            return
        }
        _uiState.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val authResult = auth.signInWithEmailAndPassword(email, pass).await()
                if (authResult.user != null && !authResult.user!!.isEmailVerified) {
                    authResult.user!!.sendEmailVerification().await()
                    _uiState.value = AuthUiState(isLoading = false, message = "Success! New verification email sent.")
                } else {
                    _uiState.value = AuthUiState(isLoading = false, message = "Error: User is already verified or does not exist.")
                }
                auth.signOut()
            } catch (e: Exception) {
                _uiState.value = AuthUiState(isLoading = false, message = "Error: ${e.message}")
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): StateFlow<FirebaseUser?> {
    val flow = MutableStateFlow(currentUser)
    val listener = FirebaseAuth.AuthStateListener { auth ->
        flow.value = auth.currentUser
    }
    addAuthStateListener(listener)
    return flow.asStateFlow()
}