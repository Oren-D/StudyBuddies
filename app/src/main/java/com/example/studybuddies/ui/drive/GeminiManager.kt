package com.example.studybuddies.ui.drive

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.studybuddies.data.model.DriveFile
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

class GeminiManager(private val apiKey: String) {

    suspend fun analyzeFile(file: DriveFile, isUnlocked: Boolean, contentResolver: ContentResolver): String {
        return withContext(Dispatchers.IO) {
            val generativeModel = GenerativeModel(
                modelName = "gemini-flash-latest",
                apiKey = apiKey
            )
            
            val safeDesc = if (file.description.isBlank()) "No description provided." else file.description
            
            val promptText = if (isUnlocked) {
                "Accurately describe exactly what you see in the provided file in no more than 3 sentences. If it is an academic document or notes, summarize the core concepts. If it is just an everyday object (like a soda can) or unrelated to school, realistically state what it is without inventing any academic meaning or experiments. Additional context: '$safeDesc'."
            } else {
                "Accurately describe exactly what you see in the provided file as an exciting 'teaser' preview in no more than 3 sentences. If it is an academic document or assignment, summarize the broad topics covered, but CRITICAL RULE: DO NOT REVEAL ANY SPECIFIC ANSWERS, SOLUTIONS, OR EQUATIONS. If it is just an everyday object or unrelated to school, realistically state what it is without inventing any academic meaning. Additional context: '$safeDesc'."
            }

            val isImage = file.name.endsWith(".jpg", true) || file.name.endsWith(".png", true) || file.name.endsWith(".jpeg", true)
            
            if (isImage) {
                try {
                    val bitmap = if (file.downloadUrl.startsWith("http")) {
                        val inputStream = URL(file.downloadUrl).openStream()
                        BitmapFactory.decodeStream(inputStream)
                    } else {
                        val uri = Uri.parse(file.downloadUrl)
                        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }
                    
                    if (bitmap != null) {
                        val inputContent = content {
                            image(bitmap)
                            text(promptText)
                        }
                        generativeModel.generateContent(inputContent).text ?: "Could not generate summary."
                    } else {
                        generativeModel.generateContent(promptText).text ?: "Could not generate summary."
                    }
                } catch (e: Exception) {
                    generativeModel.generateContent(promptText).text ?: "Could not generate summary."
                }
            } else {
                generativeModel.generateContent(promptText).text ?: "Could not generate summary."
            }
        }
    }
}
