package com.example.myapplication.models.databaseModels;

public class KasikornModel {
    public int transactionID;
    public String date, time, amount, referenceNo, combinedDateTime;
    public boolean isApiSent;

    public int getTransactionID() {
        return transactionID;
    }

    public void setTransactionID(int transactionID) {
        this.transactionID = transactionID;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public void setReferenceNo(String referenceNo) {
        this.referenceNo = referenceNo;
    }

    public String getCombinedDateTime() {
        return combinedDateTime;
    }

    public void setCombinedDateTime(String combinedDateTime) {
        this.combinedDateTime = combinedDateTime;
    }

    public boolean isApiSent() {
        return isApiSent;
    }

    public void setApiSent(boolean apiSent) {
        isApiSent = apiSent;
    }
}
