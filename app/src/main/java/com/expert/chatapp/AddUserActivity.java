package com.expert.chatapp;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;


import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.expert.chatapp.chatting.ChatActivity;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class AddUserActivity extends AppCompatActivity {
    private EditText recipientUsernameInput, recipientTokenInput;
    private Button startChatButton;
    private FirebaseFirestore db;
    private SharedPreferences preferences;
    private String currentUsername, currentToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_user);

        // Initialize Firestore & Preferences
        db = FirebaseFirestore.getInstance();
        preferences = getSharedPreferences("ChatAppPrefs", MODE_PRIVATE);
        currentUsername = preferences.getString("username", "");
        currentToken = preferences.getString("token", "");

        // UI Elements
        recipientUsernameInput = findViewById(R.id.recipientUsernameInput);
        recipientTokenInput = findViewById(R.id.recipientTokenInput);
        startChatButton = findViewById(R.id.startChatButton);

        // Disable button initially
        startChatButton.setEnabled(false);

        // Enable button when both fields are filled
        recipientUsernameInput.addTextChangedListener(new SimpleTextWatcher(() -> validateInput()));
        recipientTokenInput.addTextChangedListener(new SimpleTextWatcher(() -> validateInput()));

        // Start Chat Button Click
        startChatButton.setOnClickListener(v -> startChat());
    }

    private void validateInput() {
        String username = recipientUsernameInput.getText().toString().trim();
        String token = recipientTokenInput.getText().toString().trim();
        startChatButton.setEnabled(!TextUtils.isEmpty(username) && !TextUtils.isEmpty(token));
    }

/*
    private void startChat() {
        String recipientUsername = recipientUsernameInput.getText().toString().trim();
        String recipientToken = recipientTokenInput.getText().toString().trim();

        db.collection("users").document(recipientUsername).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                String storedToken = task.getResult().getString("token");
                if (storedToken != null && storedToken.equals(recipientToken)) {

                    // Create Chat ID (Ensuring consistency)
                    String chatId = generateChatId(currentUsername, recipientUsername);

                    // Create a chat entry for both users
                    Map<String, Object> chatData = new HashMap<>();
                    chatData.put("user1", currentUsername);
                    chatData.put("user2", recipientUsername);
                    chatData.put("lastMessage", "Say hi 👋");

                    chatData.put("timestamp", System.currentTimeMillis());
                    chatData.put("participants", Arrays.asList(currentUsername, recipientUsername));

                    db.collection("chats").document(chatId).set(chatData).addOnSuccessListener(aVoid -> {
                        // Open ChatActivity
                        Intent intent = new Intent(AddUserActivity.this, ChatActivity.class);
                        intent.putExtra("chatId", chatId);
                        intent.putExtra("recipientUsername", recipientUsername);
                        startActivity(intent);
                        finish();
                    }).addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to start chat!", Toast.LENGTH_SHORT).show();
                    });

                } else {
                    Toast.makeText(this, "Invalid Token!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "User not found!", Toast.LENGTH_SHORT).show();
            }
        });
    }
*/

    private void startChat() {
        String recipientUsername = recipientUsernameInput.getText().toString().trim();
        String recipientToken = recipientTokenInput.getText().toString().trim();

        db.collection("users").document(recipientUsername).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                String storedToken = task.getResult().getString("token");
                if (storedToken != null && storedToken.equals(recipientToken)) {

                    // Generate a unique chat ID
                    String chatId = generateChatId(currentUsername, recipientUsername);
                    DocumentReference chatRef = db.collection("chats").document(chatId);

                    chatRef.get().addOnSuccessListener(documentSnapshot -> {
                        if (!documentSnapshot.exists()) {
                            // Chat doesn't exist -> Create a new chat
                            Map<String, Object> chatData = new HashMap<>();
                            chatData.put("user1", currentUsername);
                            chatData.put("user2", recipientUsername);
                            chatData.put("lastMessage", "Say hi 👋");
                            chatData.put("timestamp", System.currentTimeMillis());
                            chatData.put("participants", Arrays.asList(currentUsername, recipientUsername));

                            chatRef.set(chatData).addOnSuccessListener(aVoid -> {
                                openChat(chatId, recipientUsername);
                            }).addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to start chat!", Toast.LENGTH_SHORT).show();
                            });

                        } else {
                            // Chat already exists -> Just open it
                            openChat(chatId, recipientUsername);
                        }
                    });

                } else {
                    Toast.makeText(this, "Invalid Token!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "User not found!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openChat(String chatId, String recipientUsername) {
        Intent intent = new Intent(AddUserActivity.this, ChatActivity.class);
        intent.putExtra("chatId", chatId);
        intent.putExtra("recipientUsername", recipientUsername);
        startActivity(intent);
        finish();
    }


    public static String generateChatId(String user1, String user2) {
        return (user1.compareTo(user2) < 0) ? user1 + "_" + user2 : user2 + "_" + user1;
    }
}
