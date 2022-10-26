package com.example.myapplication;
// clear records every 2 weeks -> pull out for copy

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Path;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.example.myapplication.SQLiteDatabaseHandler.KasikornDatabaseHandler;
import com.example.myapplication.SQLiteDatabaseHandler.TelcoDatabaseHandler;
import com.example.myapplication.interfaces.CheckBotStatusInterface;
import com.example.myapplication.interfaces.UploadTelcoInterface;
import com.example.myapplication.models.CheckBotStatus;
import com.example.myapplication.models.Error400POJO;
import com.example.myapplication.models.Error401POJO;
import com.example.myapplication.models.UploadObject;
import com.example.myapplication.models.databaseModels.KasikornModel;
import com.example.myapplication.models.databaseModels.TelcoDatabaseModel;
import com.example.myapplication.models.databaseModels.TransactionModel;
import com.example.myapplication.utils.ApiUtils;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HotlinkService extends AccessibilityService {
  // counters
  public int wrongOTPSendCounter = 0;
  public int callPhoneNUmOnceCtr = 0;
  public int callOTPOnceCtr = 0;
  public int callOnceCounter = 0;
  public int callOnceCounterForSuccess = 0;

  private DateFormat TWELVE_TF = new SimpleDateFormat("hh:mma");
  private DateFormat TWENTY_FOUR_TF = new SimpleDateFormat("HH:mm");
  private DateFormat DATE_TF = new SimpleDateFormat("yyyy-MM-dd");

  private static final String TAG = "access";
  String otp = "";
  String balance = "";
  String tranID = "";
  boolean isCalled = false;

  final Handler ha = new Handler();
  final Handler reloadPINHandler = new Handler();
  final Handler OTPHandler = new Handler();
  Handler OTPHandler2 = new Handler();
  final Handler requestOTPAgainHandler = new Handler();
  final Handler delayMainPageHandler = new Handler();
  final Handler reloadPINAgainHandler = new Handler();
  final Handler getUpdatedBalanceHandler = new Handler();
  final Handler checkBotHandler = new Handler();

  CheckBotStatusInterface checkBotStatusInterface;
  // retrofits

  UploadTelcoInterface uploadTelcoInterface;

  SharedPreferences preferences;
  SharedPreferences.Editor editor;
  Context context;;
  boolean waitingToResolve;
  AccessibilityNodeInfo getRootNode;
  AccessibilityNodeInfo getOTPRootNode;

  TelcoDatabaseHandler telcoDatabaseHandler;
  TelcoDatabaseModel telcoDatabaseModel;

  private static final DecimalFormat df = new DecimalFormat("0.00");
  protected void onServiceConnected() {
    super.onServiceConnected();
    Log.i(TAG, "onServiceConnected: hotlink connected!");

    Intent launchIntent = getPackageManager().getLaunchIntentForPackage("my.com.maxis.hotlink.production");
    if (launchIntent != null) {
      startActivity(launchIntent);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.R)
  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    context = MyApplication.getAppContext();
    preferences = PreferenceManager.getDefaultSharedPreferences(context);
    editor = preferences.edit();
    checkBotStatusInterface = ApiUtils.getCheckBotStatusInterface();

    context = MyApplication.getAppContext();
    telcoDatabaseHandler = new TelcoDatabaseHandler(context);

    getViewableTransactionData(event);

//    checkBotHandler.postDelayed(new Runnable() {
//      @Override
//      public void run() {
//        RequestBody secret_key = RequestBody.create(MediaType.parse("text/plain"), "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
//
//        Call<CheckBotStatus> call = checkBotStatusInterface.checkBotStatus(secret_key);
//        call.enqueue(new Callback<CheckBotStatus>() {
//          @Override
//          public void onResponse(@NonNull Call<CheckBotStatus> call, @NonNull Response<CheckBotStatus> response) {
//            Log.i(TAG, "message: " + response.message());
//          }
//
//          @Override
//          public void onFailure(Call<CheckBotStatus> call, Throwable t) {
//            t.printStackTrace();
//          }
//        });
//      }
//    }, 10000);


  }

  @RequiresApi(api = Build.VERSION_CODES.R)
  public void getViewableTransactionData(AccessibilityEvent event) {
    try {

      uploadTelcoInterface = ApiUtils.getUploadTelcoInterface();

      AccessibilityNodeInfo source = event.getSource();

      // login --> OK!
      if (callPhoneNUmOnceCtr == 0) {
        callPhoneNUmOnceCtr += 1;

        List<AccessibilityNodeInfo> loginBtn = source.findAccessibilityNodeInfosByViewId("my.com.maxis.hotlink.production:id/btnLogin");
        if (loginBtn.size() != 0) {
          for (AccessibilityNodeInfo loginBtnNode : loginBtn) {
            try {
              JSONObject postLoginJsonObj = new JSONObject();
              postLoginJsonObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
              postLoginJsonObj.put("PhoneNumber", "");

              Call<UploadObject> call = uploadTelcoInterface.uploadTelco(postLoginJsonObj);
              call.enqueue(new Callback<UploadObject>() {
                @Override
                public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                  try {
                    if (response.body() == null) {
                      Log.i(TAG, "Waiting for phone num...");

                      reloadPINHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                          //call function
                          call.clone().enqueue(new Callback<UploadObject>() {
                            @Override
                            public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                              try {
                                if (response.body() == null) {
                                  Log.i(TAG, "Waiting for phone num...");
                                } else {
                                  JSONObject loginObj = new JSONObject(new Gson().toJson(response.body()));
                                  if (loginObj.get("status").equals("SUCCESS")) {
                                    reloadPINHandler.removeCallbacksAndMessages(null);
                                    editor.putString("phoneNum", loginObj.get("PhoneNumber").toString());
                                    editor.commit();

                                    loginBtnNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                    performGlobalAction(GLOBAL_ACTION_RECENTS);
                                    try{Thread.sleep(2000);} catch (Exception e) {};
                                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage("my.com.maxis.hotlink.production");
                                    if (launchIntent != null) {
                                      startActivity(launchIntent);
                                    }


                                  }
                                }
                              } catch (Exception e) {
                                e.printStackTrace();
                              }
                            }
                            @Override
                            public void onFailure(Call<UploadObject> call, Throwable t) {
                              Log.e(TAG, "onFailure: ", t);
                            }
                          });

                          reloadPINHandler.postDelayed(this, 5000);
                        }
                      }, 5000);
                    }
                    else {
                      JSONObject loginObj = new JSONObject(new Gson().toJson(response.body()));

                      if (loginObj.get("status").equals("SUCCESS")) {
                        editor.putString("phoneNum", loginObj.get("PhoneNumber").toString());
                        editor.commit();
                        reloadPINHandler.removeCallbacksAndMessages(null);
                        loginBtnNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                      }
                    }
                  } catch (Exception e) {
                    e.printStackTrace();
                  }
                }
                @Override
                public void onFailure(Call<UploadObject> call, Throwable t) {
                  Log.e(TAG, "onFailure: ", t);
                }
              });
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        }
      }

      /** Login with hp num **/
      getRootNode = getRootInActiveWindow();
      if (getRootNode.getChild(1).getClassName().equals("android.webkit.WebView")) {
        String phoneNum = preferences.getString("phoneNum", "");
        Bundle MobileNoBundle = new Bundle();
        MobileNoBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phoneNum.substring(1));

        getRootNode.getChild(1).getChild(0).getChild(0).getChild(0).getChild(2).getChild(0).getChild(1).getChild(1).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, MobileNoBundle);
        Thread.sleep(1000);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        Path path = new Path();
        path.moveTo(261, 687);
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
          @Override
          public void onCompleted(GestureDescription gestureDescription) {
            super.onCompleted(gestureDescription);
            try {
              Thread.sleep(2000);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }
        }, null);
      }
//      if (source.getClassName().equals("android.webkit.WebView")) {
//        String phoneNum = preferences.getString("phoneNum", "");
//        Bundle MobileNoBundle = new Bundle();
//        MobileNoBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phoneNum.substring(1));
//
//        source.getChild(2).getChild(0).getChild(1).getChild(1).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, MobileNoBundle);
//        Thread.sleep(1000);
//        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
//        Path path = new Path();
//        path.moveTo(261, 687);
//        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
//
//        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
//          @Override
//          public void onCompleted(GestureDescription gestureDescription) {
//            super.onCompleted(gestureDescription);
//            try {
//              Thread.sleep(2000);
//            } catch (InterruptedException e) {
//              e.printStackTrace();
//            }
//          }
//        }, null);
//      }

      /** OTP input **/
      getOTPRootNode = getRootInActiveWindow();
      if (getOTPRootNode.getChild(1).getClassName().equals("android.webkit.WebView")) {

        if (callOTPOnceCtr == 0) {
          callOTPOnceCtr += 1;
          try {
            String phoneNum = preferences.getString("phoneNum", "");
            JSONObject otpActionObj = new JSONObject();
            otpActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
            otpActionObj.put("PhoneNumber", phoneNum);
            otpActionObj.put("action", "OTP");

            // initial request --> 1
            Call<UploadObject> otpActionCall = uploadTelcoInterface.uploadTelco(otpActionObj);
            otpActionCall.enqueue(new Callback<UploadObject>() {
              @Override
              public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {

                if (response.body() != null) {
                  try {
                    JSONObject otpActionResponseObj = new JSONObject(new Gson().toJson(response.body()));
                    if (otpActionResponseObj.get("status").equals("SUCCESS")) {
                      callOTPOnceCtr = callOTPOnceCtr - 1;
                      String phoneOTP = otpActionResponseObj.get("OTP").toString();

                      Bundle phoneOTPBundle = new Bundle();
                      phoneOTPBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phoneOTP);

                      getOTPRootNode.getChild(1).getChild(0).getChild(0).getChild(0).getChild(2).getChild(1).getChild(0).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, phoneOTPBundle);

                      DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                      final int midY = (int) (displayMetrics.heightPixels / 1.9);
                      final int midX = displayMetrics.widthPixels / 2;

                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                      Path path = new Path();
                      path.moveTo(midX, midY);
                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                          super.onCompleted(gestureDescription);
                          /** delay 3s to call wrongotpaction **/
//                        try {
//                          Thread.sleep(3000);
//                          if (source.getParent().getClassName().equals("android.widget.EditText")) {
//                            if (wrongOTPSendCounter == 0) { // to prevent multiclicks
//                              wrongOTPSendCounter += 1;
//                              Log.i(TAG, "reached wrongotp, starting 2nd handler...");
//                              OTPHandler2 = new Handler(Looper.getMainLooper());
//                              OTPHandler2.postDelayed(new Runnable() {
//                                @Override
//                                public void run() {
//                                  if (source.getParent().getClassName().equals("android.widget.EditText")) {
//                                    Log.i(TAG, "====== 1st wrong attempt ======");
//                                    wrongOTPSendCounter = 0;
//                                    try {
//                                      JSONObject otpWrongActionObj = new JSONObject();
//                                      otpWrongActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
//                                      otpWrongActionObj.put("PhoneNumber", phoneNum);
//                                      otpWrongActionObj.put("action", "WrongOTP");
//
//                                      Call<UploadObject> otpWrongActionCall = uploadTelcoInterface.uploadTelco(otpWrongActionObj); //--> wrong otp 1
//                                      otpWrongActionCall.enqueue(new Callback<UploadObject>() {
//                                        @Override
//                                        public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
//                                          try {
//                                            // call action: otp again to retrieve otp value.
//                                            if (response.code() == 401) {
//                                              Gson gson = new GsonBuilder().create();
//                                              Error401POJO mError;
//                                              try {
//                                                assert response.errorBody() != null;
//                                                mError= gson.fromJson(response.errorBody().string(),Error401POJO.class);
//                                                Log.i(TAG, "wrong otp action === 1st wrong attempt, Status: " + mError.getStatus());
//                                                Log.i(TAG, "wrong otp action === 1st wrong attempt, Message:" + mError.getMessage());
//
//                                                if (mError.getMessage().equals("Status updated. Please request OTP again.")) {
//                                                  Thread.sleep(5000); // request otp again after 10s?
//                                                  otpActionCall.clone().enqueue(new Callback<UploadObject>() { // --> request otp 1
//                                                    @Override
//                                                    public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
//                                                      if (response.body() != null) {
//                                                        try {
//                                                          JSONObject otpAction2ResponseObj = new JSONObject(new Gson().toJson(response.body()));
//                                                          if (otpAction2ResponseObj.get("status").equals("SUCCESS")) {
//                                                            otp = otpAction2ResponseObj.get("OTP").toString();
//
//                                                            String phoneOTP = otp;
//                                                            Bundle phoneOTPBundle = new Bundle();
//                                                            phoneOTPBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phoneOTP);
//
//                                                            source.getParent().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, phoneOTPBundle);
//
//                                                            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
//                                                            final int midY = (int) (displayMetrics.heightPixels / 1.9);
//                                                            final int midX = displayMetrics.widthPixels / 2;
//
//                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
//                                                            Path path = new Path();
//                                                            path.moveTo(midX, midY);
//                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
//                                                            wrongOTPSendCounter = 0;
//
//                                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
//                                                                @Override
//                                                                public void onCompleted(GestureDescription gestureDescription) {
//                                                                  super.onCompleted(gestureDescription);
//                                                                  try {
//                                                                    Thread.sleep(2000);
//                                                                    if (source.getParent().getClassName().equals("android.widget.EditText")) {
//                                                                      if (wrongOTPSendCounter == 0) {
//                                                                        wrongOTPSendCounter += 1;
//                                                                        Log.i(TAG, "====== 2nd wrong attempt ======");
//                                                                        /** disabled temporarily **/
////                                                                        try {
////                                                                          otpWrongActionCall.clone().enqueue(new Callback<UploadObject>() {
////                                                                            @Override
////                                                                            public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
////                                                                              try {
////                                                                                otpActionCall.clone().enqueue(new Callback<UploadObject>() {
////                                                                                  @Override
////                                                                                  public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
////                                                                                    if (response.code() == 401) {
////                                                                                      Gson gson = new GsonBuilder().create();
////                                                                                      Error401POJO mError;
////                                                                                      try {
////                                                                                        assert response.errorBody() != null;
////                                                                                        mError = gson.fromJson(response.errorBody().string(), Error401POJO.class);
////                                                                                        Log.i(TAG, "wrong otp action === 2nd wrng attempt, Status: " + mError.getStatus());
////                                                                                        Log.i(TAG, "wrong otp action === 2nd wrng attempt, Message:" + mError.getMessage());
////
////                                                                                        Log.i(TAG, "=== manually continue to prevent account locked ===");
////                                                                                      } catch (Exception e) {
////                                                                                        e.printStackTrace();
////                                                                                      }
////                                                                                    }
////                                                                                  }
////
////                                                                                  @Override
////                                                                                  public void onFailure(Call<UploadObject> call, Throwable t) {
////
////                                                                                  }
////                                                                                });
////                                                                              } catch (Exception e) {}
////                                                                            }
////                                                                            @Override
////                                                                            public void onFailure(Call<UploadObject> call, Throwable t) {}
////                                                                          });
////                                                                        } catch (Exception e) {}
//                                                                      }
//
//                                                                    }
//                                                                  } catch (Exception e) {};
//                                                                }
//                                                              }, null);
//
//                                                          }
//                                                        } catch (Exception e) {}
//                                                      }
//                                                      else {
//                                                        try {
//                                                          Gson gson = new GsonBuilder().create();
//                                                          Error401POJO mError;
//
//                                                          assert response.errorBody() != null;
//                                                          mError= gson.fromJson(response.errorBody().string(),Error401POJO.class);
//                                                          Log.i(TAG, "wrong otp action: Status: " + mError.getStatus());
//                                                          Log.i(TAG, "wrong otp action:  Message:" + mError.getMessage());
//
//                                                          if (mError.getMessage().equals("Previous OTP wrong. Waiting CS to change new otp")) {
//                                                            requestOTPAgainHandler.postDelayed(new Runnable() {
//                                                              @Override
//                                                              public void run() {
//                                                                otpActionCall.clone().enqueue(new Callback<UploadObject>() {
//                                                                  @Override
//                                                                  public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
//                                                                    if (response.body() == null) {
//                                                                      Log.i(TAG, "waiting CS to change new otp...");
//                                                                    } else {
//                                                                      try {
//                                                                        JSONObject otpAction2ResponseObj = new JSONObject(new Gson().toJson(response.body()));
//                                                                        if (otpAction2ResponseObj.get("status").equals("SUCCESS")) {
//                                                                          otp = otpAction2ResponseObj.get("OTP").toString();
//
//                                                                          String phoneOTP = otp;
//                                                                          Bundle phoneOTPBundle = new Bundle();
//                                                                          phoneOTPBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phoneOTP);
//
//                                                                          source.getParent().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, phoneOTPBundle);
//
//                                                                          DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
//                                                                          final int midY = (int) (displayMetrics.heightPixels / 1.9);
//                                                                          final int midX = displayMetrics.widthPixels / 2;
//
//                                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
//                                                                          Path path = new Path();
//                                                                          path.moveTo(midX, midY);
//                                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
//                                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
//                                                                            @Override
//                                                                            public void onCompleted(GestureDescription gestureDescription) {
//                                                                              super.onCompleted(gestureDescription);
////                                                                              try {
////                                                                                Thread.sleep(2000);
////                                                                                if (source.getParent().getClassName().equals("android.widget.EditText")) {
////                                                                                  if (wrongOTPSendCounter == 0) {
////                                                                                    wrongOTPSendCounter += 1;
////                                                                                    Log.i(TAG, "====== 2nd wrong attempt ======");
////
////                                                                                    try {
////                                                                                      otpWrongActionCall.clone().enqueue(new Callback<UploadObject>() {
////                                                                                        @Override
////                                                                                        public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
////                                                                                          try {
////                                                                                            otpActionCall.clone().enqueue(new Callback<UploadObject>() {
////                                                                                              @Override
////                                                                                              public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
////                                                                                                if (response.code() == 401) {
////                                                                                                  Gson gson = new GsonBuilder().create();
////                                                                                                  Error401POJO mError;
////                                                                                                  try {
////                                                                                                    assert response.errorBody() != null;
////                                                                                                    mError = gson.fromJson(response.errorBody().string(), Error401POJO.class);
////                                                                                                    Log.i(TAG, "wrong otp action === 2nd wrng attempt, Status: " + mError.getStatus());
////                                                                                                    Log.i(TAG, "wrong otp action === 2nd wrng attempt, Message:" + mError.getMessage());
////
////                                                                                                    Log.i(TAG, "=== manually continue to prevent account locked ===");
////                                                                                                  } catch (Exception e) {
////                                                                                                    e.printStackTrace();
////                                                                                                  }
////                                                                                                }
////                                                                                              }
////
////                                                                                              @Override
////                                                                                              public void onFailure(Call<UploadObject> call, Throwable t) {
////
////                                                                                              }
////                                                                                            });
////                                                                                          } catch (Exception e) {}
////                                                                                        }
////                                                                                        @Override
////                                                                                        public void onFailure(Call<UploadObject> call, Throwable t) {}
////                                                                                      });
////                                                                                    } catch (Exception e) {}
////                                                                                  }
////
////                                                                                }
////                                                                              } catch (Exception e) {};
//                                                                            }
//                                                                          }, null);
//
//                                                                          // request again if wrong
//                                                                        }
//                                                                      } catch (Exception e) {
//                                                                        e.printStackTrace();
//                                                                      }
//
//                                                                    }
//                                                                  }
//
//                                                                  @Override
//                                                                  public void onFailure(Call<UploadObject> call, Throwable t) {
//
//                                                                  }
//                                                                });
//                                                                requestOTPAgainHandler.postDelayed(this, 5000);
//                                                              }
//                                                            }, 5000);
//                                                          }
//                                                        } catch (Exception e) {
//
//                                                        }
//                                                      }
//
//                                                    }
//
//                                                    @Override
//                                                    public void onFailure(Call<UploadObject> call, Throwable t) {
//
//                                                    }
//                                                  });
//
//                                                }
//                                              } catch (Exception e) {
//                                                e.printStackTrace();
//                                              }
//                                            }
//                                          } catch (Exception e) {
//                                            e.printStackTrace();
//                                          }
//                                        }
//
//                                        @Override
//                                        public void onFailure(Call<UploadObject> call, Throwable t) {}
//                                      });
//                                    } catch (Exception e) {
//                                      e.printStackTrace();
//                                    }
//
//                                  }
//                                }
//                              }, 2000);
//                              }
//                          }
//                        } catch (Exception e) {
//
//                        }
                        }
                      }, null);

                      // if wrong OTP, send WrongOTP action
                      // counter is to ensure click once
                    }
                  } catch (Exception e) {

                  }
                }
                else if (response.body() == null) {
                  OTPHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                      //call function
                      call.clone().enqueue(new Callback<UploadObject>() {
                        @Override
                        public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                          try {
                            if (response.body() == null) {
                              Log.i(TAG, "Waiting for OTP...");
                            }
                            else {
                              JSONObject otpActionResponseObj = new JSONObject(new Gson().toJson(response.body()));
                              if (otpActionResponseObj.get("status").equals("SUCCESS")) {
                                callOTPOnceCtr = callOTPOnceCtr - 1;

                                Thread.sleep(2000);
                                String phoneOTP = otpActionResponseObj.get("OTP").toString();
                                Bundle phoneOTPBundle = new Bundle();
                                phoneOTPBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phoneOTP);

                                getOTPRootNode.getChild(1).getChild(0).getChild(0).getChild(0).getChild(2).getChild(1).getChild(0).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, phoneOTPBundle);

                                DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                                final int midY = (int) (displayMetrics.heightPixels / 1.9);
                                final int midX = displayMetrics.widthPixels / 2;

                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                Path path = new Path();
                                path.moveTo(midX, midY);
                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                  @Override
                                  public void onCompleted(GestureDescription gestureDescription) {
                                    super.onCompleted(gestureDescription);
//                                  try {
//                                    Thread.sleep(3000);
//                                    if (source.getParent().getClassName().equals("android.widget.EditText")) {
//                                      if (wrongOTPSendCounter == 0) { // to prevent multiclicks
//                                        wrongOTPSendCounter += 1;
//                                        Log.i(TAG, "reached wrongotp, starting 2nd handler...");
//                                        OTPHandler2 = new Handler(Looper.getMainLooper());
//                                        OTPHandler2.postDelayed(new Runnable() {
//                                          @Override
//                                          public void run() {
//                                            if (source.getParent().getClassName().equals("android.widget.EditText")) {
//                                              Log.i(TAG, "====== 1st wrong attempt ======");
//                                              wrongOTPSendCounter = 0;
//                                              try {
//                                                JSONObject otpWrongActionObj = new JSONObject();
//                                                otpWrongActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
//                                                otpWrongActionObj.put("PhoneNumber", phoneNum);
//                                                otpWrongActionObj.put("action", "WrongOTP");
//
//                                                Call<UploadObject> otpWrongActionCall = uploadTelcoInterface.uploadTelco(otpWrongActionObj); //--> wrong otp 1
//                                                otpWrongActionCall.enqueue(new Callback<UploadObject>() {
//                                                  @Override
//                                                  public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
//                                                    try {
//                                                      // call action: otp again to retrieve otp value.
//                                                      if (response.code() == 401) {
//                                                        Gson gson = new GsonBuilder().create();
//                                                        Error401POJO mError;
//                                                        try {
//                                                          assert response.errorBody() != null;
//                                                          mError= gson.fromJson(response.errorBody().string(),Error401POJO.class);
//                                                          Log.i(TAG, "wrong otp action === 1st wrong attempt, Status: " + mError.getStatus());
//                                                          Log.i(TAG, "wrong otp action === 1st wrong attempt, Message:" + mError.getMessage());
//
//                                                          if (mError.getMessage().equals("Status updated. Please request OTP again.")) {
//                                                            Thread.sleep(5000); // request otp again after 10s?
//                                                            otpActionCall.clone().enqueue(new Callback<UploadObject>() { // --> request otp 1
//                                                              @Override
//                                                              public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
//                                                                if (response.body() != null) {
//                                                                  try {
//                                                                    JSONObject otpAction2ResponseObj = new JSONObject(new Gson().toJson(response.body()));
//                                                                    if (otpAction2ResponseObj.get("status").equals("SUCCESS")) {
//                                                                      otp = otpAction2ResponseObj.get("OTP").toString();
//
//                                                                      String phoneOTP = otp;
//                                                                      Bundle phoneOTPBundle = new Bundle();
//                                                                      phoneOTPBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phoneOTP);
//
//                                                                      source.getParent().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, phoneOTPBundle);
//
//                                                                      DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
//                                                                      final int midY = (int) (displayMetrics.heightPixels / 1.9);
//                                                                      final int midX = displayMetrics.widthPixels / 2;
//
//                                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
//                                                                      Path path = new Path();
//                                                                      path.moveTo(midX, midY);
//                                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
//                                                                      wrongOTPSendCounter = 0;
//
//                                                                      Log.i(TAG, "====== 2nd wrong attempt ======");
//                                                                      wrongOTPSendCounter = 0;
//                                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
//                                                                        @Override
//                                                                        public void onCompleted(GestureDescription gestureDescription) {
//                                                                          super.onCompleted(gestureDescription);
//
////                                                                        try {
////                                                                          Thread.sleep(2000);
////                                                                          if (source.getParent().getClassName().equals("android.widget.EditText")) {
////                                                                            if (wrongOTPSendCounter == 0) {
////                                                                              wrongOTPSendCounter += 1;
////                                                                              try {
////                                                                                otpWrongActionCall.clone().enqueue(new Callback<UploadObject>() {
////                                                                                  @Override
////                                                                                  public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
////                                                                                    try {
////                                                                                      otpActionCall.clone().enqueue(new Callback<UploadObject>() {
////                                                                                        @Override
////                                                                                        public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
////                                                                                          if (response.code() == 401) {
////                                                                                            Gson gson = new GsonBuilder().create();
////                                                                                            Error401POJO mError;
////                                                                                            try {
////                                                                                              assert response.errorBody() != null;
////                                                                                              mError = gson.fromJson(response.errorBody().string(), Error401POJO.class);
////                                                                                              Log.i(TAG, "wrong otp action === 2nd wrng attempt, Status: " + mError.getStatus());
////                                                                                              Log.i(TAG, "wrong otp action === 2nd wrng attempt, Message:" + mError.getMessage());
////
////                                                                                              Log.i(TAG, "=== manually continue to prevent account locked ===");
////                                                                                            } catch (Exception e) {
////                                                                                              e.printStackTrace();
////                                                                                            }
////                                                                                          }
////                                                                                        }
////
////                                                                                        @Override
////                                                                                        public void onFailure(Call<UploadObject> call, Throwable t) {
////
////                                                                                        }
////                                                                                      });
////                                                                                    } catch (Exception e) {}
////                                                                                  }
////                                                                                  @Override
////                                                                                  public void onFailure(Call<UploadObject> call, Throwable t) {}
////                                                                                });
////                                                                              } catch (Exception e) {}
////                                                                            }
////                                                                          }
////                                                                        } catch (Exception e) {
////
////                                                                        }
//
//                                                                        }
//                                                                      }, null);
//
//                                                                    }
//                                                                  } catch (Exception e) {}
//                                                                }
//                                                                else {
//                                                                  try {
//                                                                    Gson gson = new GsonBuilder().create();
//                                                                    Error401POJO mError;
//
//                                                                    assert response.errorBody() != null;
//                                                                    mError= gson.fromJson(response.errorBody().string(),Error401POJO.class);
//                                                                    Log.i(TAG, "wrong otp action: Status: " + mError.getStatus());
//                                                                    Log.i(TAG, "wrong otp action:  Message:" + mError.getMessage());
//
//                                                                    if (mError.getMessage().equals("Previous OTP wrong. Waiting CS to change new otp")) {
//                                                                      requestOTPAgainHandler.postDelayed(new Runnable() {
//                                                                        @Override
//                                                                        public void run() {
//                                                                          otpActionCall.clone().enqueue(new Callback<UploadObject>() {
//                                                                            @Override
//                                                                            public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
//                                                                              if (response.body() == null) {
//                                                                                Log.i(TAG, "waiting CS to change new otp...");
//                                                                              } else {
//                                                                                try {
//                                                                                  JSONObject otpAction2ResponseObj = new JSONObject(new Gson().toJson(response.body()));
//                                                                                  if (otpAction2ResponseObj.get("status").equals("SUCCESS")) {
//                                                                                    otp = otpAction2ResponseObj.get("OTP").toString();
//
//                                                                                    String phoneOTP = otp;
//                                                                                    Bundle phoneOTPBundle = new Bundle();
//                                                                                    phoneOTPBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phoneOTP);
//
//                                                                                    source.getParent().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, phoneOTPBundle);
//
//                                                                                    DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
//                                                                                    final int midY = (int) (displayMetrics.heightPixels / 1.9);
//                                                                                    final int midX = displayMetrics.widthPixels / 2;
//
//                                                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
//                                                                                    Path path = new Path();
//                                                                                    path.moveTo(midX, midY);
//                                                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
//                                                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
//                                                                                      @Override
//                                                                                      public void onCompleted(GestureDescription gestureDescription) {
//                                                                                        super.onCompleted(gestureDescription);
//
//                                                                                        try {
//                                                                                          Thread.sleep(2000);
//                                                                                          if (source.getParent().getClassName().equals("android.widget.EditText")) {
//                                                                                            if (wrongOTPSendCounter == 0) {
//                                                                                              wrongOTPSendCounter += 1;
//                                                                                              Log.i(TAG, "====== 2nd wrong attempt ======");
//
////                                                                                          try {
////                                                                                            otpWrongActionCall.clone().enqueue(new Callback<UploadObject>() {
////                                                                                              @Override
////                                                                                              public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
////                                                                                                try {
////                                                                                                  otpActionCall.clone().enqueue(new Callback<UploadObject>() {
////                                                                                                    @Override
////                                                                                                    public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
////                                                                                                      if (response.code() == 401) {
////                                                                                                        Gson gson = new GsonBuilder().create();
////                                                                                                        Error401POJO mError;
////                                                                                                        try {
////                                                                                                          assert response.errorBody() != null;
////                                                                                                          mError = gson.fromJson(response.errorBody().string(), Error401POJO.class);
////                                                                                                          Log.i(TAG, "wrong otp action === 2nd wrng attempt, Status: " + mError.getStatus());
////                                                                                                          Log.i(TAG, "wrong otp action === 2nd wrng attempt, Message:" + mError.getMessage());
////
////                                                                                                          Log.i(TAG, "=== manually continue to prevent account locked ===");
////                                                                                                        } catch (Exception e) {
////                                                                                                          e.printStackTrace();
////                                                                                                        }
////                                                                                                      }
////                                                                                                    }
////
////                                                                                                    @Override
////                                                                                                    public void onFailure(Call<UploadObject> call, Throwable t) {
////
////                                                                                                    }
////                                                                                                  });
////                                                                                                } catch (Exception e) {}
////                                                                                              }
////                                                                                              @Override
////                                                                                              public void onFailure(Call<UploadObject> call, Throwable t) {}
////                                                                                            });
////                                                                                          } catch (Exception e) {}
//                                                                                            }
//
//                                                                                          }
//                                                                                        } catch (Exception e) {};
//                                                                                      }
//                                                                                    }, null);
//
//                                                                                    // request again if wrong
//                                                                                  }
//                                                                                } catch (Exception e) {
//                                                                                  e.printStackTrace();
//                                                                                }
//
//                                                                              }
//                                                                            }
//
//                                                                            @Override
//                                                                            public void onFailure(Call<UploadObject> call, Throwable t) {
//
//                                                                            }
//                                                                          });
//                                                                          requestOTPAgainHandler.postDelayed(this, 5000);
//                                                                        }
//                                                                      }, 5000);
//                                                                    }
//                                                                  } catch (Exception e) {
//
//                                                                  }
//                                                                }
//
//                                                              }
//
//                                                              @Override
//                                                              public void onFailure(Call<UploadObject> call, Throwable t) {
//
//                                                              }
//                                                            });
//
//                                                          }
//                                                        } catch (Exception e) {
//                                                          e.printStackTrace();
//                                                        }
//                                                      }
//                                                    } catch (Exception e) {
//                                                      e.printStackTrace();
//                                                    }
//                                                  }
//
//                                                  @Override
//                                                  public void onFailure(Call<UploadObject> call, Throwable t) {}
//                                                });
//                                              } catch (Exception e) {
//                                                e.printStackTrace();
//                                              }
//
//                                            }
//                                          }
//                                        }, 2000);
//                                      }
//                                    }
//                                  } catch (Exception e) {}
                                  }
                                }, null);
                              }
                            }
                          } catch (Exception e) {
                            e.printStackTrace();
                          }
                        }
                        @Override
                        public void onFailure(Call<UploadObject> call, Throwable t) {
                          Log.e(TAG, "onFailure: ", t);
                        }
                      });

                      OTPHandler.postDelayed(this, 5000);
                    }
                  }, 5000);
                }
              }
              @Override
              public void onFailure(Call<UploadObject> call, Throwable t) {

              }
            });
          } catch (Exception e) {e.printStackTrace();}
        }
      }


      // top up btn
      getRootNode = getRootInActiveWindow();
      try {
        if (getRootNode.getChild(0).getChild(1).getChild(1).getClassName().equals("android.widget.TextView")) {
          wrongOTPSendCounter = 0;
          callPhoneNUmOnceCtr = callPhoneNUmOnceCtr - 1;
          OTPHandler.removeCallbacksAndMessages(null);
          OTPHandler2.removeCallbacksAndMessages(null);
          requestOTPAgainHandler.removeCallbacksAndMessages(null);
          // check whether balance called or not
          boolean isCalled = preferences.getBoolean("isCalled", false);
          boolean isFailed = preferences.getBoolean("isFailed", false);

          try {
            String phoneNum = preferences.getString("phoneNum", "");
            /** if first time balance post, perform as below. Upon successful reload, update balance called to true **/

            if (!isCalled)  {
              if (waitingToResolve) {
                Log.i(TAG, "resolving... (first time call)");
              }
              else if (!waitingToResolve) {
                Thread.sleep(2000);
                GestureDescription.Builder builder = new GestureDescription.Builder();
                Path path1 = new Path();
                path1.moveTo(0, 248);
                builder.addStroke(new GestureDescription.StrokeDescription(path1, 100, 100));
                dispatchGesture(builder.build(), null, null);
                Thread.sleep(1000);

                getRootNode = getRootInActiveWindow();
                if (getRootNode != null) {
                  for (int j = 0; j < getRootNode.getChildCount(); j++) {
                    AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(j);
                    balance = reloadResultInfo.getChild(0).getChild(0).getChild(2).getText().toString();

                    editor.putString("oldBalance", reloadResultInfo.getChild(0).getChild(0).getChild(2).getText().toString());
                    editor.commit();
                  }
                }

                JSONObject balanceActionObj = new JSONObject();
                balanceActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                balanceActionObj.put("PhoneNumber", phoneNum);
                balanceActionObj.put("action", "MAIN");
                balanceActionObj.put("Balance", balance);

                Call<UploadObject> balanceActionCall = uploadTelcoInterface.uploadTelco(balanceActionObj);
                balanceActionCall.enqueue(new Callback<UploadObject>() {
                  @Override
                  public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                    if (response.body() != null) {
                      // called balance or not also need call this
                      try {
                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                        Path path = new Path();
                        path.moveTo(200, 980);
                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                          @Override
                          public void onCompleted(GestureDescription gestureDescription) {
                            super.onCompleted(gestureDescription);

                            getRootNode = getRootInActiveWindow();
                            if (getRootNode != null) {
                              for (int j = 0; j < getRootNode.getChildCount(); j++) {
                                AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(j);

                                if (reloadResultInfo.getChild(0).getClassName().equals("android.widget.FrameLayout")) {
                                  try {
                                    try {
                                      // start post action reload
                                      Thread.sleep(1000);
                                      JSONObject reloadPINActionObj = new JSONObject();
                                      reloadPINActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                      reloadPINActionObj.put("PhoneNumber", phoneNum);
                                      reloadPINActionObj.put("action", "ReloadPIN");

                                      Call<UploadObject> reloadPINActionCall = uploadTelcoInterface.uploadTelco(reloadPINActionObj);
                                      reloadPINActionCall.enqueue(new Callback<UploadObject>() {
                                        @Override
                                        public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                          try {
                                            // if action reload PIN returns nothing, then keep calling
                                            if (response.body() == null) {
                                              Log.i(TAG, "Waiting for reload PIN...");

                                              ha.postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                  //call function
                                                  call.clone().enqueue(new Callback<UploadObject>() {
                                                    @Override
                                                    public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                      try {
                                                        if (response.body() == null) {
                                                          Log.i(TAG, "Waiting for reload PIN...");
                                                          Log.i(TAG, "onResponse: " + callOnceCounter);
                                                        }
                                                        else {
                                                          JSONObject reloadPINActionResponseObj = new JSONObject(new Gson().toJson(response.body()));
                                                          if (reloadPINActionResponseObj.get("status").equals("SUCCESS")) {
                                                            ha.removeCallbacksAndMessages(null);

                                                            String reloadPINString = reloadPINActionResponseObj.get("ReloadPIN").toString();
                                                            tranID = reloadPINActionResponseObj.get("TranID").toString();

                                                            saveReloadPIN(phoneNum, "Hotlink", tranID, reloadPINString);

                                                            Bundle reloadPINBundle = new Bundle();
                                                            reloadPINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reloadPINString);
                                                            try {
                                                              getRootNode = getRootInActiveWindow();
                                                              if (getRootNode != null) {
                                                                for (int j = 0; j < getRootNode.getChildCount(); j++) {
                                                                  AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(j);
                                                                  reloadResultInfo.getChild(0).getChild(5).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, reloadPINBundle);
                                                                  Thread.sleep(1000);
                                                                  reloadResultInfo.getChild(0).getChild(7).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                }
                                                              }

                                                              Thread.sleep(1000);
                                                              getRootNode = getRootInActiveWindow();
                                                              if (getRootNode != null) {
                                                                for (int i = 0; i < getRootNode.getChildCount(); i++) {
                                                                  AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(i);

                                                                  if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Voucher is invalid")) {
                                                                    JSONObject reloadFailedObj = new JSONObject();
                                                                    reloadFailedObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                                    reloadFailedObj.put("PhoneNumber", phoneNum);
                                                                    reloadFailedObj.put("action", "Result");
                                                                    reloadFailedObj.put("TranID", tranID);
                                                                    reloadFailedObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());

                                                                    Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                                                    reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                                                      @Override
                                                                      public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                                        if (response.code() == 400) {
                                                                          Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                        } else if (response.code() == 401) {
                                                                          Log.i(TAG, "onResponse: posted and this exist 401 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                        } else if (response.code() == 200) {
                                                                          Log.i(TAG, "onResponse: posted and this exist 200 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                                          telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                                          editor.putBoolean("isFailed", true);
                                                                          editor.commit();

                                                                          reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                                          performGlobalAction(GLOBAL_ACTION_RECENTS);
                                                                          try{Thread.sleep(2000);} catch (Exception e) {};
                                                                          Intent launchIntent = getPackageManager().getLaunchIntentForPackage("my.com.maxis.hotlink.production");
                                                                          if (launchIntent != null) {
                                                                            startActivity(launchIntent);
                                                                          }
                                                                        }
                                                                      }

                                                                      @Override
                                                                      public void onFailure(Call<UploadObject> call, Throwable t) {

                                                                      }
                                                                    });
                                                                  }
                                                                  else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Top Up Successful")) {
                                                                    reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                                    waitingToResolve = true;
                                                                    callOnceCounter = 0;

                                                                    try {Thread.sleep(2000);} catch (Exception e) {}

                                                                    // click myhotlink bottom tab
                                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                    Path path = new Path();
                                                                    path.moveTo(713, 1504);
                                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                      @Override
                                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                                        super.onCompleted(gestureDescription);
                                                                        try {
                                                                          Thread.sleep(1000);
                                                                        } catch (Exception e) {
                                                                        }

                                                                        // click home bottom tab
                                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                        Path path = new Path();
                                                                        path.moveTo(12, 1504);
                                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                          @Override
                                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                                            super.onCompleted(gestureDescription);
                                                                            try {
                                                                              Thread.sleep(1000);
                                                                            } catch (Exception e) {}

                                                                            // to click top up page after navigated again
                                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                            Log.i(TAG, "clicked top up page punya button");
                                                                            Path path = new Path();
                                                                            path.moveTo(0, 248);
                                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                              @Override
                                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                                super.onCompleted(gestureDescription);
                                                                                try {
                                                                                  Thread.sleep(1000);
                                                                                } catch (Exception e) {}

                                                                                getUpdatedBalanceHandler.postDelayed(new Runnable() {
                                                                                  @Override
                                                                                  public void run() {
                                                                                    getRootNode = getRootInActiveWindow();
                                                                                    for (int j = 0; j < getRootNode.getChildCount(); j++) {
                                                                                      AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(j);
                                                                                      String newBalanceAttempt = reloadResultInfo.getChild(0).getChild(0).getChild(2).getText().toString();
                                                                                      double a = Double.parseDouble(preferences.getString("oldBalance", ""));
                                                                                      double b = Double.parseDouble(newBalanceAttempt);

                                                                                      if (preferences.getString("oldBalance", "").equals(newBalanceAttempt)) {
                                                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                        Path path = new Path();
                                                                                        path.moveTo(1, 300);
                                                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                          @Override
                                                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                                                            super.onCompleted(gestureDescription);
                                                                                            try {
                                                                                              Thread.sleep(1000);
                                                                                            } catch (Exception e) {
                                                                                            }

                                                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                            Path path = new Path();
                                                                                            path.moveTo(0, 248);
                                                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                              @Override
                                                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                                                super.onCompleted(gestureDescription);
                                                                                                Log.i(TAG, "onCompleted: still same...");
                                                                                              }
                                                                                            }, null);
                                                                                          }
                                                                                        }, null);
                                                                                      }
                                                                                      else if (!preferences.getString("oldBalance", "").equals(newBalanceAttempt)) {
                                                                                        try {
                                                                                          Date date = new Date(System.currentTimeMillis());
                                                                                          String time = TWELVE_TF.format(date);
                                                                                          String formattedDate = DATE_TF.format(date);

                                                                                          String refNum =df.format(b - a) + convertTo24HoursFormat(time).replace(":", "") + formattedDate.replace("-", "");

                                                                                          JSONObject successActionObj = new JSONObject();
                                                                                          successActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                                                          successActionObj.put("PhoneNumber", phoneNum);
                                                                                          successActionObj.put("action", "Result");
                                                                                          successActionObj.put("TranID", tranID);
                                                                                          successActionObj.put("ReferenceNumber", "refNum");
                                                                                          successActionObj.put("Credit", df.format(b - a));
                                                                                          successActionObj.put("Balance", newBalanceAttempt);

                                                                                          Call<UploadObject> successActionCall= uploadTelcoInterface.uploadTelco(successActionObj);
                                                                                          successActionCall.enqueue(new Callback<UploadObject>() {
                                                                                            @Override
                                                                                            public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                                                              if (response.body() != null) {
                                                                                                getUpdatedBalanceHandler.removeCallbacksAndMessages(null);
                                                                                                telcoDatabaseHandler.updateIsSuccess(true, phoneNum, "Hotlink", tranID);
                                                                                                Log.i(TAG, "onCompleted: not equals. save to sp(iscalled and new value)");
                                                                                                waitingToResolve = false;

                                                                                                editor.putBoolean("isCalled", true);
                                                                                                editor.putBoolean("isFailed", false);
                                                                                                editor.putString("oldBalance", newBalanceAttempt);
                                                                                                editor.commit();
                                                                                              }
                                                                                              else if (response.code() == 401) {
                                                                                                requestOTPAgainHandler.removeCallbacksAndMessages(null);
                                                                                                editor.putBoolean("isCalled", false);
                                                                                                editor.commit();

                                                                                                try {
                                                                                                  try {
                                                                                                    Thread.sleep(2000);
                                                                                                  } catch (Exception e) {}
                                                                                                  ha.removeCallbacksAndMessages(null);
                                                                                                  // tabbar my hotlink
                                                                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                                  Path path = new Path();
                                                                                                  path.moveTo(713, 1504);
                                                                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                                    @Override
                                                                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                                                                      super.onCompleted(gestureDescription);
                                                                                                      try {
                                                                                                        Thread.sleep(2000);
                                                                                                      } catch (Exception e) {}

                                                                                                      // settings gear icon
                                                                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                                      Path path = new Path();
                                                                                                      path.moveTo(816, 45);
                                                                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                                        @Override
                                                                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                                                                          super.onCompleted(gestureDescription);
                                                                                                          try {
                                                                                                            Thread.sleep(2000);
                                                                                                          } catch (Exception e) {}
                                                                                                          // logout button
                                                                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                                          Path path = new Path();
                                                                                                          path.moveTo(10, 657);
                                                                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                                            @Override
                                                                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                                                                              super.onCompleted(gestureDescription);
                                                                                                              try {
                                                                                                                Thread.sleep(2000);
                                                                                                              } catch (Exception e) {}
                                                                                                              // confirm logout button
                                                                                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                                              Path path = new Path();
                                                                                                              path.moveTo(462, 1480);
                                                                                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1));
                                                                                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                                                @Override
                                                                                                                public void onCompleted(GestureDescription gestureDescription) {
                                                                                                                  super.onCompleted(gestureDescription);
                                                                                                                  Log.i(TAG, "Logged out successfully.");
                                                                                                                }
                                                                                                              },null);
                                                                                                            }
                                                                                                          }, null);
                                                                                                        }
                                                                                                      }, null);
                                                                                                    }
                                                                                                  }, null);
                                                                                                } catch (Exception e) {}
                                                                                              }
                                                                                            }

                                                                                            @Override
                                                                                            public void onFailure(Call<UploadObject> call, Throwable t) {

                                                                                            }
                                                                                          });
                                                                                        } catch (Exception e) {}
                                                                                      }
                                                                                    }
                                                                                    getUpdatedBalanceHandler.postDelayed(this, 5000);
                                                                                  }
                                                                                }, 5000);
                                                                              }
                                                                            }, null);
                                                                          }
                                                                        }, null);
                                                                      }
                                                                    }, null);
                                                                  }
                                                                  else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Your access to top up with")) {
                                                                    JSONObject blockedActionObj = new JSONObject();
                                                                    blockedActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                                    blockedActionObj.put("PhoneNumber", phoneNum);
                                                                    blockedActionObj.put("action", "Result");
                                                                    blockedActionObj.put("TranID", tranID);
                                                                    blockedActionObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());
                                                                    blockedActionObj.put("phoneStatus", "BLOCKED");

                                                                    Call<UploadObject> blockedActionCall = uploadTelcoInterface.uploadTelco(blockedActionObj);
                                                                    blockedActionCall.enqueue(new Callback<UploadObject>() {
                                                                      @Override
                                                                      public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                                        if (response.code() == 400) {
                                                                          Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                                        }
                                                                        else if (response.code() == 401) {
                                                                          telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                                          reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                          Path path = new Path();
                                                                          path.moveTo(500, 300);
                                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                            @Override
                                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                                              super.onCompleted(gestureDescription);
                                                                              try {
                                                                                Thread.sleep(1000);
                                                                              } catch (Exception e) {

                                                                              }

                                                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                              Path path = new Path();
                                                                              path.moveTo(500, 300);
                                                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                @Override
                                                                                public void onCompleted(GestureDescription gestureDescription) {
                                                                                  super.onCompleted(gestureDescription);
                                                                                  // to myhotlink page
                                                                                  try {
                                                                                    Thread.sleep(2000);
                                                                                  } catch (Exception e) {}
                                                                                  ha.removeCallbacksAndMessages(null);
                                                                                  // tabbar my hotlink
                                                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                  Path path = new Path();
                                                                                  path.moveTo(713, 1504);
                                                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                    @Override
                                                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                                                      super.onCompleted(gestureDescription);
                                                                                      try {
                                                                                        Thread.sleep(2000);
                                                                                      } catch (Exception e) {}

                                                                                      // settings gear icon
                                                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                      Path path = new Path();
                                                                                      path.moveTo(816, 45);
                                                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                        @Override
                                                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                                                          super.onCompleted(gestureDescription);
                                                                                          try {
                                                                                            Thread.sleep(2000);
                                                                                          } catch (Exception e) {}
                                                                                          // logout button
                                                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                          Path path = new Path();
                                                                                          path.moveTo(10, 657);
                                                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                            @Override
                                                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                                                              super.onCompleted(gestureDescription);
                                                                                              try {
                                                                                                Thread.sleep(2000);
                                                                                              } catch (Exception e) {}
                                                                                              // confirm logout button
                                                                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                              Path path = new Path();
                                                                                              path.moveTo(462, 1480);
                                                                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1));
                                                                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                                @Override
                                                                                                public void onCompleted(GestureDescription gestureDescription) {
                                                                                                  super.onCompleted(gestureDescription);
                                                                                                  Log.i(TAG, "Logged out successfully.");
                                                                                                }
                                                                                              },null);
                                                                                            }
                                                                                          }, null);
                                                                                        }
                                                                                      }, null);
                                                                                    }
                                                                                  }, null);
                                                                                }
                                                                              }, null);
                                                                            }
                                                                          }, null);

                                                                        }
                                                                        else if (response.code() == 200) {
                                                                          Log.i(TAG, "onResponse: posted and this exist 200 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                                          reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                        }
                                                                      }

                                                                      @Override
                                                                      public void onFailure(Call<UploadObject> call, Throwable t) {

                                                                      }
                                                                    });
                                                                  }
                                                                  else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("is used up")) {
                                                                    // might logout
                                                                    try {
                                                                      JSONObject failedActionObj = new JSONObject();
                                                                      failedActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                                      failedActionObj.put("PhoneNumber", phoneNum);
                                                                      failedActionObj.put("action", "Result");
                                                                      failedActionObj.put("TranID", tranID);
                                                                      failedActionObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());

                                                                      Call<UploadObject> failedActionCall = uploadTelcoInterface.uploadTelco(failedActionObj);
                                                                      failedActionCall.enqueue(new Callback<UploadObject>() {
                                                                        @Override
                                                                        public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                                          if (response.code() == 400) {
                                                                            Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                          } else if (response.code() == 401) {
                                                                            Log.i(TAG, "onResponse: posted and this exist 401 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                          } else if (response.code() == 200) {
                                                                            telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                                            reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                            Path path = new Path();
                                                                            path.moveTo(500, 300);
                                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                              @Override
                                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                                super.onCompleted(gestureDescription);
                                                                                try {Thread.sleep(1000);}catch (Exception e) {}

                                                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                Path path = new Path();
                                                                                path.moveTo(500, 300);
                                                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                  @Override
                                                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                                                    super.onCompleted(gestureDescription);
                                                                                    try {Thread.sleep(1000);}catch (Exception e) {}
                                                                                    editor.putBoolean("isFailed", true);
                                                                                    editor.commit();

                                                                                    performGlobalAction(GLOBAL_ACTION_RECENTS);
                                                                                    try{Thread.sleep(2000);} catch (Exception e) {};
                                                                                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage("my.com.maxis.hotlink.production");
                                                                                    if (launchIntent != null) {
                                                                                      startActivity(launchIntent);
                                                                                    }
                                                                                  }
                                                                                }, null);

                                                                              }
                                                                            }, null);

                                                                          }
                                                                        }

                                                                        @Override
                                                                        public void onFailure(Call<UploadObject> call, Throwable t) {

                                                                        }
                                                                      });
                                                                    } catch (Exception e) {}
                                                                  }
                                                                }
                                                              }
                                                            } catch (Exception e) {

                                                            }

                                                          }
                                                        }
                                                      } catch (Exception e) {
                                                        e.printStackTrace();
                                                      }
                                                    }

                                                    @Override
                                                    public void onFailure(Call<UploadObject> call, Throwable t) {
                                                      Log.e(TAG, "onFailure: ", t);
                                                    }
                                                  });

                                                  ha.postDelayed(this, 5000);
                                                }
                                              }, 5000);
                                            }
                                            else {
                                              // success get action reload (get reload pin)
                                              JSONObject reloadPINActionResponseObj = new JSONObject(new Gson().toJson(response.body()));

                                              if (reloadPINActionResponseObj.get("status").equals("SUCCESS")) {
                                                String reloadPINString = reloadPINActionResponseObj.get("ReloadPIN").toString();
                                                tranID = reloadPINActionResponseObj.get("TranID").toString();
                                                saveReloadPIN(phoneNum, "Hotlink", tranID, reloadPINString);
                                                Bundle reloadPINBundle = new Bundle();
                                                reloadPINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reloadPINString);

                                                Thread.sleep(2000);
                                                getRootNode = getRootInActiveWindow();
                                                if (getRootNode != null) {
                                                  for (int j = 0; j < getRootNode.getChildCount(); j++) {
                                                    AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(j);
                                                    reloadResultInfo.getChild(0).getChild(5).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, reloadPINBundle);
                                                    Thread.sleep(1000);
                                                    reloadResultInfo.getChild(0).getChild(7).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                  }
                                                }

                                                // detect reload result..
                                                Thread.sleep(2000);
                                                getRootNode = getRootInActiveWindow();
                                                if (getRootNode != null) {
                                                  for (int i = 0; i < getRootNode.getChildCount(); i++) {
                                                    AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(i);
                                                    // if failed reload
                                                    if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Voucher is invalid")) {
                                                      callOnceCounter = callOnceCounter - 1;

                                                      JSONObject reloadFailedObj = new JSONObject();
                                                      reloadFailedObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                      reloadFailedObj.put("PhoneNumber", phoneNum);
                                                      reloadFailedObj.put("action", "Result");
                                                      reloadFailedObj.put("TranID", tranID);
                                                      reloadFailedObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());

                                                      Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                                      reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                                        @Override
                                                        public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                          if (response.code() == 400) {
                                                            Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                          } else if (response.code() == 401) {
                                                            Log.i(TAG, "onResponse: posted and this exist 401 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                          } else if (response.code() == 200) {
                                                            Log.i(TAG, "onResponse: posted and this exist 200 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                            telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                            editor.putBoolean("isFailed", true);
                                                            editor.commit();

                                                            reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                            performGlobalAction(GLOBAL_ACTION_RECENTS);
                                                            try{Thread.sleep(2000);} catch (Exception e) {};
                                                            Intent launchIntent = getPackageManager().getLaunchIntentForPackage("my.com.maxis.hotlink.production");
                                                            if (launchIntent != null) {
                                                              startActivity(launchIntent);
                                                            }
                                                          }
                                                        }

                                                        @Override
                                                        public void onFailure(Call<UploadObject> call, Throwable t) {

                                                        }
                                                      });
                                                    }
                                                    else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Top Up Successful")) {
                                                      reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                      waitingToResolve = true;
                                                      callOnceCounter = 0;

                                                      try {Thread.sleep(2000);} catch (Exception e) {}

                                                      // click myhotlink bottom tab
                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                      Path path = new Path();
                                                      path.moveTo(713, 1504);
                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                        @Override
                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                          super.onCompleted(gestureDescription);
                                                          try {
                                                            Thread.sleep(1000);
                                                          } catch (Exception e) {
                                                          }

                                                          // click home bottom tab
                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                          Path path = new Path();
                                                          path.moveTo(12, 1504);
                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                            @Override
                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                              super.onCompleted(gestureDescription);
                                                              try {
                                                                Thread.sleep(1000);
                                                              } catch (Exception e) {}

                                                              // to click top up page after navigated again
                                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                              Log.i(TAG, "clicked top up page punya button");
                                                              Path path = new Path();
                                                              path.moveTo(0, 248);
                                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                @Override
                                                                public void onCompleted(GestureDescription gestureDescription) {
                                                                  super.onCompleted(gestureDescription);
                                                                  try {
                                                                    Thread.sleep(1000);
                                                                  } catch (Exception e) {}

                                                                  getUpdatedBalanceHandler.postDelayed(new Runnable() {
                                                                    @Override
                                                                    public void run() {
                                                                      getRootNode = getRootInActiveWindow();
                                                                      for (int j = 0; j < getRootNode.getChildCount(); j++) {
                                                                        AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(j);
                                                                        String newBalanceAttempt = reloadResultInfo.getChild(0).getChild(0).getChild(2).getText().toString();
                                                                        double a = Double.parseDouble(preferences.getString("oldBalance", ""));
                                                                        double b = Double.parseDouble(newBalanceAttempt);

                                                                        if (preferences.getString("oldBalance", "").equals(newBalanceAttempt)) {
                                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                          Path path = new Path();
                                                                          path.moveTo(1, 300);
                                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                            @Override
                                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                                              super.onCompleted(gestureDescription);
                                                                              try {
                                                                                Thread.sleep(1000);
                                                                              } catch (Exception e) {
                                                                              }

                                                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                              Path path = new Path();
                                                                              path.moveTo(0, 248);
                                                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                @Override
                                                                                public void onCompleted(GestureDescription gestureDescription) {
                                                                                  super.onCompleted(gestureDescription);
                                                                                  Log.i(TAG, "onCompleted: still same...");
                                                                                }
                                                                              }, null);
                                                                            }
                                                                          }, null);
                                                                        }
                                                                        else if (!preferences.getString("oldBalance", "").equals(newBalanceAttempt)) {
                                                                          try {
                                                                            Date date = new Date(System.currentTimeMillis());
                                                                            String time = TWELVE_TF.format(date);
                                                                            String formattedDate = DATE_TF.format(date);

                                                                            String refNum =df.format(b - a) + convertTo24HoursFormat(time).replace(":", "") + formattedDate.replace("-", "");

                                                                            JSONObject successActionObj = new JSONObject();
                                                                            successActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                                            successActionObj.put("PhoneNumber", phoneNum);
                                                                            successActionObj.put("action", "Result");
                                                                            successActionObj.put("TranID", tranID);
                                                                            successActionObj.put("ReferenceNumber", "refNum");
                                                                            successActionObj.put("Credit", df.format(b - a));
                                                                            successActionObj.put("Balance", newBalanceAttempt);

                                                                            Call<UploadObject> successActionCall= uploadTelcoInterface.uploadTelco(successActionObj);
                                                                            successActionCall.enqueue(new Callback<UploadObject>() {
                                                                              @Override
                                                                              public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                                                if (response.body() != null) {
                                                                                  getUpdatedBalanceHandler.removeCallbacksAndMessages(null);
                                                                                  telcoDatabaseHandler.updateIsSuccess(true, phoneNum, "Hotlink", tranID);
                                                                                  Log.i(TAG, "onCompleted: not equals. save to sp(iscalled and new value)");
                                                                                  waitingToResolve = false;

                                                                                  editor.putBoolean("isCalled", true);
                                                                                  editor.putBoolean("isFailed", false);
                                                                                  editor.putString("oldBalance", newBalanceAttempt);
                                                                                  editor.commit();
                                                                                }
                                                                                else if (response.code() == 401) {
                                                                                  requestOTPAgainHandler.removeCallbacksAndMessages(null);
                                                                                  editor.putBoolean("isCalled", false);
                                                                                  editor.commit();

                                                                                  try {
                                                                                    try {
                                                                                      Thread.sleep(2000);
                                                                                    } catch (Exception e) {}
                                                                                    ha.removeCallbacksAndMessages(null);
                                                                                    // tabbar my hotlink
                                                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                    Path path = new Path();
                                                                                    path.moveTo(713, 1504);
                                                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                      @Override
                                                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                                                        super.onCompleted(gestureDescription);
                                                                                        try {
                                                                                          Thread.sleep(2000);
                                                                                        } catch (Exception e) {}

                                                                                        // settings gear icon
                                                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                        Path path = new Path();
                                                                                        path.moveTo(816, 45);
                                                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                          @Override
                                                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                                                            super.onCompleted(gestureDescription);
                                                                                            try {
                                                                                              Thread.sleep(2000);
                                                                                            } catch (Exception e) {}
                                                                                            // logout button
                                                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                            Path path = new Path();
                                                                                            path.moveTo(10, 657);
                                                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                              @Override
                                                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                                                super.onCompleted(gestureDescription);
                                                                                                try {
                                                                                                  Thread.sleep(2000);
                                                                                                } catch (Exception e) {}
                                                                                                // confirm logout button
                                                                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                                Path path = new Path();
                                                                                                path.moveTo(462, 1480);
                                                                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1));
                                                                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                                  @Override
                                                                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                                                                    super.onCompleted(gestureDescription);
                                                                                                    Log.i(TAG, "Logged out successfully.");
                                                                                                  }
                                                                                                },null);
                                                                                              }
                                                                                            }, null);
                                                                                          }
                                                                                        }, null);
                                                                                      }
                                                                                    }, null);
                                                                                  } catch (Exception e) {}
                                                                                }
                                                                              }

                                                                              @Override
                                                                              public void onFailure(Call<UploadObject> call, Throwable t) {

                                                                              }
                                                                            });
                                                                          } catch (Exception e) {}
                                                                        }
                                                                      }
                                                                      getUpdatedBalanceHandler.postDelayed(this, 5000);
                                                                    }
                                                                  }, 5000);
                                                                }
                                                              }, null);
                                                            }
                                                          }, null);
                                                        }
                                                      }, null);
                                                    }
                                                    else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Your access to top up with")) {
                                                      callOnceCounter = 0;

                                                      JSONObject blockedActionObj = new JSONObject();
                                                      blockedActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                      blockedActionObj.put("PhoneNumber", phoneNum);
                                                      blockedActionObj.put("action", "Result");
                                                      blockedActionObj.put("TranID", tranID);
                                                      blockedActionObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());
                                                      blockedActionObj.put("phoneStatus", "BLOCKED");

                                                      Call<UploadObject> blockedActionCall = uploadTelcoInterface.uploadTelco(blockedActionObj);
                                                      blockedActionCall.enqueue(new Callback<UploadObject>() {
                                                        @Override
                                                        public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                          if (response.code() == 400) {
                                                            Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                          }
                                                          else if (response.code() == 401) {
                                                            telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                            reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                            Path path = new Path();
                                                            path.moveTo(500, 300);
                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                              @Override
                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                super.onCompleted(gestureDescription);
                                                                try {
                                                                  Thread.sleep(1000);
                                                                } catch (Exception e) {

                                                                }

                                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                Path path = new Path();
                                                                path.moveTo(500, 300);
                                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                  @Override
                                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                                    super.onCompleted(gestureDescription);
                                                                    // to myhotlink page
                                                                    try {
                                                                      Thread.sleep(2000);
                                                                    } catch (Exception e) {}
                                                                    ha.removeCallbacksAndMessages(null);
                                                                    // tabbar my hotlink
                                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                    Path path = new Path();
                                                                    path.moveTo(713, 1504);
                                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                      @Override
                                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                                        super.onCompleted(gestureDescription);
                                                                        try {
                                                                          Thread.sleep(2000);
                                                                        } catch (Exception e) {}

                                                                        // settings gear icon
                                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                        Path path = new Path();
                                                                        path.moveTo(816, 45);
                                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                          @Override
                                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                                            super.onCompleted(gestureDescription);
                                                                            try {
                                                                              Thread.sleep(2000);
                                                                            } catch (Exception e) {}
                                                                            // logout button
                                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                            Path path = new Path();
                                                                            path.moveTo(10, 657);
                                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                              @Override
                                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                                super.onCompleted(gestureDescription);
                                                                                try {
                                                                                  Thread.sleep(2000);
                                                                                } catch (Exception e) {}
                                                                                // confirm logout button
                                                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                Path path = new Path();
                                                                                path.moveTo(462, 1480);
                                                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1));
                                                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                  @Override
                                                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                                                    super.onCompleted(gestureDescription);
                                                                                    Log.i(TAG, "Logged out successfully.");
                                                                                  }
                                                                                },null);
                                                                              }
                                                                            }, null);
                                                                          }
                                                                        }, null);
                                                                      }
                                                                    }, null);
                                                                  }
                                                                }, null);
                                                              }
                                                            }, null);

                                                          }
                                                          else if (response.code() == 200) {
                                                            Log.i(TAG, "onResponse: posted and this exist 200 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                            reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                          }
                                                        }

                                                        @Override
                                                        public void onFailure(Call<UploadObject> call, Throwable t) {

                                                        }
                                                      });
                                                    }
                                                    else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("is used up")) {
                                                      // might logout
                                                      try {
                                                        JSONObject failedActionObj = new JSONObject();
                                                        failedActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                        failedActionObj.put("PhoneNumber", phoneNum);
                                                        failedActionObj.put("action", "Result");
                                                        failedActionObj.put("TranID", tranID);
                                                        failedActionObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());

                                                        Call<UploadObject> failedActionCall = uploadTelcoInterface.uploadTelco(failedActionObj);
                                                        failedActionCall.enqueue(new Callback<UploadObject>() {
                                                          @Override
                                                          public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                            if (response.code() == 400) {
                                                              Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                            } else if (response.code() == 401) {
                                                              Log.i(TAG, "onResponse: posted and this exist 401 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                            } else if (response.code() == 200) {
                                                              telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                              reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                              Path path = new Path();
                                                              path.moveTo(500, 300);
                                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                @Override
                                                                public void onCompleted(GestureDescription gestureDescription) {
                                                                  super.onCompleted(gestureDescription);
                                                                  try {Thread.sleep(1000);}catch (Exception e) {}

                                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                  Path path = new Path();
                                                                  path.moveTo(500, 300);
                                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                    @Override
                                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                                      super.onCompleted(gestureDescription);
                                                                      try {Thread.sleep(1000);}catch (Exception e) {}
                                                                      editor.putBoolean("isFailed", true);
                                                                      editor.commit();

                                                                      performGlobalAction(GLOBAL_ACTION_RECENTS);
                                                                      try{Thread.sleep(2000);} catch (Exception e) {};
                                                                      Intent launchIntent = getPackageManager().getLaunchIntentForPackage("my.com.maxis.hotlink.production");
                                                                      if (launchIntent != null) {
                                                                        startActivity(launchIntent);
                                                                      }
                                                                    }
                                                                  }, null);

                                                                }
                                                              }, null);

                                                            }
                                                          }

                                                          @Override
                                                          public void onFailure(Call<UploadObject> call, Throwable t) {

                                                          }
                                                        });
                                                      } catch (Exception e) {}
                                                    }
                                                  }
                                                }
                                              }
                                            }
                                          } catch (Exception e) {
                                            e.printStackTrace();
                                          }
                                        }

                                        @Override
                                        public void onFailure(Call<UploadObject> call, Throwable t) {
                                          Log.i(TAG, "reloadPINActionCall: ", t);
                                        }
                                      });
                                    } catch (Exception e) {
                                      e.printStackTrace();
                                    }
                                  } catch (Exception e) {
                                    e.printStackTrace();
                                  }
//                        }

                                }
                              }
                            }
                          }
                        }, null);

                      } catch (Exception e) {}
                    }
                    else if (response.code() == 401) {
                      try {
                        try {
                          Thread.sleep(2000);
                        } catch (Exception e) {}
                        ha.removeCallbacksAndMessages(null);
                        // tabbar my hotlink
                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                        Path path = new Path();
                        path.moveTo(713, 1504);
                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                          @Override
                          public void onCompleted(GestureDescription gestureDescription) {
                            super.onCompleted(gestureDescription);
                            try {
                              Thread.sleep(2000);
                            } catch (Exception e) {}

                            // settings gear icon
                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                            Path path = new Path();
                            path.moveTo(816, 45);
                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                              @Override
                              public void onCompleted(GestureDescription gestureDescription) {
                                super.onCompleted(gestureDescription);
                                try {
                                  Thread.sleep(2000);
                                } catch (Exception e) {}
                                // logout button
                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                Path path = new Path();
                                path.moveTo(10, 657);
                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                  @Override
                                  public void onCompleted(GestureDescription gestureDescription) {
                                    super.onCompleted(gestureDescription);
                                    try {
                                      Thread.sleep(2000);
                                    } catch (Exception e) {}
                                    // confirm logout button
                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                    Path path = new Path();
                                    path.moveTo(462, 1480);
                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1));
                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                      @Override
                                      public void onCompleted(GestureDescription gestureDescription) {
                                        super.onCompleted(gestureDescription);
                                        Log.i(TAG, "Logged out successfully.");
                                      }
                                    },null);
                                  }
                                }, null);
                              }
                            }, null);
                          }
                        }, null);
                      } catch (Exception e) {}
                    }
                  }

                  @Override
                  public void onFailure(Call<UploadObject> call, Throwable t) {

                  }
                });
              }
            }
            // already performed first time call. subsequent reload doesn't need post MAIN balance anymore --> If successful reload:
            else if (isCalled && !isFailed) {
              if (waitingToResolve) {
                Log.i(TAG, "resolving... (not first time call && not reload failed)");
              } else if (!waitingToResolve && callOnceCounterForSuccess == 0) {
                callOnceCounterForSuccess += 1;
                // consider change to coordinate click?
                Thread.sleep(2000);
                GestureDescription.Builder builder = new GestureDescription.Builder();
                Path path1 = new Path();
                path1.moveTo(0, 248);
                builder.addStroke(new GestureDescription.StrokeDescription(path1, 100, 100));
                dispatchGesture(builder.build(), null, null);

                Thread.sleep(2000);

                Log.i(TAG, "already performed first time call. so don't perform MAIN call");
                getRootNode = getRootInActiveWindow();
                if (getRootNode != null) {
                  for (int j = 0; j < getRootNode.getChildCount(); j++) {
                    AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(j);
                    balance = reloadResultInfo.getChild(0).getChild(0).getChild(2).getText().toString();
                    Log.i(TAG, "getViewableTransactionData: " + balance);

                    editor.putString("oldBalance", reloadResultInfo.getChild(0).getChild(0).getChild(2).getText().toString());
                    editor.commit();
                  }
                }

                // click inner top up
                try {
                  Thread.sleep(3000);
                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                  Path path = new Path();
                  path.moveTo(133, 962);
                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                      super.onCompleted(gestureDescription);

                      getRootNode = getRootInActiveWindow();
                      if (getRootNode != null) {
                        for (int j = 0; j < getRootNode.getChildCount(); j++) {
                          AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(j);

                          if (reloadResultInfo.getChild(0).getClassName().equals("android.widget.FrameLayout")) {
                            try {
                              try {
                                // start post action reload
                                Thread.sleep(1000);
                                JSONObject reloadPINActionObj = new JSONObject();
                                reloadPINActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                reloadPINActionObj.put("PhoneNumber", phoneNum);
                                reloadPINActionObj.put("action", "ReloadPIN");

                                Call<UploadObject> reloadPINActionCall = uploadTelcoInterface.uploadTelco(reloadPINActionObj);
                                reloadPINActionCall.enqueue(new Callback<UploadObject>() {
                                  @Override
                                  public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                    try {
                                      // if action reload PIN returns nothing, then keep calling
                                      if (response.body() == null) {
                                        Log.i(TAG, "Waiting for reload PIN...");

                                        ha.postDelayed(new Runnable() {
                                          @Override
                                          public void run() {
                                            //call function
                                            call.clone().enqueue(new Callback<UploadObject>() {
                                              @Override
                                              public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                try {
                                                  if (response.body() == null) {
                                                    Log.i(TAG, "Waiting for reload PIN...");
                                                  } else {
                                                    JSONObject reloadPINActionResponseObj = new JSONObject(new Gson().toJson(response.body()));
                                                    if (reloadPINActionResponseObj.get("status").equals("SUCCESS")) {
                                                      ha.removeCallbacksAndMessages(null);

                                                      String reloadPINString = reloadPINActionResponseObj.get("ReloadPIN").toString();
                                                      tranID = reloadPINActionResponseObj.get("TranID").toString();
                                                      saveReloadPIN(phoneNum, "Hotlink", tranID, reloadPINString);

                                                      Bundle reloadPINBundle = new Bundle();
                                                      reloadPINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reloadPINString);
                                                      try {
                                                        getRootNode = getRootInActiveWindow();
                                                        if (getRootNode != null) {
                                                          for (int j = 0; j < getRootNode.getChildCount(); j++) {
                                                            AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(j);
                                                            reloadResultInfo.getChild(0).getChild(5).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, reloadPINBundle);
                                                            Thread.sleep(1000);
                                                            reloadResultInfo.getChild(0).getChild(7).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                          }
                                                        }

                                                        Thread.sleep(1000);
                                                        getRootNode = getRootInActiveWindow();
                                                        if (getRootNode != null) {
                                                          for (int i = 0; i < getRootNode.getChildCount(); i++) {
                                                            AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(i);

                                                            if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Voucher is invalid")) {
                                                              JSONObject reloadFailedObj = new JSONObject();
                                                              reloadFailedObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                              reloadFailedObj.put("PhoneNumber", phoneNum);
                                                              reloadFailedObj.put("action", "Result");
                                                              reloadFailedObj.put("TranID", tranID);
                                                              reloadFailedObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());

                                                              Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                                              reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                                                @Override
                                                                public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                                  if (response.code() == 400) {
                                                                    Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                  } else if (response.code() == 401) {
                                                                    Log.i(TAG, "onResponse: posted and this exist 401 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                  } else if (response.code() == 200) {
                                                                    Log.i(TAG, "onResponse: posted and this exist 200 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                                    telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                                    editor.putBoolean("isFailed", true);
                                                                    editor.commit();

                                                                    reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                    callOnceCounterForSuccess = callOnceCounterForSuccess - 1;

                                                                    performGlobalAction(GLOBAL_ACTION_RECENTS);
                                                                    try{Thread.sleep(2000);} catch (Exception e) {};
                                                                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage("my.com.maxis.hotlink.production");
                                                                    if (launchIntent != null) {
                                                                      startActivity(launchIntent);
                                                                    }
                                                                  }
                                                                }

                                                                @Override
                                                                public void onFailure(Call<UploadObject> call, Throwable t) {

                                                                }
                                                              });
                                                            }
                                                            else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Top Up Successful")) {
                                                              reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                              waitingToResolve = true;
                                                              callOnceCounter = 0;

                                                              try {Thread.sleep(2000);} catch (Exception e) {}

                                                              // click myhotlink bottom tab
                                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                              Path path = new Path();
                                                              path.moveTo(713, 1504);
                                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                @Override
                                                                public void onCompleted(GestureDescription gestureDescription) {
                                                                  super.onCompleted(gestureDescription);
                                                                  try {
                                                                    Thread.sleep(1000);
                                                                  } catch (Exception e) {
                                                                  }

                                                                  // click home bottom tab
                                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                  Path path = new Path();
                                                                  path.moveTo(12, 1504);
                                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                    @Override
                                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                                      super.onCompleted(gestureDescription);
                                                                      try {
                                                                        Thread.sleep(1000);
                                                                      } catch (Exception e) {}

                                                                      // to click top up page after navigated again
                                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                      Log.i(TAG, "clicked top up page punya button");
                                                                      Path path = new Path();
                                                                      path.moveTo(0, 248);
                                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                        @Override
                                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                                          super.onCompleted(gestureDescription);
                                                                          try {
                                                                            Thread.sleep(1000);
                                                                          } catch (Exception e) {}

                                                                          getUpdatedBalanceHandler.postDelayed(new Runnable() {
                                                                            @Override
                                                                            public void run() {
                                                                              getRootNode = getRootInActiveWindow();
                                                                              for (int j = 0; j < getRootNode.getChildCount(); j++) {
                                                                                AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(j);
                                                                                String newBalanceAttempt = reloadResultInfo.getChild(0).getChild(0).getChild(2).getText().toString();
                                                                                double a = Double.parseDouble(preferences.getString("oldBalance", ""));
                                                                                double b = Double.parseDouble(newBalanceAttempt);

                                                                                if (preferences.getString("oldBalance", "").equals(newBalanceAttempt)) {
                                                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                  Path path = new Path();
                                                                                  path.moveTo(1, 300);
                                                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                    @Override
                                                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                                                      super.onCompleted(gestureDescription);
                                                                                      try {
                                                                                        Thread.sleep(1000);
                                                                                      } catch (Exception e) {
                                                                                      }

                                                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                      Path path = new Path();
                                                                                      path.moveTo(0, 248);
                                                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                        @Override
                                                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                                                          super.onCompleted(gestureDescription);
                                                                                          Log.i(TAG, "onCompleted: still same...");
                                                                                        }
                                                                                      }, null);
                                                                                    }
                                                                                  }, null);
                                                                                }
                                                                                else if (!preferences.getString("oldBalance", "").equals(newBalanceAttempt)) {
                                                                                  try {
                                                                                    Date date = new Date(System.currentTimeMillis());
                                                                                    String time = TWELVE_TF.format(date);
                                                                                    String formattedDate = DATE_TF.format(date);

                                                                                    String refNum =df.format(b - a) + convertTo24HoursFormat(time).replace(":", "") + formattedDate.replace("-", "");

                                                                                    JSONObject successActionObj = new JSONObject();
                                                                                    successActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                                                    successActionObj.put("PhoneNumber", phoneNum);
                                                                                    successActionObj.put("action", "Result");
                                                                                    successActionObj.put("TranID", tranID);
                                                                                    successActionObj.put("ReferenceNumber", "refNum");
                                                                                    successActionObj.put("Credit", df.format(b - a));
                                                                                    successActionObj.put("Balance", newBalanceAttempt);

                                                                                    Call<UploadObject> successActionCall= uploadTelcoInterface.uploadTelco(successActionObj);
                                                                                    successActionCall.enqueue(new Callback<UploadObject>() {
                                                                                      @Override
                                                                                      public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                                                        if (response.body() != null) {
                                                                                          getUpdatedBalanceHandler.removeCallbacksAndMessages(null);
                                                                                          telcoDatabaseHandler.updateIsSuccess(true, phoneNum, "Hotlink", tranID);
                                                                                          Log.i(TAG, "onCompleted: not equals. save to sp(iscalled and new value)");
                                                                                          waitingToResolve = false;

                                                                                          editor.putBoolean("isCalled", true);
                                                                                          editor.putBoolean("isFailed", false);
                                                                                          editor.putString("oldBalance", newBalanceAttempt);
                                                                                          editor.commit();
                                                                                        }
                                                                                        else if (response.code() == 401) {
                                                                                          requestOTPAgainHandler.removeCallbacksAndMessages(null);
                                                                                          editor.putBoolean("isCalled", false);
                                                                                          editor.commit();

                                                                                          try {
                                                                                            try {
                                                                                              Thread.sleep(2000);
                                                                                            } catch (Exception e) {}
                                                                                            ha.removeCallbacksAndMessages(null);
                                                                                            // tabbar my hotlink
                                                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                            Path path = new Path();
                                                                                            path.moveTo(713, 1504);
                                                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                              @Override
                                                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                                                super.onCompleted(gestureDescription);
                                                                                                try {
                                                                                                  Thread.sleep(2000);
                                                                                                } catch (Exception e) {}

                                                                                                // settings gear icon
                                                                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                                Path path = new Path();
                                                                                                path.moveTo(816, 45);
                                                                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                                  @Override
                                                                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                                                                    super.onCompleted(gestureDescription);
                                                                                                    try {
                                                                                                      Thread.sleep(2000);
                                                                                                    } catch (Exception e) {}
                                                                                                    // logout button
                                                                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                                    Path path = new Path();
                                                                                                    path.moveTo(10, 657);
                                                                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                                      @Override
                                                                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                                                                        super.onCompleted(gestureDescription);
                                                                                                        try {
                                                                                                          Thread.sleep(2000);
                                                                                                        } catch (Exception e) {}
                                                                                                        // confirm logout button
                                                                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                                        Path path = new Path();
                                                                                                        path.moveTo(462, 1480);
                                                                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1));
                                                                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                                          @Override
                                                                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                                                                            super.onCompleted(gestureDescription);
                                                                                                            Log.i(TAG, "Logged out successfully.");
                                                                                                          }
                                                                                                        },null);
                                                                                                      }
                                                                                                    }, null);
                                                                                                  }
                                                                                                }, null);
                                                                                              }
                                                                                            }, null);
                                                                                          } catch (Exception e) {}
                                                                                        }
                                                                                      }

                                                                                      @Override
                                                                                      public void onFailure(Call<UploadObject> call, Throwable t) {

                                                                                      }
                                                                                    });
                                                                                  } catch (Exception e) {}
                                                                                }
                                                                              }
                                                                              getUpdatedBalanceHandler.postDelayed(this, 5000);
                                                                            }
                                                                          }, 5000);
                                                                        }
                                                                      }, null);
                                                                    }
                                                                  }, null);
                                                                }
                                                              }, null);
                                                            }
                                                            else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Your access to top up with")) {
                                                              JSONObject blockedActionObj = new JSONObject();
                                                              blockedActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                              blockedActionObj.put("PhoneNumber", phoneNum);
                                                              blockedActionObj.put("action", "Result");
                                                              blockedActionObj.put("TranID", tranID);
                                                              blockedActionObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());
                                                              blockedActionObj.put("phoneStatus", "BLOCKED");

                                                              Call<UploadObject> blockedActionCall = uploadTelcoInterface.uploadTelco(blockedActionObj);
                                                              blockedActionCall.enqueue(new Callback<UploadObject>() {
                                                                @Override
                                                                public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                                  if (response.code() == 400) {
                                                                    Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                                  }
                                                                  else if (response.code() == 401) {
                                                                    telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                                    reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                    callOnceCounterForSuccess = callOnceCounterForSuccess - 1;

                                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                    Path path = new Path();
                                                                    path.moveTo(500, 300);
                                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                      @Override
                                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                                        super.onCompleted(gestureDescription);
                                                                        try {
                                                                          Thread.sleep(1000);
                                                                        } catch (Exception e) {

                                                                        }

                                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                        Path path = new Path();
                                                                        path.moveTo(500, 300);
                                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                          @Override
                                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                                            super.onCompleted(gestureDescription);
                                                                            // to myhotlink page
                                                                            try {
                                                                              Thread.sleep(2000);
                                                                            } catch (Exception e) {}
                                                                            ha.removeCallbacksAndMessages(null);
                                                                            // tabbar my hotlink
                                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                            Path path = new Path();
                                                                            path.moveTo(713, 1504);
                                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                              @Override
                                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                                super.onCompleted(gestureDescription);
                                                                                try {
                                                                                  Thread.sleep(2000);
                                                                                } catch (Exception e) {}

                                                                                // settings gear icon
                                                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                Path path = new Path();
                                                                                path.moveTo(816, 45);
                                                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                  @Override
                                                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                                                    super.onCompleted(gestureDescription);
                                                                                    try {
                                                                                      Thread.sleep(2000);
                                                                                    } catch (Exception e) {}
                                                                                    // logout button
                                                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                    Path path = new Path();
                                                                                    path.moveTo(10, 657);
                                                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                      @Override
                                                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                                                        super.onCompleted(gestureDescription);
                                                                                        try {
                                                                                          Thread.sleep(2000);
                                                                                        } catch (Exception e) {}
                                                                                        // confirm logout button
                                                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                        Path path = new Path();
                                                                                        path.moveTo(462, 1480);
                                                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1));
                                                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                          @Override
                                                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                                                            super.onCompleted(gestureDescription);
                                                                                            Log.i(TAG, "Logged out successfully.");
                                                                                            editor.putBoolean("isCalled", false);
                                                                                            editor.commit();
                                                                                          }
                                                                                        },null);
                                                                                      }
                                                                                    }, null);
                                                                                  }
                                                                                }, null);
                                                                              }
                                                                            }, null);
                                                                          }
                                                                        }, null);
                                                                      }
                                                                    }, null);

                                                                  }
                                                                  else if (response.code() == 200) {
                                                                    Log.i(TAG, "onResponse: posted and this exist 200 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                                    reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                  }
                                                                }

                                                                @Override
                                                                public void onFailure(Call<UploadObject> call, Throwable t) {

                                                                }
                                                              });
                                                            }
                                                            else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("is used up")) {
                                                              // might logout
                                                              try {
                                                                JSONObject failedActionObj = new JSONObject();
                                                                failedActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                                failedActionObj.put("PhoneNumber", phoneNum);
                                                                failedActionObj.put("action", "Result");
                                                                failedActionObj.put("TranID", tranID);
                                                                failedActionObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());

                                                                Call<UploadObject> failedActionCall = uploadTelcoInterface.uploadTelco(failedActionObj);
                                                                failedActionCall.enqueue(new Callback<UploadObject>() {
                                                                  @Override
                                                                  public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                                    if (response.code() == 400) {
                                                                      Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                    } else if (response.code() == 401) {
                                                                      Log.i(TAG, "onResponse: posted and this exist 401 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                    } else if (response.code() == 200) {
                                                                      telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                                      reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                      callOnceCounterForSuccess = callOnceCounterForSuccess - 1;

                                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                      Path path = new Path();
                                                                      path.moveTo(500, 300);
                                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                        @Override
                                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                                          super.onCompleted(gestureDescription);
                                                                          try {Thread.sleep(1000);}catch (Exception e) {}

                                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                          Path path = new Path();
                                                                          path.moveTo(500, 300);
                                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                            @Override
                                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                                              super.onCompleted(gestureDescription);
                                                                              try {Thread.sleep(1000);}catch (Exception e) {}
                                                                              editor.putBoolean("isFailed", true);
                                                                              editor.commit();

                                                                              performGlobalAction(GLOBAL_ACTION_RECENTS);
                                                                              try{Thread.sleep(2000);} catch (Exception e) {};
                                                                              Intent launchIntent = getPackageManager().getLaunchIntentForPackage("my.com.maxis.hotlink.production");
                                                                              if (launchIntent != null) {
                                                                                startActivity(launchIntent);
                                                                              }
                                                                            }
                                                                          }, null);

                                                                        }
                                                                      }, null);

                                                                    }
                                                                  }

                                                                  @Override
                                                                  public void onFailure(Call<UploadObject> call, Throwable t) {

                                                                  }
                                                                });
                                                              } catch (Exception e) {}
                                                            }
                                                          }
                                                        }
                                                      } catch (Exception e) {

                                                      }

                                                    }
                                                  }
                                                } catch (Exception e) {
                                                  e.printStackTrace();
                                                }
                                              }

                                              @Override
                                              public void onFailure(Call<UploadObject> call, Throwable t) {
                                                Log.e(TAG, "onFailure: ", t);
                                              }
                                            });

                                            ha.postDelayed(this, 5000);
                                          }
                                        }, 5000);
                                      }
                                      else {
                                        // success get action reload (get reload pin)
                                        JSONObject reloadPINActionResponseObj = new JSONObject(new Gson().toJson(response.body()));

                                        if (reloadPINActionResponseObj.get("status").equals("SUCCESS")) {
                                          String reloadPINString = reloadPINActionResponseObj.get("ReloadPIN").toString();
                                          tranID = reloadPINActionResponseObj.get("TranID").toString();
                                          saveReloadPIN(phoneNum, "Hotlink", tranID, reloadPINString);

                                          Bundle reloadPINBundle = new Bundle();
                                          reloadPINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reloadPINString);

                                          Thread.sleep(2000);
                                          getRootNode = getRootInActiveWindow();
                                          if (getRootNode != null) {
                                            for (int j = 0; j < getRootNode.getChildCount(); j++) {
                                              AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(j);
                                              reloadResultInfo.getChild(0).getChild(5).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, reloadPINBundle);
                                              Thread.sleep(1000);
                                              reloadResultInfo.getChild(0).getChild(7).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                            }
                                          }

                                          // detect reload result..
                                          Thread.sleep(2000);
                                          getRootNode = getRootInActiveWindow();
                                          if (getRootNode != null) {
                                            for (int i = 0; i < getRootNode.getChildCount(); i++) {
                                              AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(i);
                                              // if failed reload
                                              if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Voucher is invalid")) {
                                                JSONObject reloadFailedObj = new JSONObject();
                                                reloadFailedObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                reloadFailedObj.put("PhoneNumber", phoneNum);
                                                reloadFailedObj.put("action", "Result");
                                                reloadFailedObj.put("TranID", tranID);
                                                reloadFailedObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());

                                                Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                                reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                                  @Override
                                                  public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                    if (response.code() == 400) {
                                                      Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                    } else if (response.code() == 401) {
                                                      Log.i(TAG, "onResponse: posted and this exist 401 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                    } else if (response.code() == 200) {
                                                      Log.i(TAG, "onResponse: posted and this exist 200 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                      telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                      editor.putBoolean("isFailed", true);
                                                      editor.commit();

                                                      reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                      callOnceCounterForSuccess = callOnceCounterForSuccess - 1;

                                                      performGlobalAction(GLOBAL_ACTION_RECENTS);
                                                      try{Thread.sleep(2000);} catch (Exception e) {};
                                                      Intent launchIntent = getPackageManager().getLaunchIntentForPackage("my.com.maxis.hotlink.production");
                                                      if (launchIntent != null) {
                                                        startActivity(launchIntent);
                                                      }
                                                    }
                                                  }

                                                  @Override
                                                  public void onFailure(Call<UploadObject> call, Throwable t) {

                                                  }
                                                });
                                              }
                                              else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Top Up Successful")) {
                                                reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                waitingToResolve = true;
                                                callOnceCounter = 0;

                                                try {Thread.sleep(2000);} catch (Exception e) {}

                                                // click myhotlink bottom tab
                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                Path path = new Path();
                                                path.moveTo(713, 1504);
                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                  @Override
                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                    super.onCompleted(gestureDescription);
                                                    try {
                                                      Thread.sleep(1000);
                                                    } catch (Exception e) {
                                                    }

                                                    // click home bottom tab
                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                    Path path = new Path();
                                                    path.moveTo(12, 1504);
                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                      @Override
                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                        super.onCompleted(gestureDescription);
                                                        try {
                                                          Thread.sleep(1000);
                                                        } catch (Exception e) {}

                                                        // to click top up page after navigated again
                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                        Log.i(TAG, "clicked top up page punya button");
                                                        Path path = new Path();
                                                        path.moveTo(0, 248);
                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                          @Override
                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                            super.onCompleted(gestureDescription);
                                                            try {
                                                              Thread.sleep(1000);
                                                            } catch (Exception e) {}

                                                            getUpdatedBalanceHandler.postDelayed(new Runnable() {
                                                              @Override
                                                              public void run() {
                                                                getRootNode = getRootInActiveWindow();
                                                                for (int j = 0; j < getRootNode.getChildCount(); j++) {
                                                                  AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(j);
                                                                  String newBalanceAttempt = reloadResultInfo.getChild(0).getChild(0).getChild(2).getText().toString();
                                                                  double a = Double.parseDouble(preferences.getString("oldBalance", ""));
                                                                  double b = Double.parseDouble(newBalanceAttempt);

                                                                  if (preferences.getString("oldBalance", "").equals(newBalanceAttempt)) {
                                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                    Path path = new Path();
                                                                    path.moveTo(1, 300);
                                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                      @Override
                                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                                        super.onCompleted(gestureDescription);
                                                                        try {
                                                                          Thread.sleep(1000);
                                                                        } catch (Exception e) {
                                                                        }

                                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                        Path path = new Path();
                                                                        path.moveTo(0, 248);
                                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                          @Override
                                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                                            super.onCompleted(gestureDescription);
                                                                            Log.i(TAG, "onCompleted: still same...");
                                                                          }
                                                                        }, null);
                                                                      }
                                                                    }, null);
                                                                  }
                                                                  else if (!preferences.getString("oldBalance", "").equals(newBalanceAttempt)) {
                                                                    try {
                                                                      Date date = new Date(System.currentTimeMillis());
                                                                      String time = TWELVE_TF.format(date);
                                                                      String formattedDate = DATE_TF.format(date);

                                                                      String refNum =df.format(b - a) + convertTo24HoursFormat(time).replace(":", "") + formattedDate.replace("-", "");

                                                                      JSONObject successActionObj = new JSONObject();
                                                                      successActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                                      successActionObj.put("PhoneNumber", phoneNum);
                                                                      successActionObj.put("action", "Result");
                                                                      successActionObj.put("TranID", tranID);
                                                                      successActionObj.put("ReferenceNumber", "refNum");
                                                                      successActionObj.put("Credit", df.format(b - a));
                                                                      successActionObj.put("Balance", newBalanceAttempt);

                                                                      Call<UploadObject> successActionCall= uploadTelcoInterface.uploadTelco(successActionObj);
                                                                      successActionCall.enqueue(new Callback<UploadObject>() {
                                                                        @Override
                                                                        public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                                          if (response.body() != null) {
                                                                            getUpdatedBalanceHandler.removeCallbacksAndMessages(null);
                                                                            telcoDatabaseHandler.updateIsSuccess(true, phoneNum, "Hotlink", tranID);
                                                                            Log.i(TAG, "onCompleted: not equals. save to sp(iscalled and new value)");
                                                                            waitingToResolve = false;

                                                                            editor.putBoolean("isCalled", true);
                                                                            editor.putBoolean("isFailed", false);
                                                                            editor.putString("oldBalance", newBalanceAttempt);
                                                                            editor.commit();
                                                                          }
                                                                          else if (response.code() == 401) {
                                                                            requestOTPAgainHandler.removeCallbacksAndMessages(null);
                                                                            editor.putBoolean("isCalled", false);
                                                                            editor.commit();

                                                                            try {
                                                                              try {
                                                                                Thread.sleep(2000);
                                                                              } catch (Exception e) {}
                                                                              ha.removeCallbacksAndMessages(null);
                                                                              // tabbar my hotlink
                                                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                              Path path = new Path();
                                                                              path.moveTo(713, 1504);
                                                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                @Override
                                                                                public void onCompleted(GestureDescription gestureDescription) {
                                                                                  super.onCompleted(gestureDescription);
                                                                                  try {
                                                                                    Thread.sleep(2000);
                                                                                  } catch (Exception e) {}

                                                                                  // settings gear icon
                                                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                  Path path = new Path();
                                                                                  path.moveTo(816, 45);
                                                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                    @Override
                                                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                                                      super.onCompleted(gestureDescription);
                                                                                      try {
                                                                                        Thread.sleep(2000);
                                                                                      } catch (Exception e) {}
                                                                                      // logout button
                                                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                      Path path = new Path();
                                                                                      path.moveTo(10, 657);
                                                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                        @Override
                                                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                                                          super.onCompleted(gestureDescription);
                                                                                          try {
                                                                                            Thread.sleep(2000);
                                                                                          } catch (Exception e) {}
                                                                                          // confirm logout button
                                                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                          Path path = new Path();
                                                                                          path.moveTo(462, 1480);
                                                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1));
                                                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                            @Override
                                                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                                                              super.onCompleted(gestureDescription);
                                                                                              Log.i(TAG, "Logged out successfully.");
                                                                                            }
                                                                                          },null);
                                                                                        }
                                                                                      }, null);
                                                                                    }
                                                                                  }, null);
                                                                                }
                                                                              }, null);
                                                                            } catch (Exception e) {}
                                                                          }
                                                                        }

                                                                        @Override
                                                                        public void onFailure(Call<UploadObject> call, Throwable t) {

                                                                        }
                                                                      });
                                                                    } catch (Exception e) {}
                                                                  }
                                                                }
                                                                getUpdatedBalanceHandler.postDelayed(this, 5000);
                                                              }
                                                            }, 5000);
                                                          }
                                                        }, null);
                                                      }
                                                    }, null);
                                                  }
                                                }, null);
                                              }
                                              else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Your access to top up with")) {
                                                JSONObject blockedActionObj = new JSONObject();
                                                blockedActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                blockedActionObj.put("PhoneNumber", phoneNum);
                                                blockedActionObj.put("action", "Result");
                                                blockedActionObj.put("TranID", tranID);
                                                blockedActionObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());
                                                blockedActionObj.put("phoneStatus", "BLOCKED");

                                                Call<UploadObject> blockedActionCall = uploadTelcoInterface.uploadTelco(blockedActionObj);
                                                blockedActionCall.enqueue(new Callback<UploadObject>() {
                                                  @Override
                                                  public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                    if (response.code() == 400) {
                                                      Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                    }
                                                    else if (response.code() == 401) {
                                                      telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                      reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                      callOnceCounterForSuccess = callOnceCounterForSuccess - 1;

                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                      Path path = new Path();
                                                      path.moveTo(500, 300);
                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                        @Override
                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                          super.onCompleted(gestureDescription);
                                                          try {
                                                            Thread.sleep(1000);
                                                          } catch (Exception e) {

                                                          }

                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                          Path path = new Path();
                                                          path.moveTo(500, 300);
                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                            @Override
                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                              super.onCompleted(gestureDescription);
                                                              // to myhotlink page
                                                              try {
                                                                Thread.sleep(2000);
                                                              } catch (Exception e) {}
                                                              ha.removeCallbacksAndMessages(null);
                                                              // tabbar my hotlink
                                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                              Path path = new Path();
                                                              path.moveTo(713, 1504);
                                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                @Override
                                                                public void onCompleted(GestureDescription gestureDescription) {
                                                                  super.onCompleted(gestureDescription);
                                                                  try {
                                                                    Thread.sleep(2000);
                                                                  } catch (Exception e) {}

                                                                  // settings gear icon
                                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                  Path path = new Path();
                                                                  path.moveTo(816, 45);
                                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                    @Override
                                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                                      super.onCompleted(gestureDescription);
                                                                      try {
                                                                        Thread.sleep(2000);
                                                                      } catch (Exception e) {}
                                                                      // logout button
                                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                      Path path = new Path();
                                                                      path.moveTo(10, 657);
                                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                        @Override
                                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                                          super.onCompleted(gestureDescription);
                                                                          try {
                                                                            Thread.sleep(2000);
                                                                          } catch (Exception e) {}
                                                                          // confirm logout button
                                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                          Path path = new Path();
                                                                          path.moveTo(462, 1480);
                                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1));
                                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                            @Override
                                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                                              super.onCompleted(gestureDescription);
                                                                              Log.i(TAG, "Logged out successfully.");
                                                                              editor.putBoolean("isCalled", false);
                                                                              editor.commit();
                                                                            }
                                                                          },null);
                                                                        }
                                                                      }, null);
                                                                    }
                                                                  }, null);
                                                                }
                                                              }, null);
                                                            }
                                                          }, null);
                                                        }
                                                      }, null);

                                                    }
                                                    else if (response.code() == 200) {
                                                      Log.i(TAG, "onResponse: posted and this exist 200 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                      reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                    }
                                                  }

                                                  @Override
                                                  public void onFailure(Call<UploadObject> call, Throwable t) {

                                                  }
                                                });
                                              }
                                              else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("is used up")) {
                                                // might logout
                                                try {
                                                  JSONObject failedActionObj = new JSONObject();
                                                  failedActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                  failedActionObj.put("PhoneNumber", phoneNum);
                                                  failedActionObj.put("action", "Result");
                                                  failedActionObj.put("TranID", tranID);
                                                  failedActionObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());

                                                  Call<UploadObject> failedActionCall = uploadTelcoInterface.uploadTelco(failedActionObj);
                                                  failedActionCall.enqueue(new Callback<UploadObject>() {
                                                    @Override
                                                    public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                      if (response.code() == 400) {
                                                        Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                      } else if (response.code() == 401) {
                                                        Log.i(TAG, "onResponse: posted and this exist 401 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                      } else if (response.code() == 200) {
                                                        telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                        callOnceCounterForSuccess = callOnceCounterForSuccess - 1;

                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                        Path path = new Path();
                                                        path.moveTo(500, 300);
                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                          @Override
                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                            super.onCompleted(gestureDescription);
                                                            try {Thread.sleep(1000);}catch (Exception e) {}

                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                            Path path = new Path();
                                                            path.moveTo(500, 300);
                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                              @Override
                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                super.onCompleted(gestureDescription);
                                                                try {Thread.sleep(1000);}catch (Exception e) {}
                                                                editor.putBoolean("isFailed", true);
                                                                editor.commit();

                                                                performGlobalAction(GLOBAL_ACTION_RECENTS);
                                                                try{Thread.sleep(2000);} catch (Exception e) {};
                                                                Intent launchIntent = getPackageManager().getLaunchIntentForPackage("my.com.maxis.hotlink.production");
                                                                if (launchIntent != null) {
                                                                  startActivity(launchIntent);
                                                                }
                                                              }
                                                            }, null);

                                                          }
                                                        }, null);

                                                      }
                                                    }

                                                    @Override
                                                    public void onFailure(Call<UploadObject> call, Throwable t) {

                                                    }
                                                  });
                                                } catch (Exception e) {}
                                              }
                                            }
                                          }
                                        }
                                      }
                                    } catch (Exception e) {
                                      e.printStackTrace();
                                    }
                                  }

                                  @Override
                                  public void onFailure(Call<UploadObject> call, Throwable t) {
                                    Log.i(TAG, "reloadPINActionCall: ", t);
                                  }
                                });
                              } catch (Exception e) {
                                e.printStackTrace();
                              }
                            } catch (Exception e) {
                              e.printStackTrace();
                            }
//                        }

                          }
                        }
                      }
                    }
                  }, null);

                } catch (Exception e) {}
              }
            }
            // already performed first time call. subsequent reload doesn't need post MAIN balance anymore --> If failed reload:
            else if (isCalled && isFailed) {
              if (waitingToResolve) {
                Log.i(TAG, "resolving... (not first time call && reload failed)");
              }
              else if (!waitingToResolve && callOnceCounter == 0) {
                callOnceCounter += 1;
                try {
                  Thread.sleep(2000);
                  GestureDescription.Builder builder = new GestureDescription.Builder();
                  Path path1 = new Path();
                  path1.moveTo(0, 248);
                  builder.addStroke(new GestureDescription.StrokeDescription(path1, 1, 1));
                  dispatchGesture(builder.build(), new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                      super.onCompleted(gestureDescription);

                      try {
                        Thread.sleep(2000);
                      } catch (InterruptedException e) {
                        e.printStackTrace();
                      }

                      Log.i(TAG, "Already called action main, but failed reload");

                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                      Path path2 = new Path();
                      path2.moveTo(200, 980);
                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path2, 100, 100));
                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                          super.onCompleted(gestureDescription);
                          getRootNode = getRootInActiveWindow();
                          if (getRootNode != null) {
                            for (int j = 0; j < getRootNode.getChildCount(); j++) {
                              AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(j);

                              if (reloadResultInfo.getChild(0).getClassName().equals("android.widget.FrameLayout")) {
                                try {
                                  // start post action reload
                                  Thread.sleep(1000);
                                  JSONObject reloadPINActionObj = new JSONObject();
                                  reloadPINActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                  reloadPINActionObj.put("PhoneNumber", phoneNum);
                                  reloadPINActionObj.put("action", "ReloadPIN");

                                  Call<UploadObject> reloadPINActionCall = uploadTelcoInterface.uploadTelco(reloadPINActionObj);
                                  reloadPINActionCall.enqueue(new Callback<UploadObject>() {
                                    @Override
                                    public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                      try {
                                        // if action reload PIN returns nothing, then keep calling
                                        if (response.body() == null) {
                                          Log.i(TAG, "Waiting for reload PIN...");

                                          ha.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                              //call function
                                              call.clone().enqueue(new Callback<UploadObject>() {
                                                @Override
                                                public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                  try {
                                                    if (response.body() == null) {
                                                      Log.i(TAG, "Waiting for reload PIN...");
                                                    } else {
                                                      JSONObject reloadPINActionResponseObj = new JSONObject(new Gson().toJson(response.body()));
                                                      if (reloadPINActionResponseObj.get("status").equals("SUCCESS")) {
                                                        ha.removeCallbacksAndMessages(null);

                                                        String reloadPINString = reloadPINActionResponseObj.get("ReloadPIN").toString();
                                                        tranID = reloadPINActionResponseObj.get("TranID").toString();
                                                        saveReloadPIN(phoneNum, "Hotlink", tranID, reloadPINString);
                                                        Bundle reloadPINBundle = new Bundle();
                                                        reloadPINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reloadPINString);
                                                        try {
                                                          getRootNode = getRootInActiveWindow();
                                                          if (getRootNode != null) {
                                                            for (int j = 0; j < getRootNode.getChildCount(); j++) {
                                                              AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(j);
                                                              reloadResultInfo.getChild(0).getChild(5).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, reloadPINBundle);
                                                              Thread.sleep(1000);
                                                              reloadResultInfo.getChild(0).getChild(7).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                            }
                                                          }

                                                          Thread.sleep(1000);
                                                          getRootNode = getRootInActiveWindow();
                                                          if (getRootNode != null) {
                                                            for (int i = 0; i < getRootNode.getChildCount(); i++) {
                                                              AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(i);

                                                              // if failed reload
                                                              if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Voucher is invalid")) {
                                                                JSONObject reloadFailedObj = new JSONObject();
                                                                reloadFailedObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                                reloadFailedObj.put("PhoneNumber", phoneNum);
                                                                reloadFailedObj.put("action", "Result");
                                                                reloadFailedObj.put("TranID", tranID);
                                                                reloadFailedObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());

                                                                Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                                                reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                                                  @Override
                                                                  public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                                    if (response.code() == 400) {
                                                                      Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                    } else if (response.code() == 401) {
                                                                      Log.i(TAG, "onResponse: posted and this exist 401 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                    } else if (response.code() == 200) {
                                                                      Log.i(TAG, "onResponse: posted and this exist 200 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                                      telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                                      editor.putBoolean("isFailed", true);
                                                                      editor.commit();

                                                                      reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                      callOnceCounter = callOnceCounter - 1;

                                                                      performGlobalAction(GLOBAL_ACTION_RECENTS);
                                                                      try{Thread.sleep(2000);} catch (Exception e) {};
                                                                      Intent launchIntent = getPackageManager().getLaunchIntentForPackage("my.com.maxis.hotlink.production");
                                                                      if (launchIntent != null) {
                                                                        startActivity(launchIntent);
                                                                      }
                                                                    }
                                                                  }

                                                                  @Override
                                                                  public void onFailure(Call<UploadObject> call, Throwable t) {

                                                                  }
                                                                });
                                                              }
                                                              else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Top Up Successful")) {
                                                                reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                                waitingToResolve = true;
                                                                callOnceCounter = 0;

                                                                try {Thread.sleep(2000);} catch (Exception e) {}

                                                                // click myhotlink bottom tab
                                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                Path path = new Path();
                                                                path.moveTo(713, 1504);
                                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                  @Override
                                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                                    super.onCompleted(gestureDescription);
                                                                    try {
                                                                      Thread.sleep(1000);
                                                                    } catch (Exception e) {
                                                                    }

                                                                    // click home bottom tab
                                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                    Path path = new Path();
                                                                    path.moveTo(12, 1504);
                                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                      @Override
                                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                                        super.onCompleted(gestureDescription);
                                                                        try {
                                                                          Thread.sleep(1000);
                                                                        } catch (Exception e) {}

                                                                        // to click top up page after navigated again
                                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                        Log.i(TAG, "clicked top up page punya button");
                                                                        Path path = new Path();
                                                                        path.moveTo(0, 248);
                                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                          @Override
                                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                                            super.onCompleted(gestureDescription);
                                                                            try {
                                                                              Thread.sleep(1000);
                                                                            } catch (Exception e) {}

                                                                            getUpdatedBalanceHandler.postDelayed(new Runnable() {
                                                                              @Override
                                                                              public void run() {
                                                                                getRootNode = getRootInActiveWindow();
                                                                                for (int j = 0; j < getRootNode.getChildCount(); j++) {
                                                                                  AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(j);
                                                                                  String newBalanceAttempt = reloadResultInfo.getChild(0).getChild(0).getChild(2).getText().toString();
                                                                                  double a = Double.parseDouble(preferences.getString("oldBalance", ""));
                                                                                  double b = Double.parseDouble(newBalanceAttempt);

                                                                                  if (preferences.getString("oldBalance", "").equals(newBalanceAttempt)) {
                                                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                    Path path = new Path();
                                                                                    path.moveTo(1, 300);
                                                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                      @Override
                                                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                                                        super.onCompleted(gestureDescription);
                                                                                        try {
                                                                                          Thread.sleep(1000);
                                                                                        } catch (Exception e) {
                                                                                        }

                                                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                        Path path = new Path();
                                                                                        path.moveTo(0, 248);
                                                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                          @Override
                                                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                                                            super.onCompleted(gestureDescription);
                                                                                            Log.i(TAG, "onCompleted: still same...");
                                                                                          }
                                                                                        }, null);
                                                                                      }
                                                                                    }, null);
                                                                                  }
                                                                                  else if (!preferences.getString("oldBalance", "").equals(newBalanceAttempt)) {
                                                                                    try {
                                                                                      Date date = new Date(System.currentTimeMillis());
                                                                                      String time = TWELVE_TF.format(date);
                                                                                      String formattedDate = DATE_TF.format(date);

                                                                                      String refNum =df.format(b - a) + convertTo24HoursFormat(time).replace(":", "") + formattedDate.replace("-", "");

                                                                                      JSONObject successActionObj = new JSONObject();
                                                                                      successActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                                                      successActionObj.put("PhoneNumber", phoneNum);
                                                                                      successActionObj.put("action", "Result");
                                                                                      successActionObj.put("TranID", tranID);
                                                                                      successActionObj.put("ReferenceNumber", "refNum");
                                                                                      successActionObj.put("Credit", df.format(b - a));
                                                                                      successActionObj.put("Balance", newBalanceAttempt);

                                                                                      Call<UploadObject> successActionCall= uploadTelcoInterface.uploadTelco(successActionObj);
                                                                                      successActionCall.enqueue(new Callback<UploadObject>() {
                                                                                        @Override
                                                                                        public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                                                          if (response.body() != null) {
                                                                                            getUpdatedBalanceHandler.removeCallbacksAndMessages(null);
                                                                                            telcoDatabaseHandler.updateIsSuccess(true, phoneNum, "Hotlink", tranID);
                                                                                            Log.i(TAG, "onCompleted: not equals. save to sp(iscalled and new value)");
                                                                                            waitingToResolve = false;

                                                                                            editor.putBoolean("isCalled", true);
                                                                                            editor.putBoolean("isFailed", false);
                                                                                            editor.putString("oldBalance", newBalanceAttempt);
                                                                                            editor.commit();
                                                                                          }
                                                                                          else if (response.code() == 401) {
                                                                                            requestOTPAgainHandler.removeCallbacksAndMessages(null);
                                                                                            editor.putBoolean("isCalled", false);
                                                                                            editor.commit();

                                                                                            try {
                                                                                              try {
                                                                                                Thread.sleep(2000);
                                                                                              } catch (Exception e) {}
                                                                                              ha.removeCallbacksAndMessages(null);
                                                                                              // tabbar my hotlink
                                                                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                              Path path = new Path();
                                                                                              path.moveTo(713, 1504);
                                                                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                                @Override
                                                                                                public void onCompleted(GestureDescription gestureDescription) {
                                                                                                  super.onCompleted(gestureDescription);
                                                                                                  try {
                                                                                                    Thread.sleep(2000);
                                                                                                  } catch (Exception e) {}

                                                                                                  // settings gear icon
                                                                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                                  Path path = new Path();
                                                                                                  path.moveTo(816, 45);
                                                                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                                    @Override
                                                                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                                                                      super.onCompleted(gestureDescription);
                                                                                                      try {
                                                                                                        Thread.sleep(2000);
                                                                                                      } catch (Exception e) {}
                                                                                                      // logout button
                                                                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                                      Path path = new Path();
                                                                                                      path.moveTo(10, 657);
                                                                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                                        @Override
                                                                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                                                                          super.onCompleted(gestureDescription);
                                                                                                          try {
                                                                                                            Thread.sleep(2000);
                                                                                                          } catch (Exception e) {}
                                                                                                          // confirm logout button
                                                                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                                          Path path = new Path();
                                                                                                          path.moveTo(462, 1480);
                                                                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1));
                                                                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                                            @Override
                                                                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                                                                              super.onCompleted(gestureDescription);
                                                                                                              Log.i(TAG, "Logged out successfully.");
                                                                                                            }
                                                                                                          },null);
                                                                                                        }
                                                                                                      }, null);
                                                                                                    }
                                                                                                  }, null);
                                                                                                }
                                                                                              }, null);
                                                                                            } catch (Exception e) {}
                                                                                          }
                                                                                        }

                                                                                        @Override
                                                                                        public void onFailure(Call<UploadObject> call, Throwable t) {

                                                                                        }
                                                                                      });
                                                                                    } catch (Exception e) {}
                                                                                  }
                                                                                }
                                                                                getUpdatedBalanceHandler.postDelayed(this, 5000);
                                                                              }
                                                                            }, 5000);
                                                                          }
                                                                        }, null);
                                                                      }
                                                                    }, null);
                                                                  }
                                                                }, null);
                                                              }
                                                              else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Your access to top up with")) {
                                                                JSONObject blockedActionObj = new JSONObject();
                                                                blockedActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                                blockedActionObj.put("PhoneNumber", phoneNum);
                                                                blockedActionObj.put("action", "Result");
                                                                blockedActionObj.put("TranID", tranID);
                                                                blockedActionObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());
                                                                blockedActionObj.put("phoneStatus", "BLOCKED");

                                                                Call<UploadObject> blockedActionCall = uploadTelcoInterface.uploadTelco(blockedActionObj);
                                                                blockedActionCall.enqueue(new Callback<UploadObject>() {
                                                                  @Override
                                                                  public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                                    if (response.code() == 400) {
                                                                      Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                                    }
                                                                    else if (response.code() == 401) {
                                                                      telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                                      reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                      callOnceCounter = callOnceCounter - 1;
                                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                      Path path = new Path();
                                                                      path.moveTo(500, 300);
                                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                        @Override
                                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                                          super.onCompleted(gestureDescription);
                                                                          try {
                                                                            Thread.sleep(1000);
                                                                          } catch (Exception e) {

                                                                          }

                                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                          Path path = new Path();
                                                                          path.moveTo(500, 300);
                                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                            @Override
                                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                                              super.onCompleted(gestureDescription);
                                                                              // to myhotlink page
                                                                              try {
                                                                                Thread.sleep(2000);
                                                                              } catch (Exception e) {}
                                                                              ha.removeCallbacksAndMessages(null);
                                                                              // tabbar my hotlink
                                                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                              Path path = new Path();
                                                                              path.moveTo(713, 1504);
                                                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                @Override
                                                                                public void onCompleted(GestureDescription gestureDescription) {
                                                                                  super.onCompleted(gestureDescription);
                                                                                  try {
                                                                                    Thread.sleep(2000);
                                                                                  } catch (Exception e) {}

                                                                                  // settings gear icon
                                                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                  Path path = new Path();
                                                                                  path.moveTo(816, 45);
                                                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                    @Override
                                                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                                                      super.onCompleted(gestureDescription);
                                                                                      try {
                                                                                        Thread.sleep(2000);
                                                                                      } catch (Exception e) {}
                                                                                      // logout button
                                                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                      Path path = new Path();
                                                                                      path.moveTo(10, 657);
                                                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                        @Override
                                                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                                                          super.onCompleted(gestureDescription);
                                                                                          try {
                                                                                            Thread.sleep(2000);
                                                                                          } catch (Exception e) {}
                                                                                          // confirm logout button
                                                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                          Path path = new Path();
                                                                                          path.moveTo(462, 1480);
                                                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1));
                                                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                            @Override
                                                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                                                              super.onCompleted(gestureDescription);
                                                                                              editor.putBoolean("isCalled", false);
                                                                                              editor.commit();
                                                                                              Log.i(TAG, "Logged out successfully.");
                                                                                            }
                                                                                          },null);
                                                                                        }
                                                                                      }, null);
                                                                                    }
                                                                                  }, null);
                                                                                }
                                                                              }, null);
                                                                            }
                                                                          }, null);
                                                                        }
                                                                      }, null);

                                                                    }
                                                                    else if (response.code() == 200) {
                                                                      Log.i(TAG, "onResponse: posted and this exist 200 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                                      reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                    }
                                                                  }

                                                                  @Override
                                                                  public void onFailure(Call<UploadObject> call, Throwable t) {

                                                                  }
                                                                });
                                                              }
                                                              else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("is used up")) {
                                                                // might logout
                                                                try {
                                                                  JSONObject failedActionObj = new JSONObject();
                                                                  failedActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                                  failedActionObj.put("PhoneNumber", phoneNum);
                                                                  failedActionObj.put("action", "Result");
                                                                  failedActionObj.put("TranID", tranID);
                                                                  failedActionObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());

                                                                  Call<UploadObject> failedActionCall = uploadTelcoInterface.uploadTelco(failedActionObj);
                                                                  failedActionCall.enqueue(new Callback<UploadObject>() {
                                                                    @Override
                                                                    public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                                      if (response.code() == 400) {
                                                                        Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                      } else if (response.code() == 401) {
                                                                        Log.i(TAG, "onResponse: posted and this exist 401 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                      } else if (response.code() == 200) {
                                                                        telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                                        callOnceCounter = callOnceCounter - 1;
                                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                        Path path = new Path();
                                                                        path.moveTo(500, 300);
                                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                          @Override
                                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                                            super.onCompleted(gestureDescription);
                                                                            try {Thread.sleep(1000);}catch (Exception e) {}

                                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                            Path path = new Path();
                                                                            path.moveTo(500, 300);
                                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                              @Override
                                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                                super.onCompleted(gestureDescription);
                                                                                try {Thread.sleep(1000);}catch (Exception e) {}
                                                                                editor.putBoolean("isFailed", true);
                                                                                editor.commit();

                                                                                performGlobalAction(GLOBAL_ACTION_RECENTS);
                                                                                try{Thread.sleep(2000);} catch (Exception e) {};
                                                                                Intent launchIntent = getPackageManager().getLaunchIntentForPackage("my.com.maxis.hotlink.production");
                                                                                if (launchIntent != null) {
                                                                                  startActivity(launchIntent);
                                                                                }
                                                                              }
                                                                            }, null);

                                                                          }
                                                                        }, null);

                                                                      }
                                                                    }

                                                                    @Override
                                                                    public void onFailure(Call<UploadObject> call, Throwable t) {

                                                                    }
                                                                  });
                                                                } catch (Exception e) {}
                                                              }
                                                            }
                                                          }
                                                        } catch (Exception e) {

                                                        }

                                                      }
                                                    }
                                                  } catch (Exception e) {
                                                    e.printStackTrace();
                                                  }
                                                }

                                                @Override
                                                public void onFailure(Call<UploadObject> call, Throwable t) {
                                                  Log.e(TAG, "onFailure: ", t);
                                                }
                                              });

                                              ha.postDelayed(this, 5000);
                                            }
                                          }, 5000);
                                        }
                                        else {
                                          // success get action reload (get reload pin)
                                          JSONObject reloadPINActionResponseObj = new JSONObject(new Gson().toJson(response.body()));

                                          if (reloadPINActionResponseObj.get("status").equals("SUCCESS")) {
                                            String reloadPINString = reloadPINActionResponseObj.get("ReloadPIN").toString();
                                            tranID = reloadPINActionResponseObj.get("TranID").toString();
                                            saveReloadPIN(phoneNum, "Hotlink", tranID, reloadPINString);
                                            Bundle reloadPINBundle = new Bundle();
                                            reloadPINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reloadPINString);

                                            Thread.sleep(2000);
                                            getRootNode = getRootInActiveWindow();
                                            if (getRootNode != null) {
                                              for (int j = 0; j < getRootNode.getChildCount(); j++) {
                                                AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(j);
                                                reloadResultInfo.getChild(0).getChild(5).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, reloadPINBundle);
                                                Thread.sleep(1000);
                                                reloadResultInfo.getChild(0).getChild(7).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                              }
                                            }

                                            // detect reload result..
                                            Thread.sleep(1000);
                                            getRootNode = getRootInActiveWindow();
                                            if (getRootNode != null) {
                                              for (int i = 0; i < getRootNode.getChildCount(); i++) {
                                                AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(i);
                                                // if failed reload
                                                if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Voucher is invalid")) {
                                                  JSONObject reloadFailedObj = new JSONObject();
                                                  reloadFailedObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                  reloadFailedObj.put("PhoneNumber", phoneNum);
                                                  reloadFailedObj.put("action", "Result");
                                                  reloadFailedObj.put("TranID", tranID);
                                                  reloadFailedObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());

                                                  Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                                  reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                                    @Override
                                                    public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                      if (response.code() == 400) {
                                                        Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                      } else if (response.code() == 401) {
                                                        Log.i(TAG, "onResponse: posted and this exist 401 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                      } else if (response.code() == 200) {
                                                        Log.i(TAG, "onResponse: posted and this exist 200 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                        telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                        editor.putBoolean("isFailed", true);
                                                        editor.commit();

                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                        callOnceCounter = callOnceCounter - 1;

                                                        performGlobalAction(GLOBAL_ACTION_RECENTS);
                                                        try{Thread.sleep(2000);} catch (Exception e) {};
                                                        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("my.com.maxis.hotlink.production");
                                                        if (launchIntent != null) {
                                                          startActivity(launchIntent);
                                                        }
                                                      }
                                                    }

                                                    @Override
                                                    public void onFailure(Call<UploadObject> call, Throwable t) {

                                                    }
                                                  });
                                                }
                                                else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Top Up Successful")) {
                                                  reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                  waitingToResolve = true;
                                                  callOnceCounter = 0;

                                                  try {Thread.sleep(2000);} catch (Exception e) {}

                                                  // click myhotlink bottom tab
                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                  Path path = new Path();
                                                  path.moveTo(713, 1504);
                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                    @Override
                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                      super.onCompleted(gestureDescription);
                                                      try {
                                                        Thread.sleep(1000);
                                                      } catch (Exception e) {
                                                      }

                                                      // click home bottom tab
                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                      Path path = new Path();
                                                      path.moveTo(12, 1504);
                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                        @Override
                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                          super.onCompleted(gestureDescription);
                                                          try {
                                                            Thread.sleep(1000);
                                                          } catch (Exception e) {}

                                                          // to click top up page after navigated again
                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                          Log.i(TAG, "clicked top up page punya button");
                                                          Path path = new Path();
                                                          path.moveTo(0, 248);
                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                            @Override
                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                              super.onCompleted(gestureDescription);
                                                              try {
                                                                Thread.sleep(1000);
                                                              } catch (Exception e) {}

                                                              getUpdatedBalanceHandler.postDelayed(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                  getRootNode = getRootInActiveWindow();
                                                                  for (int j = 0; j < getRootNode.getChildCount(); j++) {
                                                                    AccessibilityNodeInfo reloadResultInfo = getRootNode.getChild(j);
                                                                    String newBalanceAttempt = reloadResultInfo.getChild(0).getChild(0).getChild(2).getText().toString();
                                                                    double a = Double.parseDouble(preferences.getString("oldBalance", ""));
                                                                    double b = Double.parseDouble(newBalanceAttempt);

                                                                    if (preferences.getString("oldBalance", "").equals(newBalanceAttempt)) {
                                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                      Path path = new Path();
                                                                      path.moveTo(1, 300);
                                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                        @Override
                                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                                          super.onCompleted(gestureDescription);
                                                                          try {
                                                                            Thread.sleep(1000);
                                                                          } catch (Exception e) {
                                                                          }

                                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                          Path path = new Path();
                                                                          path.moveTo(0, 248);
                                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                            @Override
                                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                                              super.onCompleted(gestureDescription);
                                                                              Log.i(TAG, "onCompleted: still same...");
                                                                            }
                                                                          }, null);
                                                                        }
                                                                      }, null);
                                                                    }
                                                                    else if (!preferences.getString("oldBalance", "").equals(newBalanceAttempt)) {
                                                                      try {
                                                                        Date date = new Date(System.currentTimeMillis());
                                                                        String time = TWELVE_TF.format(date);
                                                                        String formattedDate = DATE_TF.format(date);

                                                                        String refNum =df.format(b - a) + convertTo24HoursFormat(time).replace(":", "") + formattedDate.replace("-", "");

                                                                        JSONObject successActionObj = new JSONObject();
                                                                        successActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                                        successActionObj.put("PhoneNumber", phoneNum);
                                                                        successActionObj.put("action", "Result");
                                                                        successActionObj.put("TranID", tranID);
                                                                        successActionObj.put("ReferenceNumber", "refNum");
                                                                        successActionObj.put("Credit", df.format(b - a));
                                                                        successActionObj.put("Balance", newBalanceAttempt);

                                                                        Call<UploadObject> successActionCall= uploadTelcoInterface.uploadTelco(successActionObj);
                                                                        successActionCall.enqueue(new Callback<UploadObject>() {
                                                                          @Override
                                                                          public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                                            if (response.body() != null) {
                                                                              getUpdatedBalanceHandler.removeCallbacksAndMessages(null);
                                                                              telcoDatabaseHandler.updateIsSuccess(true, phoneNum, "Hotlink", tranID);
                                                                              Log.i(TAG, "onCompleted: not equals. save to sp(iscalled and new value)");
                                                                              waitingToResolve = false;

                                                                              editor.putBoolean("isCalled", true);
                                                                              editor.putBoolean("isFailed", false);
                                                                              editor.putString("oldBalance", newBalanceAttempt);
                                                                              editor.commit();
                                                                            }
                                                                            else if (response.code() == 401) {
                                                                              requestOTPAgainHandler.removeCallbacksAndMessages(null);
                                                                              editor.putBoolean("isCalled", false);
                                                                              editor.commit();

                                                                              try {
                                                                                try {
                                                                                  Thread.sleep(2000);
                                                                                } catch (Exception e) {}
                                                                                ha.removeCallbacksAndMessages(null);
                                                                                // tabbar my hotlink
                                                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                Path path = new Path();
                                                                                path.moveTo(713, 1504);
                                                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                  @Override
                                                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                                                    super.onCompleted(gestureDescription);
                                                                                    try {
                                                                                      Thread.sleep(2000);
                                                                                    } catch (Exception e) {}

                                                                                    // settings gear icon
                                                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                    Path path = new Path();
                                                                                    path.moveTo(816, 45);
                                                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                      @Override
                                                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                                                        super.onCompleted(gestureDescription);
                                                                                        try {
                                                                                          Thread.sleep(2000);
                                                                                        } catch (Exception e) {}
                                                                                        // logout button
                                                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                        Path path = new Path();
                                                                                        path.moveTo(10, 657);
                                                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                          @Override
                                                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                                                            super.onCompleted(gestureDescription);
                                                                                            try {
                                                                                              Thread.sleep(2000);
                                                                                            } catch (Exception e) {}
                                                                                            // confirm logout button
                                                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                                            Path path = new Path();
                                                                                            path.moveTo(462, 1480);
                                                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1));
                                                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                              @Override
                                                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                                                super.onCompleted(gestureDescription);
                                                                                                Log.i(TAG, "Logged out successfully.");
                                                                                              }
                                                                                            },null);
                                                                                          }
                                                                                        }, null);
                                                                                      }
                                                                                    }, null);
                                                                                  }
                                                                                }, null);
                                                                              } catch (Exception e) {}
                                                                            }
                                                                          }

                                                                          @Override
                                                                          public void onFailure(Call<UploadObject> call, Throwable t) {

                                                                          }
                                                                        });
                                                                      } catch (Exception e) {}
                                                                    }
                                                                  }
                                                                  getUpdatedBalanceHandler.postDelayed(this, 5000);
                                                                }
                                                              }, 5000);
                                                            }
                                                          }, null);
                                                        }
                                                      }, null);
                                                    }
                                                  }, null);
                                                }
                                                else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("Your access to top up with")) {
                                                  JSONObject blockedActionObj = new JSONObject();
                                                  blockedActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                  blockedActionObj.put("PhoneNumber", phoneNum);
                                                  blockedActionObj.put("action", "Result");
                                                  blockedActionObj.put("TranID", tranID);
                                                  blockedActionObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());
                                                  blockedActionObj.put("phoneStatus", "BLOCKED");

                                                  Call<UploadObject> blockedActionCall = uploadTelcoInterface.uploadTelco(blockedActionObj);
                                                  blockedActionCall.enqueue(new Callback<UploadObject>() {
                                                    @Override
                                                    public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                      if (response.code() == 400) {
                                                        Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                      }
                                                      else if (response.code() == 401) {
                                                        telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                        callOnceCounter = callOnceCounter - 1;
                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                        Path path = new Path();
                                                        path.moveTo(500, 300);
                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                          @Override
                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                            super.onCompleted(gestureDescription);
                                                            try {
                                                              Thread.sleep(1000);
                                                            } catch (Exception e) {

                                                            }

                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                            Path path = new Path();
                                                            path.moveTo(500, 300);
                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                              @Override
                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                super.onCompleted(gestureDescription);
                                                                // to myhotlink page
                                                                try {
                                                                  Thread.sleep(2000);
                                                                } catch (Exception e) {}
                                                                ha.removeCallbacksAndMessages(null);
                                                                // tabbar my hotlink
                                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                Path path = new Path();
                                                                path.moveTo(713, 1504);
                                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                  @Override
                                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                                    super.onCompleted(gestureDescription);
                                                                    try {
                                                                      Thread.sleep(2000);
                                                                    } catch (Exception e) {}

                                                                    // settings gear icon
                                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                    Path path = new Path();
                                                                    path.moveTo(816, 45);
                                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                      @Override
                                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                                        super.onCompleted(gestureDescription);
                                                                        try {
                                                                          Thread.sleep(2000);
                                                                        } catch (Exception e) {}
                                                                        // logout button
                                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                        Path path = new Path();
                                                                        path.moveTo(10, 657);
                                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                          @Override
                                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                                            super.onCompleted(gestureDescription);
                                                                            try {
                                                                              Thread.sleep(2000);
                                                                            } catch (Exception e) {}
                                                                            // confirm logout button
                                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                            Path path = new Path();
                                                                            path.moveTo(462, 1480);
                                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1));
                                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                              @Override
                                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                                super.onCompleted(gestureDescription);
                                                                                editor.putBoolean("isCalled", false);
                                                                                editor.commit();
                                                                                Log.i(TAG, "Logged out successfully.");
                                                                              }
                                                                            },null);
                                                                          }
                                                                        }, null);
                                                                      }
                                                                    }, null);
                                                                  }
                                                                }, null);
                                                              }
                                                            }, null);
                                                          }
                                                        }, null);

                                                      }
                                                      else if (response.code() == 200) {
                                                        Log.i(TAG, "onResponse: posted and this exist 200 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                      }
                                                    }

                                                    @Override
                                                    public void onFailure(Call<UploadObject> call, Throwable t) {

                                                    }
                                                  });
                                                }
                                                else if (reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString().contains("is used up")) {
                                                  // might logout
                                                  try {
                                                    JSONObject failedActionObj = new JSONObject();
                                                    failedActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
                                                    failedActionObj.put("PhoneNumber", phoneNum);
                                                    failedActionObj.put("action", "Result");
                                                    failedActionObj.put("TranID", tranID);
                                                    failedActionObj.put("message", reloadResultInfo.getChild(0).getChild(0).getChild(1).getText().toString());

                                                    Call<UploadObject> failedActionCall = uploadTelcoInterface.uploadTelco(failedActionObj);
                                                    failedActionCall.enqueue(new Callback<UploadObject>() {
                                                      @Override
                                                      public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                        if (response.code() == 400) {
                                                          Log.i(TAG, "onResponse: posted and this exist 400 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                        } else if (response.code() == 401) {
                                                          Log.i(TAG, "onResponse: posted and this exist 401 -> " + response.body() + "," + reloadResultInfo.getChild(0).getChild(0).getChild(2));
//                                                        reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                        } else if (response.code() == 200) {
                                                          telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Hotlink", tranID);
                                                          reloadResultInfo.getChild(0).getChild(0).getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                          callOnceCounter = callOnceCounter - 1;
                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                          Path path = new Path();
                                                          path.moveTo(500, 300);
                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                            @Override
                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                              super.onCompleted(gestureDescription);
                                                              try {Thread.sleep(1000);}catch (Exception e) {}

                                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                              Path path = new Path();
                                                              path.moveTo(500, 300);
                                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                @Override
                                                                public void onCompleted(GestureDescription gestureDescription) {
                                                                  super.onCompleted(gestureDescription);
                                                                  try {Thread.sleep(1000);}catch (Exception e) {}
                                                                  editor.putBoolean("isFailed", true);
                                                                  editor.commit();

                                                                  performGlobalAction(GLOBAL_ACTION_RECENTS);
                                                                  try{Thread.sleep(2000);} catch (Exception e) {};
                                                                  Intent launchIntent = getPackageManager().getLaunchIntentForPackage("my.com.maxis.hotlink.production");
                                                                  if (launchIntent != null) {
                                                                    startActivity(launchIntent);
                                                                  }
                                                                }
                                                              }, null);

                                                            }
                                                          }, null);

                                                        }
                                                      }

                                                      @Override
                                                      public void onFailure(Call<UploadObject> call, Throwable t) {

                                                      }
                                                    });
                                                  } catch (Exception e) {}
                                                }
                                              }
                                            }
                                          }
                                        }
                                      } catch (Exception e) {
                                        e.printStackTrace();
                                      }
                                    }

                                    @Override
                                    public void onFailure(Call<UploadObject> call, Throwable t) {
                                      Log.i(TAG, "reloadPINActionCall: ", t);
                                    }
                                  });
                                } catch (Exception e) {e.printStackTrace();}
                              }
                            }
                          }
                        }
                      }, null);
                    }
                  }, null);
                } catch (Exception e) {}
              }
            }
          } catch (Exception e) {}
        }
      } catch (Exception e) {

      }

    } catch (Exception e) {

    }
  }

  public void saveReloadPIN(String phoneNum, String telcoType, String tranID, String reloadPIN) {
    telcoDatabaseModel = new TelcoDatabaseModel();

    telcoDatabaseModel.setPhoneNumber(phoneNum);
    telcoDatabaseModel.setTelcoType(telcoType);
    telcoDatabaseModel.setTranID(tranID);
    telcoDatabaseModel.setReloadPIN(reloadPIN);

    telcoDatabaseHandler.addTelcoEntry(telcoDatabaseModel);
  }


  public String convertTo24HoursFormat(String twelveHourTime)
          throws ParseException {
    return TWENTY_FOUR_TF.format(TWELVE_TF.parse(twelveHourTime));
  }

  @Override
  public void onInterrupt() {

  }
}


/** top up page nodes **/
// getRootNode = getRootInActiveWindow();
//         Log.i(TAG, "1: " + getRootNode);
//         Log.i(TAG, "2: " + getRootNode.getChild(0));
//         Log.i(TAG, "3: " + getRootNode.getChild(0).getChild(0));
//         Log.i(TAG, "4: " + getRootNode.getChild(0).getChild(0).getChild(0).getChild(7));
//         Log.i(TAG, "5: " + getRootNode.getChild(0).getChild(0).getChild(0).getChild(7).getChild(0));
