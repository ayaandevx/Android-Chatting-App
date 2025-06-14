package com.expert.chatapp.chatting;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.expert.chatapp.MainActivity;
import com.expert.chatapp.R;
import com.expert.chatapp.model.MessageModel;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;


import java.util.*;

public class ChatActivity extends AppCompatActivity {
    private TextView chatHeader;
    private RecyclerView chatRecyclerView;
    private EditText messageInput;
    private ImageButton sendButton, attachButton, voiceNoteButton;
    private ChatMessageAdapter chatMessageAdapter;
    private List<MessageModel> messageList;
    private FirebaseFirestore db;
    private String chatId, recipientUsername, currentUsername;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.expert.chatapp.R.layout.activity_chat);

        // Get chat details from Intent
        chatId = getIntent().getStringExtra("chatId");
        recipientUsername = getIntent().getStringExtra("recipientUsername");


        if (TextUtils.isEmpty(chatId) || TextUtils.isEmpty(recipientUsername)) {
            Toast.makeText(this, "Error: Missing chat data", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Initialize Firestore & Preferences
        db = FirebaseFirestore.getInstance();
        preferences = getSharedPreferences("ChatAppPrefs", MODE_PRIVATE);
        currentUsername = preferences.getString("username", "");

        // UI Elements
        chatHeader = findViewById(com.expert.chatapp.R.id.chatHeader);
        chatRecyclerView = findViewById(com.expert.chatapp.R.id.chatRecyclerView);
        messageInput = findViewById(com.expert.chatapp.R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);



        // Set Chat Header
        chatHeader.setText(recipientUsername);

        // RecyclerView Setup
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // Ensures the latest message appears at the bottom
        chatRecyclerView.setLayoutManager(layoutManager);



        messageList = new ArrayList<>();
        chatMessageAdapter = new ChatMessageAdapter(this, messageList, currentUsername);
        chatRecyclerView.setAdapter(chatMessageAdapter);

        // Load Messages & Mark As Read
        loadMessages();
        markMessagesAsRead();

        // Send Button Click
        sendButton.setOnClickListener(v -> sendMessage());

    }

    private void loadMessages() {

        if (TextUtils.isEmpty(chatId)) {
            Toast.makeText(this, "Error: Invalid chat session", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("chats").document(chatId).collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Error loading messages", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    for (DocumentChange change : value.getDocumentChanges()) {
                        if (change.getType() == DocumentChange.Type.ADDED) {

                            DocumentSnapshot doc = change.getDocument();

                            //Handle Firestore Timestamp correctly
                            long timestamp = 0;
                            // Could be a Timestamp object
                            Object tsObj = doc.get("timestamp");

                            if (tsObj instanceof Timestamp) {
                                timestamp = ((Timestamp) tsObj).toDate().getTime(); // Convert to milliseconds
                            } else if (tsObj instanceof Long) {
                                timestamp = (Long) tsObj; // Already in milliseconds
                            }

                            MessageModel message = new MessageModel(
                                    doc.getId(), // messageId
                                    doc.getString("sender"),
                                    doc.getString("receiver"),
                                    doc.getString("text"),
                                    timestamp,
                                    doc.getString("status")
                            );


                            messageList.add(message);

                        }
                    }
                    chatMessageAdapter.notifyDataSetChanged();
//                    chatRecyclerView.post(() -> chatRecyclerView.smoothScrollToPosition(messageList.size() - 1));
//                    chatRecyclerView.postDelayed(() -> chatRecyclerView.smoothScrollToPosition(messageList.size() - 1), 200);
                    chatRecyclerView.postDelayed(() -> {
                        if (!messageList.isEmpty()) {
                            chatRecyclerView.smoothScrollToPosition(messageList.size() - 1);
                        }
                    }, 200);
                });
    }


    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        Map<String, Object> message = new HashMap<>();
        message.put("sender", MainActivity.currentUsername);
        message.put("receiver", recipientUsername); // Store receiver info
        message.put("text", text);
        message.put("status", "sent"); // Default status
        message.put("timestamp", FieldValue.serverTimestamp());


        db.collection("chats").document(chatId).collection("messages")
                .add(message)
                .addOnSuccessListener(documentReference -> {
                    messageInput.setText(""); // Clear input after sending

                //  Also update "chats" collection with the latest unread count for recipient
                    db.collection("chats").document(chatId)
                            .update(
                                    "lastMessage", text,
                                    "lastMessageStatus", "sent",
                                    "timestamp", FieldValue.serverTimestamp(),
                                    "unreadCount." + recipientUsername, FieldValue.increment(1) // Increase unread count
                            )
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed to update chat list", Toast.LENGTH_SHORT).show());
                }).addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show());

    }

    private void markMessagesAsRead() {
        db.collection("chats").document(chatId).collection("messages")
                .whereEqualTo("receiver", currentUsername)
                .whereEqualTo("status", "sent")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        doc.getReference().update("status", "read");
                    }

                    // Reset unread count in Firestore for this user
                    db.collection("chats").document(chatId)
                            .update(
                                    "lastMessageStatus", "read",
                                    "unreadCount." + currentUsername, 0 // Reset unread count
                            )
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed to update chat unread status", Toast.LENGTH_SHORT).show());

                    chatMessageAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update messages as read", Toast.LENGTH_SHORT).show());
    }




    public static class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ViewHolder> {
        private Context context;
        private List<MessageModel> messageList;
        private String currentUsername;

        public ChatMessageAdapter(Context context, List<MessageModel> messageList, String currentUsername) {
            this.context = context;
            this.messageList = messageList;
            this.currentUsername = currentUsername;
        }

//
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(
                    viewType == 1 ? R.layout.item_sent_message : R.layout.item_received_message,
                    parent, false);
            return new ViewHolder(view);
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MessageModel message = messageList.get(position);
            holder.messageTextView.setText(message.getText());
            holder.timestampTextView.setText(message.getFormattedTimestamp());

            // Apply a fade-in animation for new messages
            holder.itemView.setAlpha(0f);
            holder.itemView.animate().alpha(1f).setDuration(300).start();




            // Set bold text for unread messages
            if ("sent".equals(message.getStatus())) {
                holder.messageTextView.setTypeface(null, Typeface.BOLD);
            } else {
                holder.messageTextView.setTypeface(null, Typeface.NORMAL);
            }

            //  Seen
            if ("read".equals(message.getStatus())) {
                holder.seenView.setText("\u2713\u2713 ");
                holder.seenView.setTextColor(Color.parseColor("#004D40"));
            } else {
                // Not-Seen
                holder.seenView.setText("\u2713\u2713 ");
                holder.seenView.setTextColor(Color.parseColor("#ffffff"));
            }
        }

//        @Override
//        public int getItemViewType(int position) {
//            // Force RecyclerView to differentiate unread messages
//            return ("sent".equals(messageList.get(position).getStatus())) ? 1 : 0;
//        }
        @Override
        public int getItemViewType(int position) {
            MessageModel message = messageList.get(position);
            return message.getSender().equals(currentUsername) ? 1 : 0; // 1 = sent, 0 = received
        }


        @Override
        public int getItemCount() {
            return messageList.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView messageTextView, timestampTextView,seenView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                messageTextView = itemView.findViewById(R.id.messageTextView);
                timestampTextView = itemView.findViewById(R.id.timestampTextView);
                seenView = itemView.findViewById(R.id.seenView);
            }
        }
    }
}
