package com.example.myapplication.models.databaseModels;

public class TelcoDatabaseModel {
  public int telcoID;
  public String phoneNumber, telcoType, tranID, reloadPIN;
  public boolean isSuccess;

  public int getTelcoID() {
    return telcoID;
  }

  public void setTelcoID(int telcoID) {
    this.telcoID = telcoID;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  public String getTelcoType() {
    return telcoType;
  }

  public void setTelcoType(String telcoType) {
    this.telcoType = telcoType;
  }

  public String getReloadPIN() {
    return reloadPIN;
  }

  public void setReloadPIN(String reloadPIN) {
    this.reloadPIN = reloadPIN;
  }

  public boolean isSuccess() {
    return isSuccess;
  }

  public void setSuccess(boolean success) {
    isSuccess = success;
  }

  public String getTranID() {
    return tranID;
  }

  public void setTranID(String tranID) {
    this.tranID = tranID;
  }
}
