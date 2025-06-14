package com.expert.chatapp.model;
import com.google.firebase.Timestamp;
import com.expert.chatapp.MainActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatModel {
    private String chatId;
    private List<String> participants;
    private String lastMessage;
    private long unreadCount;
    private long timestamp;

    public ChatModel() {}

    public ChatModel(String chatId, List<String> participants, String lastMessage, long unreadCount, long timestamp) {
        this.chatId = chatId;
        this.participants = participants;
        this.lastMessage = lastMessage;
        this.unreadCount = unreadCount;
        this.timestamp = timestamp;
    }

    public String getChatId() {
        return chatId;
    }

    public List<String> getParticipants() {
        return participants;
    }

    public String getLastMessage() {
        return lastMessage;
    }

//    public boolean isUnread() {
//        return "sent".equals(unreadCount); // Check if the last message is still "sent"
//    }

    public long getUnreadCount() { return unreadCount; }
    public void setUnreadCount(long unreadCount) { this.unreadCount = unreadCount; }




    public String getFormattedTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public String getOtherParticipant() {
        if (participants != null && participants.size() >= 2 && MainActivity.currentUsername != null) {
            return participants.get(0).equals(MainActivity.currentUsername) ?
                    participants.get(1) : participants.get(0);
        }
        return "Unknown User"; // Fallback value to prevent null issues
    }
}
