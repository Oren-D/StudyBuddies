package com.example.studybuddies.ui.auth

/**
 * interface for Auth operations.
 */
interface IAuthenticationManager {
    /**
     * Attempts to log in a user with the given email and password.
     * @param onComplete Callback invoked with true if successful, false with an error message otherwise.
     */
    fun login(email: String, password: String, onComplete: (Boolean, String?) -> Unit)

    /**
     * Attempts to register a new user with the given email and password.
     * @param onComplete Callback invoked with true if successful, false with an error message otherwise.
     */
    fun register(email: String, password: String, onComplete: (Boolean, String?) -> Unit)

    /**
     * Checks if a user is currently logged in.
     * @return true if a user is logged in, false otherwise.
     */
    fun isLoggedIn(): Boolean

    /**
     * Logs out the current user.
     */
    fun logout()
}
