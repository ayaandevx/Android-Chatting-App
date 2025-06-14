package com.expert.chatapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Random;
import java.util.UUID;


public class SetupActivity extends AppCompatActivity {
    private EditText usernameInput;
    private TextView tokenDisplay;
    private Button continueButton;
    private SharedPreferences preferences;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if the user has already set up the app
        preferences = getSharedPreferences("ChatAppPrefs", MODE_PRIVATE);
        String storedUsername = preferences.getString("username", "");

        if (!storedUsername.isEmpty()) {
            // User already set up, go to MainChatHomeActivity
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_setup);

        usernameInput = findViewById(R.id.usernameInput);
        tokenDisplay = findViewById(R.id.tokenDisplay);
        continueButton = findViewById(R.id.continueButton);
        db = FirebaseFirestore.getInstance();

        String storedToken = preferences.getString("token", "");
        if (storedToken.isEmpty()) {
            storedToken =generateToken();
            preferences.edit().putString("token", storedToken).apply();
        }

        tokenDisplay.setText("Token: " + storedToken);

        String finalStoredToken = storedToken;
        continueButton.setOnClickListener(v -> {
            String username = usernameInput.getText().toString().trim();
            if (!username.isEmpty()) {
                checkUsernameAvailability(username, finalStoredToken);
            } else {
                Toast.makeText(this, "Please enter a username!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkUsernameAvailability(String username, String token) {
        db.collection("users").document(username).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    Toast.makeText(SetupActivity.this, "Username already exists! Try another.", Toast.LENGTH_SHORT).show();
                } else {
                    // Username available, save it
                    preferences.edit().putString("username", username).apply();
                    saveUserToFirestore(username, token);
                }
            } else {
                Toast.makeText(SetupActivity.this, "Error checking username. Try again!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveUserToFirestore(String username, String token) {
        db.collection("users").document(username)
                .set(new User(username, token))
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(SetupActivity.this, "Username registered successfully!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(SetupActivity.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(SetupActivity.this, "Error saving user!", Toast.LENGTH_SHORT).show());
    }

    private String generateToken() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@";
        Random random = new Random();
        StringBuilder token = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            token.append(characters.charAt(random.nextInt(characters.length())));
        }
        return token.toString();
    }

    public static class User {
        public String username;
        public String token;

        public User() {}

        public User(String username, String token) {
            this.username = username;
            this.token = token;
        }
    }
}

