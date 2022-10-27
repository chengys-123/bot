package com.example.myapplication.models;

import com.google.gson.annotations.SerializedName;

public class UploadKasikornObject {
  @SerializedName("status")
  String status;
  @SerializedName("message")
  String message;
  @SerializedName("Amount")
  String Amount;
  @SerializedName("Datetime")
  String Datetime;
  @SerializedName("ReferenceNumber")
  String SMSRef;

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

  public String getAmount() {
    return Amount;
  }

  public void setAmount(String amount) {
    Amount = amount;
  }

  public String getDatetime() {
    return Datetime;
  }

  public void setDatetime(String datetime) {
    Datetime = datetime;
  }

  public String getSMSRef() {
    return SMSRef;
  }

  public void setSMSRef(String SMSRef) {
    this.SMSRef = SMSRef;
  }
}
