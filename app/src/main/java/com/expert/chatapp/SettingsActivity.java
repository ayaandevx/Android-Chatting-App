package com.expert.chatapp;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SettingsActivity extends AppCompatActivity {
    private CheckBox darkModeToggle, disablePreviewsToggle;
    private Button resetTokenButton, logoutButton;
    private SharedPreferences preferences;
    private TextView usernameTextView, tokenTextView;
    private ImageButton copyUsername, copyToken;
    private FirebaseFirestore db;
    private String username;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        db = FirebaseFirestore.getInstance(); // Initialize Firestore

        // Initialize Preferences
        preferences = getSharedPreferences("ChatAppPrefs", MODE_PRIVATE);
        username = preferences.getString("username", "Unknown");
        String token = preferences.getString("token", "No Token");

        // UI Elements
        darkModeToggle = findViewById(R.id.darkModeToggle);
        disablePreviewsToggle = findViewById(R.id.disablePreviewsToggle);
        resetTokenButton = findViewById(R.id.resetTokenButton);
        logoutButton = findViewById(R.id.logoutButton);

        // Get references to UI elements
        usernameTextView = findViewById(R.id.userId);
        copyUsername = findViewById(R.id.copyUsername);

        tokenTextView = findViewById(R.id.tokenId);
        copyToken = findViewById(R.id.copyToken);

        // Display username & token
        usernameTextView.setText(username);
        tokenTextView.setText(token);

        // Copy Username
        usernameTextView.setOnClickListener(v -> copyToClipboard("Username", username));
        copyUsername.setOnClickListener(v -> copyToClipboard("Username", username));
        // Copy Token
        tokenTextView.setOnClickListener(v -> copyToClipboard("Token", token));
        copyToken.setOnClickListener(v -> copyToClipboard("Token", token));


        // Load Settings
        darkModeToggle.setChecked(preferences.getBoolean("dark_mode", false));
        disablePreviewsToggle.setChecked(preferences.getBoolean("disable_previews", false));

        // Toggle Dark Mode
        darkModeToggle.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean("dark_mode", isChecked).apply());

        // Toggle Message Previews
        disablePreviewsToggle.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean("disable_previews", isChecked).apply());

        // Reset Token
        resetTokenButton.setOnClickListener(v -> resetToken());

        // Logout
        logoutButton.setOnClickListener(v -> logout());
    }
    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, label + " copied!", Toast.LENGTH_SHORT).show();
    }

    private void resetToken() {
        String newToken = generateRandomToken();
        preferences.edit().putString("token", newToken).apply();
        tokenTextView.setText(newToken); // Update UI without recreating activity
        saveUserToFirestore(username, newToken);
    }

    private void saveUserToFirestore(String username, String token) {
        db.collection("users").document(username)
                .set(new SetupActivity.User(username, token))
                .addOnSuccessListener(aVoid -> {

                    startActivity(new Intent(SettingsActivity.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(SettingsActivity.this, "Error Resetting", Toast.LENGTH_SHORT).show());
    }


    private String generateRandomToken() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@";
        StringBuilder token = new StringBuilder(8);
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            token.append(chars.charAt(random.nextInt(chars.length())));
        }
        return token.toString();
    }


    private void logout() {
        // Create a styled TextView for the message
        TextView message = new TextView(this);
        message.setText("If you log out, your username and data will be permanently deleted, along with all chats. Do you want to proceed?");
        message.setTextSize(16);
        message.setTextColor(Color.RED); // Set text color to red
        message.setPadding(40, 30, 40, 30); // Adjust padding for better spacing
        message.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START); // Ensures left alignment
        message.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); // Ensures proper left alignment

        // Create AlertDialog with modern styling
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("\uD83D\uDEA8 Logout Warning")
                .setView(message) // Set custom message view
                .setPositiveButton("Yes, Logout", (dialog, which) -> {
                    // Remove from Firestore and clear local data
                    deleteUserChats();
                })
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .setCancelable(false) // Prevent accidental dismissals
                .show();
    }

    private void deleteUserChats() {
        String username = preferences.getString("username", null);
        if (username == null) {
            logoutAndClearData();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<Task<Void>> deleteChatTasks = new ArrayList<>();

        db.collection("chats")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        // No chats found, proceed with user deletion
                        removeUserFromFirestore(username);
                        return;
                    }

                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String chatId = document.getId(); // Get the chat ID

                        //  Check if the username is part of this chat ID
                        if (chatId.contains(username)) {
                            // First, delete all messages inside this chat
                            Task<Void> deleteMessagesTask = deleteAllMessagesInChat(chatId);

                            // After deleting messages, delete the chat document
                            Task<Void> deleteChatTask = deleteMessagesTask.continueWithTask(task -> {
                                if (task.isSuccessful()) {
                                    return document.getReference().delete();
                                } else {
                                    throw task.getException();
                                }
                            });

                            deleteChatTasks.add(deleteChatTask);
                        }
                    }

                    //  Ensure all chats and messages are deleted before removing the user
                    Tasks.whenAllSuccess(deleteChatTasks)
                            .addOnSuccessListener(aVoid -> removeUserFromFirestore(username))
                            .addOnFailureListener(e ->
                                    Toast.makeText(SettingsActivity.this, "Error deleting chats", Toast.LENGTH_SHORT).show()
                            );
                })
                .addOnFailureListener(e ->
                        Toast.makeText(SettingsActivity.this, "Error finding chats", Toast.LENGTH_SHORT).show()
                );
    }

    /**
     *  Deletes all messages inside a chat before deleting the chat itself.
     */
    private Task<Void> deleteAllMessagesInChat(String chatId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        CollectionReference messagesRef = db.collection("chats").document(chatId).collection("messages");

        return messagesRef.get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }

            List<Task<Void>> deleteTasks = new ArrayList<>();
            for (QueryDocumentSnapshot document : task.getResult()) {
                deleteTasks.add(document.getReference().delete());
            }

            return Tasks.whenAll(deleteTasks);
        });
    }

    //  Step 2: After Deleting Chats, Delete User Document
    private void removeUserFromFirestore(String username) {
        FirebaseFirestore.getInstance().collection("users").document(username)
                .delete()
                .addOnSuccessListener(aVoid -> logoutAndClearData())  //  Only logout after user is deleted
                .addOnFailureListener(e ->
                        Toast.makeText(SettingsActivity.this, "Error removing user", Toast.LENGTH_SHORT).show()
                );
    }

    //  Step 3: Finally, Clear Local Data and Logout
    private void logoutAndClearData() {
        preferences.edit().clear().apply();

        Intent intent = new Intent(SettingsActivity.this, SetupActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

}

