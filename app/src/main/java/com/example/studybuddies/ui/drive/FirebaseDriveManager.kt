package com.example.studybuddies.ui.drive

import com.example.studybuddies.data.model.Comment
import com.example.studybuddies.data.model.DriveFile
import com.example.studybuddies.data.model.SubjectDrive
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

/**
 * This class talks to Firebase directly to get the Subject Drives, Library Files, and Comments.
 */
class FirebaseDriveManager : IDriveManager {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val activeListeners = mutableListOf<ListenerRegistration>()
    
    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    override fun listenToSubjectDrives(onUpdate: (List<SubjectDrive>?, Exception?) -> Unit) {
        val listener = db.collection("subject_drives")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onUpdate(null, error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val drives = snapshot.toObjects(SubjectDrive::class.java)
                    onUpdate(drives, null)
                }
            }
        activeListeners.add(listener)
    }

    override fun createSubjectDrive(name: String, onComplete: (Boolean, String?) -> Unit) {
        if (currentUserId.isEmpty()) {
            onComplete(false, "Not logged in")
            return
        }
        
        val docRef = db.collection("subject_drives").document()
        val newDrive = SubjectDrive(
            id = docRef.id,
            name = name,
            creatorUid = currentUserId,
            timestamp = System.currentTimeMillis()
        )

        docRef.set(newDrive)
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { e -> onComplete(false, e.message) }
    }

    override fun listenToMyLibraryFiles(onUpdate: (List<DriveFile>?, Exception?) -> Unit) {
        if (currentUserId.isEmpty()) {
            onUpdate(null, Exception("Not logged in"))
            return
        }
        
        val listener = db.collection("files").whereArrayContains("unlockedBy", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onUpdate(null, error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val files = snapshot.toObjects(DriveFile::class.java)
                    onUpdate(files, null)
                }
            }
        activeListeners.add(listener)
    }

    override fun toggleLike(file: DriveFile, onComplete: (Boolean) -> Unit) {
        if (currentUserId.isEmpty() || file.uploaderId == currentUserId) {
            onComplete(false)
            return
        }

        val docRef = db.collection("files").document(file.id)
        if (file.likes.contains(currentUserId)) {
            docRef.update("likes", FieldValue.arrayRemove(currentUserId)).addOnSuccessListener { onComplete(true) }
        } else {
            docRef.update("likes", FieldValue.arrayUnion(currentUserId)).addOnSuccessListener {
                db.collection("users").document(file.uploaderId)
                    .set(hashMapOf("reputationPoints" to FieldValue.increment(1)), SetOptions.merge())
                onComplete(true)
            }
        }
    }

    override fun listenToComments(fileId: String, onUpdate: (List<Comment>?, Exception?) -> Unit) {
        val listener = db.collection("files").document(fileId).collection("comments")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onUpdate(null, error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val comments = snapshot.toObjects(Comment::class.java)
                    onUpdate(comments, null)
                }
            }
        activeListeners.add(listener)
    }

    override fun postComment(fileId: String, content: String, onComplete: (Boolean) -> Unit) {
        if (currentUserId.isEmpty()) {
            onComplete(false)
            return
        }
        
        db.collection("users").document(currentUserId).get()
            .addOnSuccessListener { doc ->
                val authorNameStr = doc.getString("displayName") ?: auth.currentUser?.email ?: "Unknown"
                
                val commentsRef = db.collection("files").document(fileId).collection("comments")
                val newRef = commentsRef.document()
                
                val comment = Comment(
                    id = newRef.id,
                    fileId = fileId,
                    authorUid = currentUserId,
                    authorName = authorNameStr,
                    content = content
                )
                
                newRef.set(comment).addOnSuccessListener {
                    db.collection("files").document(fileId)
                        .set(hashMapOf("commentCount" to FieldValue.increment(1)), SetOptions.merge())
                    onComplete(true)
                }
            }
    }

    override fun cleanup() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }
}
