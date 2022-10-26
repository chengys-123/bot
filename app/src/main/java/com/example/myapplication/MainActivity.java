package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

  private static final String TAG = "access";
  Intent mServiceIntent;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    //start service
    mServiceIntent = new Intent(MainActivity.this, KasikornService.class);
    if (!isMyServiceRunning(getApplicationContext(), KasikornService.class)) {
      startService (mServiceIntent);
    } else if (isMyServiceRunning(getApplicationContext(), KasikornService.class)) {
      Log.i(TAG, "service already started: " + isMyServiceRunning(getApplicationContext(), KasikornService.class));
    }

    // work?

//    PeriodicWorkRequest saveRequest = new PeriodicWorkRequest.Builder(WorkerJob.class, 1, TimeUnit.HOURS).build();
//    WorkManager.getInstance(this).enqueue(saveRequest);

  }

  @Override
  protected void onDestroy() {
    stopService(mServiceIntent);
    Log.i(TAG, "onDestroy!");
    super.onDestroy();

  }



  private boolean isMyServiceRunning(Context context, Class<?> serviceClass) {
    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
    for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
      if (serviceClass.getName().equals(service.service.getClassName())) {
        return true;
      }
    }
    return false;
  }


}

//  String phoneOTP = otp;
//  Bundle phoneOTPBundle = new Bundle();
//                                        phoneOTPBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phoneOTP);
//
//                                                if (topUpBtn.size() == 0) {
//                                                if (wrongOTPSendCounter == 0) {
//                                                wrongOTPSendCounter += 1;
//                                                Thread.sleep(5000);
//                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
//@Override
//public void onCompleted(GestureDescription gestureDescription) {
//        super.onCompleted(gestureDescription);
//        List<AccessibilityNodeInfo> topUpBtn = source.findAccessibilityNodeInfosByText("Top Up");
//        if (topUpBtn.size() == 0) {
//        Log.i(TAG, "run: still in otp page... 2nd attempt");
//
//        otpWrongActionCall.clone().enqueue(new Callback<UploadObject>() {
//@Override
//public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
//        if (response.body() != null) {
//        try {
//        JSONObject otpWrongActionResponseObj2 = new JSONObject(new Gson().toJson(response.body()));
//        Log.i(TAG, "otpWrongActionResponseObj2: " + otpWrongActionResponseObj2);
//        if (otpWrongActionResponseObj2.get("status").equals("SUCCESS")) {
//        otp = otpWrongActionResponseObj2.get("OTP").toString();
//
//        String phoneOTP = otp;
//        Bundle phoneOTPBundle = new Bundle();
//        phoneOTPBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phoneOTP);
//        // to prevent account lock, continue wont be pressed?
//        // click again?
//
//        }
//        } catch (Exception e) {
//        e.printStackTrace();
//        }
//        }
//        }
//
//@Override
//public void onFailure(Call<UploadObject> call, Throwable t) {
//
//        }
//        });
//        }
//        }
//        }, null);
//        }
//        }

//                                        else if (otpWrongActionResponseObj.get("status").equals("ERROR")) {
//                                          Bundle phoneOTPBundle = new Bundle();
//                                          phoneOTPBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
//
//                                        }
