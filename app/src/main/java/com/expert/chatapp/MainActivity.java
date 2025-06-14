package com.expert.chatapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.expert.chatapp.adapter.ChatAdapter;
import com.expert.chatapp.model.ChatModel;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private RecyclerView chatListRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatModel> chatList;
    private FirebaseFirestore db;
    private SharedPreferences preferences;
    public static String currentUsername;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firestore & SharedPreferences
        db = FirebaseFirestore.getInstance();
        preferences = getSharedPreferences("ChatAppPrefs", MODE_PRIVATE);
        currentUsername = preferences.getString("username", "");

        // UI Elements
        chatListRecyclerView = findViewById(R.id.chatListRecyclerView);
        FloatingActionButton AddUserChatFab = findViewById(R.id.newChatFab);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        // RecyclerView Setup
        chatListRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatList = new ArrayList<>();
        chatAdapter = new ChatAdapter(this, chatList);
        chatListRecyclerView.setAdapter(chatAdapter);


        // Fetch active chats
        loadChatList();

        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadChatList();
            swipeRefreshLayout.setRefreshing(false);
        });

        // New Chat Button Click
       AddUserChatFab.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, AddUserActivity.class))
        );




        // Initialize Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle("Chat App");

    }

    private void loadChatList() {
        if (currentUsername == null || currentUsername.isEmpty()) {
            Toast.makeText(this, "Error: Username not found", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("chats")
                .whereArrayContains("participants", currentUsername)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Error loading chats", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    chatList.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Log.d("FirestoreDebug", "Chat Document: " + doc.getData());
                            String chatId = doc.getId();
                            List<String> participants = (List<String>) doc.get("participants");
                            String lastMessage = doc.getString("lastMessage");

                            // Get unread count for the current user
                            long unreadCount = 0;
                            if (doc.contains("unreadCount") && doc.get("unreadCount") instanceof java.util.Map) {
                                unreadCount = doc.getLong("unreadCount." + currentUsername) != null ?
                                        doc.getLong("unreadCount." + currentUsername) : 0;
                            }

                            // Convert Firestore Timestamp to long (milliseconds)
                            Long timestamp = 0L;
                            Object tsObj = doc.get("timestamp");
                            if (tsObj instanceof com.google.firebase.Timestamp) {
                                timestamp = ((com.google.firebase.Timestamp) tsObj).getSeconds() * 1000; // Convert to milliseconds
                            } else if (tsObj instanceof Long) {
                                timestamp = (Long) tsObj;
                            }

                            if (chatId != null && participants != null && participants.contains(currentUsername) && participants.size() >= 2) {
                                chatList.add(new ChatModel(chatId, participants, lastMessage, unreadCount, timestamp));
                            }
                        }
                        chatAdapter.notifyDataSetChanged();
                    }
                });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.settings) {
            // Handle search button click
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            return true;
        } else if (id == R.id.logout) {
            Toast.makeText(this, "Logging out...", Toast.LENGTH_SHORT).show();
            logout();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadChatList();
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
                                    Toast.makeText(MainActivity.this, "Error deleting chats", Toast.LENGTH_SHORT).show()
                            );
                })
                .addOnFailureListener(e ->
                        Toast.makeText(MainActivity.this, "Error finding chats", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(MainActivity.this, "Error removing user", Toast.LENGTH_SHORT).show()
                );
    }

    //  Step 3: Finally, Clear Local Data and Logout
    private void logoutAndClearData() {
        preferences.edit().clear().apply();

        Intent intent = new Intent(MainActivity.this, SetupActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

}
