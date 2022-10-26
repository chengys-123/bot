package com.example.myapplication.models;

import com.google.gson.annotations.SerializedName;

public class UploadObject {
    @SerializedName("PhoneNumber")
    String PhoneNumber;
    @SerializedName("status")
    String status;
    @SerializedName("message")
    String message;
    @SerializedName("OTP")
    String OTP;
    @SerializedName("Balance")
    String Balance;
    @SerializedName("ReferenceNumber")
    String ReferenceNumber;
    @SerializedName("Credit")
    String Credit;
    @SerializedName("TranID")
    String TranID;
    @SerializedName("ReloadPIN")
    String ReloadPIN;
    @SerializedName("phoneStatus")
    String phoneStatus;
    @SerializedName("action")
    String action;

    public String getPhoneNumber() {
        return PhoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        PhoneNumber = phoneNumber;
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

    public String getOTP() {
        return OTP;
    }

    public void setOTP(String OTP) {
        this.OTP = OTP;
    }

    public String getBalance() {
        return Balance;
    }

    public void setBalance(String balance) {
        Balance = balance;
    }

    public String getReferenceNumber() {
        return ReferenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        ReferenceNumber = referenceNumber;
    }

    public String getCredit() {
        return Credit;
    }

    public void setCredit(String credit) {
        Credit = credit;
    }

    public String getTranID() {
        return TranID;
    }

    public void setTranID(String tranID) {
        TranID = tranID;
    }

    public String getReloadPIN() {
        return ReloadPIN;
    }

    public void setReloadPIN(String reloadPIN) {
        ReloadPIN = reloadPIN;
    }

    public String getPhoneStatus() {
        return phoneStatus;
    }

    public void setPhoneStatus(String phoneStatus) {
        this.phoneStatus = phoneStatus;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
