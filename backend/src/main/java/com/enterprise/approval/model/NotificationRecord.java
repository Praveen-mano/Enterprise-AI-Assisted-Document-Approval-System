package com.enterprise.approval.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification_records")
public class NotificationRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String recipientRole;

  @Column
  private String recipientEmail;

  @Column(nullable = false)
  private String channel;

  @Column(nullable = false)
  private String title;

  @Column(length = 4000)
  private String message;

  @Column(nullable = false)
  private Boolean readFlag = false;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getRecipientRole() {
    return recipientRole;
  }

  public void setRecipientRole(String recipientRole) {
    this.recipientRole = recipientRole;
  }

  public String getRecipientEmail() {
    return recipientEmail;
  }

  public void setRecipientEmail(String recipientEmail) {
    this.recipientEmail = recipientEmail;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Boolean getReadFlag() {
    return readFlag;
  }

  public void setReadFlag(Boolean readFlag) {
    this.readFlag = readFlag;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
