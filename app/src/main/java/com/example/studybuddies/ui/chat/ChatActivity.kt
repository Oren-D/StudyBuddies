package com.example.studybuddies.ui.chat

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddies.R
import com.example.studybuddies.data.model.ChatMessage

/**
 *This is the screen where users can send messages.
 * It uses IChatManager(Interface) to fetch messages from the database.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var rvMessages: RecyclerView
    private lateinit var chatAdapter: ChatAdapter

    private lateinit var chatManager: IChatManager
    private val messagesList = mutableListOf<ChatMessage>() //changeable list

    private var targetUid: String = ""
    private var targetName: String = ""
    private var chatId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        targetUid = intent.getStringExtra("TARGET_UID") ?: ""
        targetName = intent.getStringExtra("TARGET_NAME") ?: "Chat"

        chatManager = FirebaseChatManager()
        
        val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (myUid.isEmpty() || targetUid.isEmpty()) {
            Toast.makeText(this, "Error: Invalid users for chat.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        chatId = if (targetUid == "GLOBAL_CHAT") "GLOBAL_CHAT" else if (myUid < targetUid) "${myUid}_$targetUid" else "${targetUid}_$myUid"

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

    private fun setupFirestoreListener() { //Get all Chat messages
        chatManager.listenToMessages(chatId) { msgs, error ->
            if (error != null) {
                Toast.makeText(this, "Failed to load messages due to: ${error.message}", Toast.LENGTH_SHORT).show()
                return@listenToMessages
            }

            if (msgs != null) {
                chatAdapter.updateMessages(msgs)
                if (msgs.isNotEmpty()) {
                    rvMessages.smoothScrollToPosition(msgs.size - 1)
                }
            }
        }
    }

    private fun sendMessage(text: String) {
        chatManager.sendMessage(chatId, targetUid, text) { success, errorMsg ->
            if (success) {
                etMessage.text.clear()
            } else {
                Toast.makeText(this, "Failed to send: $errorMsg", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        chatManager.cleanup()
    }
}
