package com.example.studybuddies.ui.drive

import com.example.studybuddies.data.model.Comment
import com.example.studybuddies.data.model.DriveFile
import com.example.studybuddies.data.model.SubjectDrive


//An interface for Drive operations. talks to firebase and gets the info for the other classes

interface IDriveManager {
    fun listenToSubjectDrives(onUpdate: (List<SubjectDrive>?, Exception?) -> Unit)
    fun createSubjectDrive(name: String, onComplete: (Boolean, String?) -> Unit)
    
    fun listenToMyLibraryFiles(onUpdate: (List<DriveFile>?, Exception?) -> Unit)
    
    fun toggleLike(file: DriveFile, onComplete: (Boolean) -> Unit)
    fun listenToComments(fileId: String, onUpdate: (List<Comment>?, Exception?) -> Unit)
    fun postComment(fileId: String, content: String, onComplete: (Boolean) -> Unit)
    
    fun cleanup()
}
