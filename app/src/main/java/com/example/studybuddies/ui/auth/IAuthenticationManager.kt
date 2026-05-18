package com.example.studybuddies.ui.auth

/**
 * interface for Auth operations.
 */
interface IAuthenticationManager {
    /**
     * Attempts to log in a user with the given email and password.
     */
    fun login(email: String, password: String, onComplete: (Boolean, String?) -> Unit)

    /**
     * Attempts to register a new user with the given email and password.
     */
    fun register(email: String, password: String, onComplete: (Boolean, String?) -> Unit)

    /**
     * Checks if a user is currently logged in.
     */
    fun isLoggedIn(): Boolean

    /**
     * Logs out the current user.
     */
    fun logout()
}
