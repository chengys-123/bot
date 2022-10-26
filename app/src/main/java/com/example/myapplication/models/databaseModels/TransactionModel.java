package com.example.myapplication.models.databaseModels;

public class TransactionModel {
  private int bankId, merchantId;
  private float amount;
  private String datetime;

  public TransactionModel(int bankId, int merchantId, float amount, String datetime) {
    this.bankId = bankId;
    this.merchantId = merchantId;
    this.amount = amount;
    this.datetime = datetime;
  }

  public int getBankId() {
    return bankId;
  }

  public void setBankId(int bankId) {
    this.bankId = bankId;
  }

  public int getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(int merchantId) {
    this.merchantId = merchantId;
  }

  public float getAmount() {
    return amount;
  }

  public void setAmount(float amount) {
    this.amount = amount;
  }

  public String getDatetime() {
    return datetime;
  }

  public void setDatetime(String datetime) {
    this.datetime = datetime;
  }
}
