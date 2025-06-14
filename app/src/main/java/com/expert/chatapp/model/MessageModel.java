package com.expert.chatapp.model;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MessageModel {
    private String messageId;
    private String sender;
    private String receiver;
    private String text;
    private long timestamp;
    private String status;

    public MessageModel() {}

    public MessageModel(String messageId, String sender, String receiver, String text, long timestamp, String status) {
        this.messageId = messageId;
        this.sender = sender;
        this.receiver = receiver;
        this.text = text;
        this.timestamp = timestamp;
        this.status = status;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getText() {
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getStatus() {
        return status;
    }


    public String getFormattedTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }


}
