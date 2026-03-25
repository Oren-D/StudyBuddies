package com.example.studybuddies.ui.home

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddies.R
import com.example.studybuddies.data.model.ChatMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ChatActivity : AppCompatActivity() {

    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var rvMessages: RecyclerView
    private lateinit var chatAdapter: ChatAdapter

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val messagesList = mutableListOf<ChatMessage>()

    private var targetUid: String = ""
    private var targetName: String = ""
    private var chatId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        targetUid = intent.getStringExtra("TARGET_UID") ?: ""
        targetName = intent.getStringExtra("TARGET_NAME") ?: "Chat"

        val myUid = auth.currentUser?.uid ?: ""
        if (myUid.isEmpty() || targetUid.isEmpty()) {
            Toast.makeText(this, "Error: Invalid users for chat.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        chatId = if (myUid < targetUid) "${myUid}_$targetUid" else "${targetUid}_$myUid"

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = targetName
        toolbar.setNavigationOnClickListener { finish() }

        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        chatAdapter = ChatAdapter(messagesList)
        rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvMessages.adapter = chatAdapter

        setupFirestoreListener()

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
            }
        }
    }

    private fun setupFirestoreListener() {
        db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Listen failed: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val msgs = snapshot.toObjects(ChatMessage::class.java)
                    chatAdapter.updateMessages(msgs)
                    if (msgs.isNotEmpty()) {
                        rvMessages.smoothScrollToPosition(msgs.size - 1)
                    }
                }
            }
    }

    private fun sendMessage(text: String) {
        val user = auth.currentUser
        if (user == null) return

        val message = ChatMessage(
            id = "",
            text = text,
            senderId = user.uid,
            senderEmail = user.email ?: "Unknown",
            timestamp = System.currentTimeMillis()
        )

        val messageRef = db.collection("chats").document(chatId).collection("messages").document()
        val finalMessage = message.copy(id = messageRef.id)

        messageRef.set(finalMessage)
            .addOnSuccessListener {
                etMessage.text.clear()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
