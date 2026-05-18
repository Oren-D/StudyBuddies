package com.example.studybuddies.ui.drive

import android.net.Uri
import com.example.studybuddies.data.model.Comment
import com.example.studybuddies.data.model.DriveFile
import com.example.studybuddies.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

/**
 * Handles all the Firebase Storage and Database logic for a specific subject folder.
 */
class SubjectFilesManager(private val driveId: String) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var userListener: ListenerRegistration? = null
    private var filesListener: ListenerRegistration? = null
    private var commentsListener: ListenerRegistration? = null

    val currentUserId: String?
        get() = auth.currentUser?.uid
        
    val currentUserEmail: String?
        get() = auth.currentUser?.email

    fun listenToUserStatus(onUpdate: (User?, Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        userListener = db.collection("users").document(uid).addSnapshotListener { document, error ->
            if (error != null) {
                onUpdate(null, false)
                return@addSnapshotListener
            }
            if (document != null && document.exists()) {
                val userData = document.toObject(User::class.java)
                if (userData != null) {
                    onUpdate(userData, userData.isBanned)
                } else {
                    onUpdate(null, false)
                }
            } else {
                onUpdate(null, false)
            }
        }
    }

    fun listenToFiles(onUpdate: (List<DriveFile>) -> Unit) {
        filesListener = db.collection("files")
            .whereEqualTo("driveId", driveId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    return@addSnapshotListener
                }
                val files = snapshot.toObjects(DriveFile::class.java).sortedByDescending { it.timestamp }
                onUpdate(files)
            }
    }

    fun listenToComments(fileId: String, onUpdate: (List<Comment>) -> Unit) {
        commentsListener?.remove()
        commentsListener = db.collection("files").document(fileId).collection("comments")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null) {
                    val comments = snapshot.toObjects(Comment::class.java)
                    onUpdate(comments)
                }
            }
    }
    
    fun removeCommentsListener() {
        commentsListener?.remove()
        commentsListener = null
    }

    fun toggleLike(file: DriveFile, userId: String, onSuccess: () -> Unit) {
        if (file.uploaderId == userId) return

        val docRef = db.collection("files").document(file.id)
        if (file.likes.contains(userId)) {
            docRef.update("likes", FieldValue.arrayRemove(userId)).addOnSuccessListener { onSuccess() }
        } else {
            docRef.update("likes", FieldValue.arrayUnion(userId)).addOnSuccessListener {
                db.collection("users").document(file.uploaderId)
                    .set(hashMapOf("reputationPoints" to FieldValue.increment(1)), SetOptions.merge())
                onSuccess()
            }
        }
    }

    private fun deleteFileDocumentWithComments(fileId: String, onComplete: (Boolean) -> Unit) {
        val commentsRef = db.collection("files").document(fileId).collection("comments")
        commentsRef.get().addOnSuccessListener { querySnapshot ->
            val batch = db.batch()
            for (doc in querySnapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.delete(db.collection("files").document(fileId))
            batch.commit()
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { onComplete(false) }
        }.addOnFailureListener {
            db.collection("files").document(fileId).delete()
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { onComplete(false) }
        }
    }

    fun deleteFile(file: DriveFile, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        try {
            storage.getReferenceFromUrl(file.downloadUrl).delete()
        } catch (e: Exception) {}

        deleteFileDocumentWithComments(file.id) { success ->
            if (success) {
                db.collection("subject_drives").document(driveId)
                    .set(hashMapOf("fileCount" to FieldValue.increment(-1)), SetOptions.merge())
                onSuccess()
            } else {
                onFailure(Exception("Failed to delete file database records."))
            }
        }
    }

    fun reportFile(file: DriveFile, currentUserId: String, onReported: () -> Unit, onDeleted: () -> Unit) {
        if (file.uploaderId == currentUserId || file.reportedBy.contains(currentUserId)) return

        val newReportCount = file.reportedBy.size + 1
        if (newReportCount > 3) {
            try { storage.getReferenceFromUrl(file.downloadUrl).delete() } catch (e: Exception) {}
            
            deleteFileDocumentWithComments(file.id) { success ->
                if (success) {
                    db.collection("subject_drives").document(driveId)
                        .set(hashMapOf("fileCount" to FieldValue.increment(-1)), SetOptions.merge())
                }
            }
            
            val userRef = db.collection("users").document(file.uploaderId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val currentDeletes = snapshot.getLong("deletedFilesCount") ?: 0L
                val newDeletes = currentDeletes + 1
                
                val updates = mutableMapOf<String, Any>("deletedFilesCount" to newDeletes)
                if (newDeletes > 3) updates["isBanned"] = true
                transaction.set(userRef, updates, SetOptions.merge())
                null
            }
            onDeleted()
        } else {
            db.collection("files").document(file.id).update("reportedBy", FieldValue.arrayUnion(currentUserId))
            onReported()
        }
    }

    fun unlockFile(file: DriveFile, userId: String, onSuccess: () -> Unit) {
        db.collection("users").document(userId)
            .set(hashMapOf("reputationPoints" to FieldValue.increment(-5)), SetOptions.merge())
            
        db.collection("files").document(file.id)
            .update("unlockedBy", FieldValue.arrayUnion(userId))
            .addOnSuccessListener { onSuccess() }
    }

    /**
     * uploadFile: This is a complex function!
     * 1. It uploads the physical file (PDF or Image) to Firebase Storage.
     * 2. It takes the secure download link and saves it into the Firestore database so others can see it.
     */
    fun uploadFile(uri: Uri, isCamera: Boolean, mimeType: String?, description: String, onComplete: (Boolean, String?) -> Unit) {
        val user = auth.currentUser ?: return
        
        var ext = ".pdf"
        var prefix = "Document"
        if (isCamera) {
            ext = ".jpg"
            prefix = "Photo"
        } else if (mimeType?.startsWith("image/") == true) {
            ext = ".jpg"
            prefix = "Photo"
        }
        
        val fileName = "${prefix}_${UUID.randomUUID().toString().take(4)}$ext"
        val storageRef = storage.reference.child("uploads/$fileName")
        
        storageRef.putFile(uri)
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUri ->
                    val docRef = db.collection("files").document()
                    val newFile = DriveFile(
                        id = docRef.id,
                        name = fileName,
                        downloadUrl = downloadUri.toString(),
                        uploaderId = user.uid,
                        uploaderName = user.email ?: "Unknown",
                        driveId = driveId,
                        description = description,
                        timestamp = System.currentTimeMillis()
                    )

                    docRef.set(newFile)
                        .addOnSuccessListener {
                            val updates = hashMapOf<String, Any>(
                                "hasUploaded" to true,
                                "reputationPoints" to FieldValue.increment(10)
                            )
                            db.collection("users").document(user.uid).set(updates, SetOptions.merge())
                            db.collection("subject_drives").document(driveId)
                                .set(hashMapOf("fileCount" to FieldValue.increment(1)), SetOptions.merge())
                            onComplete(true, null)
                        }
                        .addOnFailureListener { e -> onComplete(false, e.message) }
                }
            }
            .addOnFailureListener { e -> onComplete(false, e.message) }
    }

    fun postComment(fileId: String, content: String, authorName: String) {
        val uid = auth.currentUser?.uid ?: return
        val commentsRef = db.collection("files").document(fileId).collection("comments")
        val newRef = commentsRef.document()
        
        val comment = Comment(
            id = newRef.id,
            fileId = fileId,
            authorUid = uid,
            authorName = authorName,
            content = content
        )
        newRef.set(comment).addOnSuccessListener {
            db.collection("files").document(fileId)
                .set(hashMapOf("commentCount" to FieldValue.increment(1)), SetOptions.merge())
        }
    }

    fun cleanup() {
        userListener?.remove()
        filesListener?.remove()
        commentsListener?.remove()
    }
}
