package com.example.myapplication.interfaces;

import com.example.myapplication.models.UploadObject;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.http.Body;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface UploadTelcoInterface {
    @POST("rapidpay_api/check_telco")

    Call<UploadObject> uploadTelco(@Body JSONObject body);
}
