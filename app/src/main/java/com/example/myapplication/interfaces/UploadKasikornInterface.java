package com.example.myapplication.interfaces;

import com.example.myapplication.models.UploadKasikornObject;

import org.json.JSONObject;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface UploadKasikornInterface {
  @POST("rapidpay_api/get_qrcode_notification")
  Call<UploadKasikornObject> uploadTelco(@Body JSONObject body);
}
