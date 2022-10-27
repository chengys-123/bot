package com.example.myapplication.utils;

import com.example.myapplication.interfaces.CheckBotStatusInterface;
import com.example.myapplication.interfaces.UploadKasikornInterface;
import com.example.myapplication.interfaces.UploadTelcoInterface;

public class ApiUtils {
    private ApiUtils() {}

    public static final String BASE_URL = "https://bo.rapidpays.com/";

    public static UploadTelcoInterface getUploadTelcoInterface() {

        return RetrofitClient.getClient(BASE_URL).create(UploadTelcoInterface.class);
    }

    public static UploadKasikornInterface getUploadKasikornInterface() {

        return RetrofitClient.getClient(BASE_URL).create(UploadKasikornInterface.class);
    }

    public static CheckBotStatusInterface getCheckBotStatusInterface() {

        return RetrofitClient.getClient(BASE_URL).create(CheckBotStatusInterface.class);
    }
}