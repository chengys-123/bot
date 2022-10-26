package com.example.myapplication.interfaces;

import com.example.myapplication.models.CheckBotStatus;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface CheckBotStatusInterface {
  @Multipart
  @POST("rapidpay_api/check_bot_status")

  Call<CheckBotStatus> checkBotStatus(@Part("secret_key") RequestBody secret_key);
}
