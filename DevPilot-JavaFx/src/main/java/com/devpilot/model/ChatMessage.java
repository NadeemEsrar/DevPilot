package com.devpilot.model;

import java.time.LocalDateTime;

/** One question/answer pair from the "chat_history" table. */
public class ChatMessage {
    private int id;
    private int userId;
    private int repoId;
    private String question;
    private String answer;
    private LocalDateTime timestamp;

    public ChatMessage() { }

    public ChatMessage(int userId, int repoId, String question, String answer) {
        this.userId = userId;
        this.repoId = repoId;
        this.question = question;
        this.answer = answer;
        this.timestamp = LocalDateTime.now();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public int getRepoId() { return repoId; }
    public void setRepoId(int repoId) { this.repoId = repoId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "ChatMessage{id=" + id + ", repoId=" + repoId + ", q='" + question
                + "', a='" + answer + "', at=" + timestamp + "}";
    }
}
