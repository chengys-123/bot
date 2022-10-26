package com.example.myapplication.models;

import com.google.gson.annotations.SerializedName;

public class CheckBotStatus {
  @SerializedName("secret_key")
  String secret_key;
  @SerializedName("status")
  String status;
  @SerializedName("message")
  String message;

  public String getSecret_key() {
    return secret_key;
  }

  public void setSecret_key(String secret_key) {
    this.secret_key = secret_key;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
