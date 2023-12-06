package com.cmpt362.cinebon.data.repo

import android.util.Log
import com.cmpt362.cinebon.data.objects.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext


class UserRepository private constructor() {

    companion object {
        const val USER_COLLECTION = "users"
        private const val USER_MOVIE_LIST = "movieList"
        private const val USER_DEFAULT_LIST = "defaultList"

        private val instance = UserRepository()

        fun getInstance(): UserRepository {
            return instance
        }
    }

    private val database = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()

    // Success.false means initial state
    // Success.true means user created
    // Failure means userdata not created
    private val _userCreatedResult = MutableStateFlow(Result.success(false))
    val userCreatedResult: StateFlow<Result<Boolean>>
        get() = _userCreatedResult

    private val _userInfo = MutableStateFlow<User?>(null)
    val userInfo: StateFlow<User?>
        get() = _userInfo

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults: StateFlow<List<User>>
        get() = _searchResults

    suspend fun createUserData(user: User) {
        withContext(IO) {
            database.collection(USER_COLLECTION).document(user.userId)
                .set(user)
                .addOnSuccessListener {
                    Log.d("UserRepository", "User data successfully written")
                    _userCreatedResult.value = Result.success(true)
                    _userInfo.value = user
                }
                .addOnFailureListener { e ->
                    Log.w("UserRepository", "Error writing document", e)
                    _userCreatedResult.value = Result.failure(e)
                    _userInfo.value = null
                }
        }
    }

    suspend fun signOut() {
        withContext(IO) {
            FirebaseAuth.getInstance().signOut()
            _userInfo.value = null
        }
    }

    fun getUserRef(userId: String) = database.collection(USER_COLLECTION).document(userId)

    suspend fun updateCurrentUserData() {
        _userInfo.value = getUserData(FirebaseAuth.getInstance().currentUser!!.uid)
    }

    suspend fun getUserData(userId: String): User? {
        val snapShot = getUserRef(userId).get().await()

        if (snapShot.exists()) {
//            Log.d("UserRepository", "User data successfully retrieved")
            return snapShot.toObject<User>()
        }

        Log.d("UserRepository", "Error getting user data")
        return null
    }

    suspend fun updateUserData(
        user: User, onResult: (Throwable?) -> Unit
    ) {
        withContext(IO) {
            getUserRef(user.userId)
                .set(user)
                .addOnSuccessListener {
                    Log.d("UserRepository", "user data updated successfully")
                    onResult(null)
                }
                .addOnFailureListener { e ->
                    Log.w("UserRepository", "error updating user data", e)
                    onResult(e)
                }
        }
    }

    suspend fun addUserList(listRef: DocumentReference, isDefault: Boolean = false) {
        withContext(IO) {
            val userRef = getUserRef(FirebaseAuth.getInstance().currentUser!!.uid)

            if (isDefault) userRef.update(USER_DEFAULT_LIST, listRef)
            else  userRef.update(USER_MOVIE_LIST, FieldValue.arrayUnion(listRef))
        }
    }

    suspend fun addFriend(friend: User) {
        withContext(IO) {
            val selfRef = getUserRef(FirebaseAuth.getInstance().currentUser!!.uid)
            val friendRef = getUserRef(friend.userId)

            selfRef
                .update("friends", FieldValue.arrayUnion(friendRef))

            friendRef
                .update("friends", FieldValue.arrayUnion(selfRef))

            Log.d("UserRepository", "Friend added")
        }
    }

    suspend fun removeFriend(friend: User) {
        withContext(IO) {
            val selfRef = getUserRef(FirebaseAuth.getInstance().currentUser!!.uid)
            val friendRef = getUserRef(friend.userId)

            selfRef
                .update("friends", FieldValue.arrayRemove(friendRef))

            friendRef
                .update("friends", FieldValue.arrayRemove(selfRef))

            Log.d("UserRepository", "Friend deleted")
        }
    }

    fun removeList(listRef: DocumentReference) {
        val selfRef = getUserRef(FirebaseAuth.getInstance().currentUser!!.uid)
        selfRef
            .update(USER_MOVIE_LIST, FieldValue.arrayRemove(listRef))
    }

    suspend fun searchUsers(username: String) {
        withContext(IO) {
            try {
                val querySnapshot = database.collection(USER_COLLECTION)
                    .whereGreaterThanOrEqualTo("username", username) //makes sure username starts with our given query
                    .whereLessThanOrEqualTo("username", username + "z")//makes sure username is less than our query + z
                    .get()
                    .await()

                val users = querySnapshot.documents.mapNotNull { it.toObject<User>() }

                _searchResults.value = users.filter { it.userId != auth.currentUser!!.uid }
                Log.d("UserRepository", "$users")

            } catch (e: Exception) {
                Log.w("UserRepository", "error searching for users", e)
                _searchResults.value = emptyList()
            }
        }
    }

    fun resetUserSearchResults() {
        _searchResults.value = emptyList()
    }

    fun attachUserRefListener(listener: EventListener<DocumentSnapshot>) {
        getUserRef(FirebaseAuth.getInstance().currentUser!!.uid)
            .addSnapshotListener(listener)
    }

    fun resetUserCreatedResult() {
        _userCreatedResult.value = Result.success(false)
    }
}