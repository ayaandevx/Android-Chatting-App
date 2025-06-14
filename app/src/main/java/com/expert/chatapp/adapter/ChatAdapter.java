package com.expert.chatapp.adapter;
import static com.expert.chatapp.MainActivity.currentUsername;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.expert.chatapp.chatting.ChatActivity;
import com.expert.chatapp.R;
import com.expert.chatapp.model.ChatModel;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
    private Context context;
    private List<ChatModel> chatList;

    public ChatAdapter(Context context, List<ChatModel> chatList) {
        this.context = context;
        this.chatList = chatList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chat, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatModel chat = chatList.get(position);
        String otherUser = chat.getOtherParticipant();
        String chatId = chat.getChatId();


        holder.usernameTextView.setText(chat.getOtherParticipant());
        holder.lastMessageTextView.setText((chat.getLastMessage() == null || chat.getLastMessage().isEmpty()) ? "Say hi 👋" : chat.getLastMessage());
        // Check unread messages
        if (chat.getUnreadCount() > 0) {
            holder.lastMessageTextView.setTextColor(ContextCompat.getColor(context, R.color.unreadTextColor)); // Set RED color
            holder.unreadBadge.setVisibility(View.VISIBLE);
            holder.unreadBadge.setText(String.valueOf(chat.getUnreadCount())); // Show unread count
        } else {
            holder.lastMessageTextView.setTextColor(ContextCompat.getColor(context, R.color.readTextColor)); // Set NORMAL color
            holder.unreadBadge.setVisibility(View.GONE);
        }


        String formattedTime = chat.getFormattedTimestamp();
//        holder.timestampTextView.setText("🕒 " + (formattedTime.isEmpty() ? "Now" : formattedTime));
        holder.timestampTextView.setText((formattedTime.isEmpty() ? "Now" : formattedTime));




        holder.itemView.setOnClickListener(v -> {
            if (chatId != null && !chatId.isEmpty() && otherUser != null && !otherUser.equals("Unknown User")) {
                Intent intent = new Intent(context, ChatActivity.class);
                intent.putExtra("chatId", chatId);
                intent.putExtra("recipientUsername", otherUser);

                // Mark chat as read
                FirebaseFirestore.getInstance().collection("chats").document(chatId)
                        .update("unreadCount." + currentUsername, 0) // Reset unread count
                        .addOnSuccessListener(aVoid -> {
                            chat.setUnreadCount(0);
                            notifyItemChanged(position); // Refresh UI
                        }).addOnFailureListener(e -> {
                            Toast.makeText(context, "Failed to update read status", Toast.LENGTH_SHORT).show();
                        });

                context.startActivity(intent);
            } else {
                Toast.makeText(context, "Error: Chat data is incomplete", Toast.LENGTH_SHORT).show();
            }
        });




    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView usernameTextView, lastMessageTextView,timestampTextView,unreadBadge;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            usernameTextView = itemView.findViewById(R.id.usernameTextView);
            lastMessageTextView = itemView.findViewById(R.id.lastMessageTextView);
            timestampTextView = itemView.findViewById(R.id.timestampTextView);
            unreadBadge = itemView.findViewById(R.id.unreadBadge);
        }
    }
}

