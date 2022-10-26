package com.example.myapplication;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.myapplication.SQLiteDatabaseHandler.TelcoDatabaseHandler;
import com.example.myapplication.interfaces.UploadTelcoInterface;
import com.example.myapplication.models.Error400POJO;
import com.example.myapplication.models.Error401POJO;
import com.example.myapplication.models.UploadObject;
import com.example.myapplication.models.databaseModels.TelcoDatabaseModel;
import com.example.myapplication.utils.ApiUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.checkerframework.checker.units.qual.C;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DigiService  extends AccessibilityService {
  private static final String TAG = "access";
  public int restartOnceCounter = 0;
  public int clickOnceCounter = 0;
  public int isSuccessCounter = 0;
  public int isFailedCounter = 0;
  public int clickReloadOnceCounter = 0;
  public int postMainOnceCounter = 0;
  public int postFailOnceCounter = 0;
  public int postSuccessOnceCounter = 0;

  String balance = "";
  String tranID = "";
  boolean waitingToResolve;
  boolean isCalled;
  boolean isFailed;

  private DateFormat TWELVE_TF = new SimpleDateFormat("hh:mma");
  private DateFormat TWENTY_FOUR_TF = new SimpleDateFormat("HH:mm");
  private DateFormat DATE_TF = new SimpleDateFormat("yyyy-MM-dd");

  UploadTelcoInterface uploadTelcoInterface;

  TelcoDatabaseHandler telcoDatabaseHandler;
  TelcoDatabaseModel telcoDatabaseModel;

  SharedPreferences preferences;
  SharedPreferences.Editor editor;
  Context context;
  AccessibilityNodeInfo getRootNode;

  private static final DecimalFormat df = new DecimalFormat("0.00");

  Handler phoneNumHandler = new Handler();
  Handler OTPHandler = new Handler();
  Handler reloadPINHandler = new Handler();
  @Override
  protected void onServiceConnected() {
    super.onServiceConnected();
    Log.i(TAG, "onServiceConnected: digi connected!");
    telcoDatabaseHandler = new TelcoDatabaseHandler(context);

    Intent launchIntent1 = getPackageManager().getLaunchIntentForPackage("com.digi.portal.mobdev.android");
    if (launchIntent1 != null) {
      startActivity(launchIntent1);
    }
  }

  // TODO:  OTP attempts, balance limit reach, logout clear counters


  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    try {
      context = MyApplication.getAppContext();
      telcoDatabaseHandler = new TelcoDatabaseHandler(context);

      context = MyApplication.getAppContext();
      preferences = PreferenceManager.getDefaultSharedPreferences(context);
      editor = preferences.edit();

      getViewableTransactionData(event);
    } catch (Exception e) {
      Log.i(TAG, "exception: " + e);
    }
  }

  public void getViewableTransactionData(AccessibilityEvent event) {
    try {
      uploadTelcoInterface = ApiUtils.getUploadTelcoInterface();
      AccessibilityNodeInfo source = event.getSource();
      // IF START EXIST

      /** login **/
      List<AccessibilityNodeInfo> findPhoneNumEt = source.findAccessibilityNodeInfosByText("ENTER YOUR PHONE NUMBER");
      if (findPhoneNumEt.size() != 0) {
        for (AccessibilityNodeInfo findPhoneNumEtNode : findPhoneNumEt) {
          if (findPhoneNumEtNode.getParent().getChild(2).getChild(0).getClassName().equals("android.widget.EditText")) {
            if (clickOnceCounter == 0) {
              try {
                JSONObject postLoginJsonObj = new JSONObject();
                postLoginJsonObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                postLoginJsonObj.put("PhoneNumber", "");

                Call<UploadObject> call = uploadTelcoInterface.uploadTelco(postLoginJsonObj);
                call.enqueue(new Callback<UploadObject>() {
                  @Override
                  public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                    try {
                      if (response.body() == null) {
                        Log.i(TAG, "Waiting for phone num...");

                        phoneNumHandler.postDelayed(new Runnable() {
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
                                      phoneNumHandler.removeCallbacksAndMessages(null);
                                      editor.putString("phoneNum", loginObj.get("PhoneNumber").toString());
                                      editor.commit();

                                      Thread.sleep(2000);
                                      String MobileNoInput = preferences.getString("phoneNum", "");

                                      Bundle phoneNumBundle = new Bundle();
                                      phoneNumBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, MobileNoInput);
                                      findPhoneNumEtNode.getParent().getChild(2).getChild(0).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, phoneNumBundle);

                                      Thread.sleep(2000);
                                      findPhoneNumEtNode.getParent().getChild(3).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                      clickOnceCounter += 1;
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

                            phoneNumHandler.postDelayed(this, 5000);
                          }
                        }, 5000);
                      } else {
                        JSONObject loginObj = new JSONObject(new Gson().toJson(response.body()));

                        if (loginObj.get("status").equals("SUCCESS")) {
                          editor.putString("phoneNum", loginObj.get("PhoneNumber").toString());
                          editor.commit();
                          phoneNumHandler.removeCallbacksAndMessages(null);
                          Thread.sleep(2000);
                          String MobileNoInput = preferences.getString("phoneNum", "");
                          ;

                          Bundle phoneNumBundle = new Bundle();
                          phoneNumBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, MobileNoInput);
                          findPhoneNumEtNode.getParent().getChild(2).getChild(0).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, phoneNumBundle);

                          Thread.sleep(2000);
                          findPhoneNumEtNode.getParent().getChild(3).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                          clickOnceCounter += 1;
                        }
                      }
                    } catch (Exception e) {

                    }
                  }

                  @Override
                  public void onFailure(Call<UploadObject> call, Throwable t) {

                  }
                });

              } catch (Exception e) {
              }
            }
          }
        }
      }

      /** otp **/
      List<AccessibilityNodeInfo> findPIN = source.findAccessibilityNodeInfosByText("Please enter your One Time PIN");
      if (findPIN.size() != 0) {
        phoneNumHandler.removeCallbacksAndMessages(null);
        for (AccessibilityNodeInfo findPINNode: findPIN) {
          String receivedPhoneNumber = preferences.getString("phoneNum", "");

          Thread.sleep(2000);

          try {
            JSONObject otpActionObj = new JSONObject();
            otpActionObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
            otpActionObj.put("PhoneNumber", receivedPhoneNumber);
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
                      String otp = otpActionResponseObj.get("OTP").toString();
                      Bundle otpBundle = new Bundle();
                      otpBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, otp);
                      findPINNode.getParent().getChild(8).getChild(0).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, otpBundle);

                      Thread.sleep(1000);
                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                      Path path = new Path();
                      path.moveTo(100, 1510);
                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                          super.onCompleted(gestureDescription);
                          OTPHandler.removeCallbacksAndMessages(null);

                          if (restartOnceCounter == 0) {
                            restartOnceCounter += 1;
                            performGlobalAction(GLOBAL_ACTION_RECENTS);

                            try{Thread.sleep(2000);} catch (Exception e) {};
                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                            Path path = new Path();
                            path.moveTo(760, 1450);
                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 1, 1));
                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                              @Override
                              public void onCompleted(GestureDescription gestureDescription) {
                                super.onCompleted(gestureDescription);
                                Log.i(TAG, "onCompleted: press ");
                                try {
                                  Thread.sleep(1000);
                                  Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.digi.portal.mobdev.android");
                                  if (launchIntent != null) {
                                    startActivity(launchIntent);
                                  }
                                } catch (Exception e) {

                                }
                              }
                            }, null);
                          }
                        }
                      }, null);
                    }
                  } catch (Exception e) {

                  }
                }
                else {
                  OTPHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                      call.clone().enqueue(new Callback<UploadObject>() {
                        @Override
                        public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                          if (response.body() == null) {
                            Log.i(TAG, "Waiting for OTP...");
                          }
                          else {
                            try {
                              JSONObject otpActionResponseObj = new JSONObject(new Gson().toJson(response.body()));
                              if (otpActionResponseObj.get("status").equals("SUCCESS")) {
                                String otp = otpActionResponseObj.get("OTP").toString();
                                Bundle otpBundle = new Bundle();
                                otpBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, otp);
                                findPINNode.getParent().getChild(8).getChild(0).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, otpBundle);

                                Thread.sleep(1000);
                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                Path path = new Path();
                                path.moveTo(100, 1510);
                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                  @Override
                                  public void onCompleted(GestureDescription gestureDescription) {
                                    super.onCompleted(gestureDescription);
                                    try {
                                      Thread.sleep(1000);

                                      getRootNode = getRootInActiveWindow();
                                      if (getRootNode.getChild(1).getClassName().equals("android.widget.TextView")) {
//                                        try {
//                                          JSONObject otpWrongActionObj = new JSONObject();
//                                          otpWrongActionObj.put("SecretKey", "1324db5adbf7e112c25dc1b6ed66a3806ed810a9");
//                                          otpWrongActionObj.put("PhoneNumber", receivedPhoneNumber);
//                                          otpWrongActionObj.put("action", "WrongOTP");
//
//                                          Call<UploadObject> otpWrongActionCall = uploadTelcoInterface.uploadTelco(otpWrongActionObj); //--> wrong otp 1
//                                          otpWrongActionCall.enqueue(new Callback<UploadObject>() {
//                                            @Override
//                                            public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
//                                              try {
//                                                // call action: otp again to retrieve otp value.
//                                                if (response.code() == 401) {
//                                                  Gson gson = new GsonBuilder().create();
//                                                  Error401POJO mError;
//                                                  try {
//                                                    assert response.errorBody() != null;
//                                                    mError= gson.fromJson(response.errorBody().string(),Error401POJO.class);
//                                                    Log.i(TAG, "wrong otp action === 1st wrong attempt, Status: " + mError.getStatus());
//                                                    Log.i(TAG, "wrong otp action === 1st wrong attempt, Message:" + mError.getMessage());
//
//                                                    if (mError.getMessage().equals("Status updated. Please request OTP again.")) {
//                                                      Thread.sleep(5000); // request otp again after 10s?
//                                                      otpActionCall.clone().enqueue(new Callback<UploadObject>() { // --> request otp 1
//                                                        @Override
//                                                        public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
//                                                          if (response.body() != null) {
//                                                            try {
//                                                              JSONObject otpAction2ResponseObj = new JSONObject(new Gson().toJson(response.body()));
//                                                              if (otpAction2ResponseObj.get("status").equals("SUCCESS")) {
//                                                                otp = otpAction2ResponseObj.get("OTP").toString();
//
//                                                                String phoneOTP = otp;
//                                                                Bundle phoneOTPBundle = new Bundle();
//                                                                phoneOTPBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phoneOTP);
//
//                                                                source.getParent().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, phoneOTPBundle);
//
//                                                                DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
//                                                                final int midY = (int) (displayMetrics.heightPixels / 1.9);
//                                                                final int midX = displayMetrics.widthPixels / 2;
//
//                                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
//                                                                Path path = new Path();
//                                                                path.moveTo(midX, midY);
//                                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
//                                                                wrongOTPSendCounter = 0;
//
//                                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
//                                                                  @Override
//                                                                  public void onCompleted(GestureDescription gestureDescription) {
//                                                                    super.onCompleted(gestureDescription);
//                                                                    try {
//                                                                      Thread.sleep(2000);
//                                                                      if (source.getParent().getClassName().equals("android.widget.EditText")) {
//                                                                        if (wrongOTPSendCounter == 0) {
//                                                                          wrongOTPSendCounter += 1;
//                                                                          Log.i(TAG, "====== 2nd wrong attempt ======");
//                                                                          /** disabled temporarily **/
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
//                                                                        }
//
//                                                                      }
//                                                                    } catch (Exception e) {};
//                                                                  }
//                                                                }, null);
//
//                                                              }
//                                                            } catch (Exception e) {}
//                                                          }
//                                                          else {
//                                                            try {
//                                                              Gson gson = new GsonBuilder().create();
//                                                              Error401POJO mError;
//
//                                                              assert response.errorBody() != null;
//                                                              mError= gson.fromJson(response.errorBody().string(),Error401POJO.class);
//                                                              Log.i(TAG, "wrong otp action: Status: " + mError.getStatus());
//                                                              Log.i(TAG, "wrong otp action:  Message:" + mError.getMessage());
//
//                                                              if (mError.getMessage().equals("Previous OTP wrong. Waiting CS to change new otp")) {
//                                                                requestOTPAgainHandler.postDelayed(new Runnable() {
//                                                                  @Override
//                                                                  public void run() {
//                                                                    otpActionCall.clone().enqueue(new Callback<UploadObject>() {
//                                                                      @Override
//                                                                      public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
//                                                                        if (response.body() == null) {
//                                                                          Log.i(TAG, "waiting CS to change new otp...");
//                                                                        } else {
//                                                                          try {
//                                                                            JSONObject otpAction2ResponseObj = new JSONObject(new Gson().toJson(response.body()));
//                                                                            if (otpAction2ResponseObj.get("status").equals("SUCCESS")) {
//                                                                              otp = otpAction2ResponseObj.get("OTP").toString();
//
//                                                                              String phoneOTP = otp;
//                                                                              Bundle phoneOTPBundle = new Bundle();
//                                                                              phoneOTPBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phoneOTP);
//
//                                                                              source.getParent().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, phoneOTPBundle);
//
//                                                                              DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
//                                                                              final int midY = (int) (displayMetrics.heightPixels / 1.9);
//                                                                              final int midX = displayMetrics.widthPixels / 2;
//
//                                                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
//                                                                              Path path = new Path();
//                                                                              path.moveTo(midX, midY);
//                                                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
//                                                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
//                                                                                @Override
//                                                                                public void onCompleted(GestureDescription gestureDescription) {
//                                                                                  super.onCompleted(gestureDescription);
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
//                                                                                }
//                                                                              }, null);
//
//                                                                              // request again if wrong
//                                                                            }
//                                                                          } catch (Exception e) {
//                                                                            e.printStackTrace();
//                                                                          }
//
//                                                                        }
//                                                                      }
//
//                                                                      @Override
//                                                                      public void onFailure(Call<UploadObject> call, Throwable t) {
//
//                                                                      }
//                                                                    });
//                                                                    requestOTPAgainHandler.postDelayed(this, 5000);
//                                                                  }
//                                                                }, 5000);
//                                                              }
//                                                            } catch (Exception e) {
//
//                                                            }
//                                                          }
//
//                                                        }
//
//                                                        @Override
//                                                        public void onFailure(Call<UploadObject> call, Throwable t) {
//
//                                                        }
//                                                      });
//
//                                                    }
//                                                  } catch (Exception e) {
//                                                    e.printStackTrace();
//                                                  }
//                                                }
//                                              } catch (Exception e) {
//                                                e.printStackTrace();
//                                              }
//                                            }
//
//                                            @Override
//                                            public void onFailure(Call<UploadObject> call, Throwable t) {}
//                                          });
//                                        } catch (Exception e) {
//                                          e.printStackTrace();
//                                        }
                                        getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                      }
                                      else if (!getRootNode.getChild(1).getClassName().equals("android.widget.TextView")){
                                        OTPHandler.removeCallbacksAndMessages(null);
                                        if (restartOnceCounter == 0) {
                                          restartOnceCounter += 1;
                                          performGlobalAction(GLOBAL_ACTION_RECENTS);

                                          try{Thread.sleep(2000);} catch (Exception e) {};
                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                          Path path = new Path();
                                          path.moveTo(760, 1450);
                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 1, 1));
                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                            @Override
                                            public void onCompleted(GestureDescription gestureDescription) {
                                              super.onCompleted(gestureDescription);
                                              Log.i(TAG, "onCompleted: press ");
                                              try {
                                                Thread.sleep(1000);
                                                Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.digi.portal.mobdev.android");
                                                if (launchIntent != null) {
                                                  startActivity(launchIntent);
                                                }
                                              } catch (Exception e) {

                                              }
                                            }
                                          }, null);
                                        }
                                      }
                                    } catch (Exception e) {

                                    }
                                  }
                                }, null);
                              }
                            } catch (Exception e) {

                            }
                          }
                        }
                        @Override
                        public void onFailure(Call<UploadObject> call, Throwable t) {

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
          } catch (Exception e) {

          }
        }
      }

      /** reload btn **/
      List<AccessibilityNodeInfo> findReload = source.findAccessibilityNodeInfosByText("RELOAD");
      for (AccessibilityNodeInfo findReloadNode: findReload) {
        isFailed = preferences.getBoolean("isFailed", false);
        isCalled = preferences.getBoolean("isCalled", false);

        /** problem with logic here **/
        if (!isCalled) {
          Log.i(TAG, "first time call");
          if (!findReloadNode.getParent().getParent().getChild(0).getText().toString().contains("to MyDigi")) {
            editor.putString("oldBalance", findReloadNode.getParent().getParent().getChild(0).getText().toString().substring(2));
            editor.commit();
          }

          //          OTPHandler2.removeCallbacksAndMessages(null);
//          requestOTPAgainHandler.removeCallbacksAndMessages(null);
          try {
            Thread.sleep(2000);
            /** reload button press**/
//          if (clickReloadOnceCounter == 0) {
//            clickReloadOnceCounter += 1;
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            Path path = new Path();
            path.moveTo(26, 351);
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
              @Override
              public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);

                try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                /** navigate to PIN tab **/
                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                Path path = new Path();
                path.moveTo(225, 177);
                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                  @Override
                  public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);

                    try {Thread.sleep(2000);} catch (Exception e) {}
                  }}, null);
              }
            }, null);
//          }
          } catch (Exception e) {}
        }
        else if (isCalled && !isFailed) { // this condition updates after "success" page appears
          Log.i(TAG, "iscalled and !isfailed");
          if (isSuccessCounter == 0) {
            isSuccessCounter += 1;
            if (!findReloadNode.getParent().getParent().getChild(0).getText().toString().contains("to MyDigi")) {
              String receivedPhoneNumber = preferences.getString("phoneNum", "");
              String newBalanceAttempt = findReloadNode.getParent().getParent().getChild(0).getText().toString().substring(2);
              double a = Double.parseDouble(preferences.getString("oldBalance", ""));
              double b = Double.parseDouble(newBalanceAttempt);
              Log.i(TAG, "sp: " + preferences.getString("oldBalance", ""));
              Log.i(TAG, "newBalanceAttempt: " + newBalanceAttempt);
              Log.i(TAG, "newBalanceAttempt: " + df.format(b - a));

              if (!preferences.getString("oldBalance", "").equals(newBalanceAttempt)) {
                try {
                  Date date = new Date(System.currentTimeMillis());
                  String time = TWELVE_TF.format(date);
                  String formattedDate = DATE_TF.format(date);

                  String refNum =df.format(b - a) + convertTo24HoursFormat(time).replace(":", "") + formattedDate.replace("-", "");

                  JSONObject successActionObj = new JSONObject();
                  successActionObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                  successActionObj.put("PhoneNumber", receivedPhoneNumber);
                  successActionObj.put("action", "Result");
                  successActionObj.put("TranID", tranID);
                  successActionObj.put("ReferenceNumber", refNum);
                  successActionObj.put("Credit", df.format(b - a));
                  successActionObj.put("Balance", newBalanceAttempt);

                  Call<UploadObject> successActionCall= uploadTelcoInterface.uploadTelco(successActionObj);
                  successActionCall.enqueue(new Callback<UploadObject>() {
                    @Override
                    public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                      if (response.body() != null) {
                        telcoDatabaseHandler.updateIsSuccess(true, receivedPhoneNumber, "Digi", tranID);
                        Log.i(TAG, "onCompleted: not equals. save to sp(iscalled and new value)");
//                      waitingToResolve = false;

                        editor.putString("oldBalance", newBalanceAttempt);
                        editor.commit();

                        try {
                          Thread.sleep(2000);
                          /** reload button press**/
//          if (clickReloadOnceCounter == 0) {
//            clickReloadOnceCounter += 1;
                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                          Path path = new Path();
                          path.moveTo(26, 351);
                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                            @Override
                            public void onCompleted(GestureDescription gestureDescription) {
                              super.onCompleted(gestureDescription);

                              try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                              /** navigate to PIN tab **/
                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                              Path path = new Path();
                              path.moveTo(225, 177);
                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                @Override
                                public void onCompleted(GestureDescription gestureDescription) {
                                  super.onCompleted(gestureDescription);

                                  try {Thread.sleep(2000);} catch (Exception e) {}
                                }}, null);
                            }
                          }, null);
//          }
                        } catch (Exception e) {}

                      }
                      else if (response.code() == 401) {
                        editor.putBoolean("isCalled", false);
                        editor.commit();

                        try {
                          /** press hamburger menu **/
                          Thread.sleep(2000);
                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                          Path path = new Path();
                          path.moveTo(0, 68);
                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                          try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                            @Override
                            public void onCompleted(GestureDescription gestureDescription) {
                              super.onCompleted(gestureDescription);
                              try {
                                Thread.sleep(2000);
                                /** press settings **/
                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                Path path = new Path();
                                path.moveTo(0, 582);
                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                  @Override
                                  public void onCompleted(GestureDescription gestureDescription) {
                                    super.onCompleted(gestureDescription);
                                    try {
                                      Thread.sleep(2000);
                                      /** press sign out **/
                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                      Path path = new Path();
                                      path.moveTo(0, 773);
                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                      try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                        @Override
                                        public void onCompleted(GestureDescription gestureDescription) {
                                          super.onCompleted(gestureDescription);
                                          try {
                                            Thread.sleep(1000);
                                            editor.putBoolean("isCalled", false);
                                            editor.commit();
                                          } catch (Exception e) {

                                          }
                                        }
                                      }, null);



                                    } catch (Exception e) {

                                    }
                                  }
                                }, null);



                              } catch (Exception e) {

                              }
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
          }

        }
        else if (isCalled && isFailed) {
          if (isFailedCounter == 0) {
            isFailedCounter += 1;
            Log.i(TAG, "iscalled and failed! retry again");
            if (!findReloadNode.getParent().getParent().getChild(0).getText().toString().contains("to MyDigi")) {
              editor.putString("oldBalance", findReloadNode.getParent().getParent().getChild(0).getText().toString().substring(2));
              editor.commit();
            }

            try {
              Thread.sleep(2000);
              /** reload button press**/
//          if (clickReloadOnceCounter == 0) {
//            clickReloadOnceCounter += 1;
              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
              Path path = new Path();
              path.moveTo(26, 351);
              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                  super.onCompleted(gestureDescription);

                  try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                  /** navigate to PIN tab **/
                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                  Path path = new Path();
                  path.moveTo(225, 177);
                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                      super.onCompleted(gestureDescription);

                      try {Thread.sleep(2000);} catch (Exception e) {}
                    }}, null);
                }
              }, null);
//          }
            } catch (Exception e) {}
          }


        }
      }

      /** reload pin et**/
      if (source.getClassName().equals("android.widget.EditText")) {
        if (source.getText().equals("Enter 15 or 16 digit PIN")) {
          isFailed = preferences.getBoolean("isFailed", false);
          isCalled = preferences.getBoolean("isCalled", false);

          try {
            String phoneNum = preferences.getString("phoneNum", "");
            /** if first time balance post, perform as below. Upon successful reload, update balance called to true **/
            if (!isCalled) {
              if (waitingToResolve) {
                Log.i(TAG, "resolving... (first time call)");
              }
              else if (!waitingToResolve) {
                if (postMainOnceCounter == 0) {
                  postMainOnceCounter += 1;
                  String oldBalance = preferences.getString("oldBalance", "");

                  JSONObject balanceActionObj = new JSONObject();
                  balanceActionObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                  balanceActionObj.put("PhoneNumber", phoneNum);
                  balanceActionObj.put("action", "MAIN");
                  balanceActionObj.put("Balance", oldBalance);

                  Call<UploadObject> balanceActionCall = uploadTelcoInterface.uploadTelco(balanceActionObj);
                  balanceActionCall.enqueue(new Callback<UploadObject>() {
                    @Override
                    public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                      if (response.body() != null) {
                        clickReloadOnceCounter = 0;
                        isFailedCounter = 0;
                        isSuccessCounter = 0;

                        try {
                          Thread.sleep(1000);
                          String phoneNum = preferences.getString("phoneNum", "");

                          JSONObject reloadPINActionObj = new JSONObject();
                          reloadPINActionObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                          reloadPINActionObj.put("PhoneNumber", phoneNum);
                          reloadPINActionObj.put("action", "ReloadPIN");

                          Call<UploadObject> reloadPINActionCall = uploadTelcoInterface.uploadTelco(reloadPINActionObj);
                          reloadPINActionCall.enqueue(new Callback<UploadObject>() {
                            @Override
                            public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                              try {
                                postMainOnceCounter = 0;
                                if (response.body() == null) {
                                  Log.i(TAG, "Waiting for reload PIN...");

                                  reloadPINHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {

                                      call.clone().enqueue(new Callback<UploadObject>() {
                                        @Override
                                        public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                          if (response.body() != null) {
                                            try {
                                              JSONObject reloadPINActionResponseObj = new JSONObject(new Gson().toJson(response.body()));
                                              if (reloadPINActionResponseObj.get("status").equals("SUCCESS")) {
                                                reloadPINHandler.removeCallbacksAndMessages(null);

                                                String reloadPINString = reloadPINActionResponseObj.get("ReloadPIN").toString();
                                                tranID = reloadPINActionResponseObj.get("TranID").toString();

                                                saveReloadPIN(phoneNum, "Digi", tranID, reloadPINString);

                                                Bundle reloadPINBundle = new Bundle();
                                                reloadPINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reloadPINString);
                                                try {
                                                  source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, reloadPINBundle);
                                                  Thread.sleep(2000);
                                                  source.getParent().getParent().getChild(4).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                  Thread.sleep(2000);

                                                  getRootNode = getRootInActiveWindow();
                                                  if (getRootNode.getChild(1).getText().toString().contains("Invalid")) {
                                                    if (postFailOnceCounter == 0) {
                                                      postFailOnceCounter += 1;
                                                      JSONObject reloadFailedObj = new JSONObject();
                                                      reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                                      reloadFailedObj.put("PhoneNumber", phoneNum);
                                                      reloadFailedObj.put("action", "Result");
                                                      reloadFailedObj.put("TranID", tranID);
                                                      reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                                      Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                                      reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                                        @Override
                                                        public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                          if (response.code() == 400) {
                                                            Log.i(TAG, "onResponse: error msg 400");
                                                            getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                                          }
                                                          else if (response.code() == 401) {
                                                            Log.i(TAG, "onResponse: error msg 401");
                                                            getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                          }
                                                          else if (response.code() == 200) {
                                                            telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                                            editor.putBoolean("isFailed", true);
                                                            editor.commit();
                                                            postFailOnceCounter = 0;

                                                            getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                            try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                            Path path = new Path();
                                                            path.moveTo(0, 68);
                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                              @Override
                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                super.onCompleted(gestureDescription);
                                                                try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                Path path = new Path();
                                                                path.moveTo(26, 351);
                                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                  @Override
                                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                                    super.onCompleted(gestureDescription);

                                                                    try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                                    /** navigate to PIN tab again **/
                                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                    Path path = new Path();
                                                                    path.moveTo(225, 177);
                                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                      @Override
                                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                                        super.onCompleted(gestureDescription);

                                                                        try {Thread.sleep(1000);} catch (Exception e) {}
                                                                      }}, null);
                                                                  }
                                                                }, null);
                                                              }
                                                            }, null);

                                                            // attempt to re-do
//                                                try {Thread.sleep(1000);}catch (Exception e) {}
//                                                editor.putBoolean("isFailed", true);
//                                                editor.commit();

                                                          }
                                                        }

                                                        @Override
                                                        public void onFailure(Call<UploadObject> call, Throwable t) {

                                                        }
                                                      });
                                                    }
                                                  }
                                                  else if (getRootNode.getChild(1).getText().toString().contains("You have reached the max allowed number of times")) {
                                                    if (postFailOnceCounter == 0) {
                                                      postFailOnceCounter += 1;

                                                      JSONObject blockedActionObj = new JSONObject();
                                                      blockedActionObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                                      blockedActionObj.put("PhoneNumber", phoneNum);
                                                      blockedActionObj.put("action", "Result");
                                                      blockedActionObj.put("TranID", tranID);
                                                      blockedActionObj.put("message", getRootNode.getChild(1).getText().toString());
                                                      blockedActionObj.put("phoneStatus", "BLOCKED");

                                                      Call<UploadObject> blockedActionCall = uploadTelcoInterface.uploadTelco(blockedActionObj);
                                                      blockedActionCall.enqueue(new Callback<UploadObject>() {
                                                        @Override
                                                        public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                          if (response.code() == 400) {
                                                            Log.i(TAG, "onResponse: posted and this exist 400");
                                                          }
                                                          else if (response.code() == 401) {
                                                            telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                                            getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                            editor.putBoolean("isFailed", true);
                                                            editor.commit();
                                                            postFailOnceCounter = 0;
                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                            Path path = new Path();
                                                            path.moveTo(0, 68);
                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                            try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                              @Override
                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                super.onCompleted(gestureDescription);
                                                                try {
                                                                  /** press hamburger menu **/
                                                                  Thread.sleep(2000);
                                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                  Path path = new Path();
                                                                  path.moveTo(0, 68);
                                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                  try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                    @Override
                                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                                      super.onCompleted(gestureDescription);
                                                                      try {
                                                                        Thread.sleep(2000);
                                                                        /** press settings **/
                                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                        Path path = new Path();
                                                                        path.moveTo(0, 582);
                                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                        try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                          @Override
                                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                                            super.onCompleted(gestureDescription);
                                                                            try {
                                                                              Thread.sleep(2000);
                                                                              /** press sign out **/
                                                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                              Path path = new Path();
                                                                              path.moveTo(0, 773);
                                                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                              try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                                @Override
                                                                                public void onCompleted(GestureDescription gestureDescription) {
                                                                                  super.onCompleted(gestureDescription);
                                                                                  try {
                                                                                    Thread.sleep(1000);
                                                                                    editor.putBoolean("isCalled", false);
                                                                                    editor.commit();
                                                                                  } catch (Exception e) {

                                                                                  }
                                                                                }
                                                                              }, null);



                                                                            } catch (Exception e) {

                                                                            }
                                                                          }
                                                                        }, null);



                                                                      } catch (Exception e) {

                                                                      }
                                                                    }
                                                                  }, null);



                                                                } catch (Exception e) {

                                                                }
                                                              }
                                                            }, null);

                                                          }
                                                          else if (response.code() == 200) {
                                                            getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                          }
                                                        }

                                                        @Override
                                                        public void onFailure(Call<UploadObject> call, Throwable t) {

                                                        }
                                                      });
                                                    }

                                                  }
                                                  else if (getRootNode.getChild(1).getText().toString().contains("Oops! Something")) {
                                                    if (postFailOnceCounter == 0) {
                                                      postFailOnceCounter += 1;

                                                      JSONObject reloadFailedObj = new JSONObject();
                                                      reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                                      reloadFailedObj.put("PhoneNumber", phoneNum);
                                                      reloadFailedObj.put("action", "Result");
                                                      reloadFailedObj.put("TranID", tranID);
                                                      reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                                      Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                                      reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                                        @Override
                                                        public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                          if (response.code() == 400) {
                                                            Log.i(TAG, "onResponse: error msg 400");
                                                            getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                                          }
                                                          else if (response.code() == 401) {
                                                            Log.i(TAG, "onResponse: error msg 401");
                                                            getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                          }
                                                          else if (response.code() == 200) {
                                                            telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                                            editor.putBoolean("isFailed", true);
                                                            editor.commit();
                                                            postFailOnceCounter = 0;

                                                            getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                            try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                            Path path = new Path();
                                                            path.moveTo(0, 68);
                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                              @Override
                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                super.onCompleted(gestureDescription);
                                                                try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                Path path = new Path();
                                                                path.moveTo(26, 351);
                                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                  @Override
                                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                                    super.onCompleted(gestureDescription);

                                                                    try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                                    /** navigate to PIN tab again **/
                                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                    Path path = new Path();
                                                                    path.moveTo(225, 177);
                                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                      @Override
                                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                                        super.onCompleted(gestureDescription);

                                                                        try {Thread.sleep(1000);} catch (Exception e) {}
                                                                      }}, null);
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
                                                    }

                                                  }
                                                  else if (getRootNode.getChild(1).getText().toString().contains("was redeemed")) {
                                                    if (postFailOnceCounter == 0) {
                                                      postFailOnceCounter += 1;

                                                      JSONObject reloadFailedObj = new JSONObject();
                                                      reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                                      reloadFailedObj.put("PhoneNumber", phoneNum);
                                                      reloadFailedObj.put("action", "Result");
                                                      reloadFailedObj.put("TranID", tranID);
                                                      reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                                      Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                                      reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                                        @Override
                                                        public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                          if (response.code() == 400) {
                                                            Log.i(TAG, "onResponse: error msg 400");
                                                            getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                                          }
                                                          else if (response.code() == 401) {
                                                            Log.i(TAG, "onResponse: error msg 401");
                                                            getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                          }
                                                          else if (response.code() == 200) {
                                                            telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                                            editor.putBoolean("isFailed", true);
                                                            editor.commit();
                                                            postFailOnceCounter = 0;

                                                            getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                            try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                            Path path = new Path();
                                                            path.moveTo(0, 68);
                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                              @Override
                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                super.onCompleted(gestureDescription);
                                                                try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                Path path = new Path();
                                                                path.moveTo(26, 351);
                                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                  @Override
                                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                                    super.onCompleted(gestureDescription);

                                                                    try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                                    /** navigate to PIN tab again **/
                                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                    Path path = new Path();
                                                                    path.moveTo(225, 177);
                                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                      @Override
                                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                                        super.onCompleted(gestureDescription);

                                                                        try {Thread.sleep(1000);} catch (Exception e) {}
                                                                      }}, null);
                                                                  }
                                                                }, null);
                                                              }
                                                            }, null);

                                                            // attempt to re-do
//                                                try {Thread.sleep(1000);}catch (Exception e) {}
//                                                editor.putBoolean("isFailed", true);
//                                                editor.commit();

                                                          }
                                                        }

                                                        @Override
                                                        public void onFailure(Call<UploadObject> call, Throwable t) {

                                                        }
                                                      });
                                                    }

                                                  }
                                                } catch (Exception e) {

                                                }

                                              }
                                            } catch (Exception e) {

                                            }
                                          }
                                        }

                                        @Override
                                        public void onFailure(Call<UploadObject> call, Throwable t) {

                                        }
                                      });

                                      reloadPINHandler.postDelayed(this, 5000);
                                    }
                                  }, 5000);
                                }
                                else {
                                  try {
                                    JSONObject reloadPINActionResponseObj = new JSONObject(new Gson().toJson(response.body()));
                                    if (reloadPINActionResponseObj.get("status").equals("SUCCESS")) {
                                      reloadPINHandler.removeCallbacksAndMessages(null);

                                      String reloadPINString = reloadPINActionResponseObj.get("ReloadPIN").toString();
                                      tranID = reloadPINActionResponseObj.get("TranID").toString();

                                      saveReloadPIN(phoneNum, "Digi", tranID, reloadPINString);

                                      Bundle reloadPINBundle = new Bundle();
                                      reloadPINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reloadPINString);
                                      try {
                                        source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, reloadPINBundle);
                                        Thread.sleep(2000);
                                        source.getParent().getParent().getChild(4).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        Thread.sleep(2000);

                                        getRootNode = getRootInActiveWindow();
                                        if (getRootNode.getChild(1).getText().toString().contains("Invalid")) {
                                          if (postFailOnceCounter == 0) {
                                            postFailOnceCounter += 1;
                                            JSONObject reloadFailedObj = new JSONObject();
                                            reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                            reloadFailedObj.put("PhoneNumber", phoneNum);
                                            reloadFailedObj.put("action", "Result");
                                            reloadFailedObj.put("TranID", tranID);
                                            reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                            Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                            reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                              @Override
                                              public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                if (response.code() == 400) {
                                                  Log.i(TAG, "onResponse: error msg 400");
                                                  getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                                }
                                                else if (response.code() == 401) {
                                                  Log.i(TAG, "onResponse: error msg 401");
                                                  getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                }
                                                else if (response.code() == 200) {
                                                  telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                                  editor.putBoolean("isFailed", true);
                                                  editor.commit();
                                                  postFailOnceCounter = 0;

                                                  getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                  try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                  Path path = new Path();
                                                  path.moveTo(0, 68);
                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                    @Override
                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                      super.onCompleted(gestureDescription);
                                                      try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                      Path path = new Path();
                                                      path.moveTo(26, 351);
                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                        @Override
                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                          super.onCompleted(gestureDescription);

                                                          try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                          /** navigate to PIN tab again **/
                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                          Path path = new Path();
                                                          path.moveTo(225, 177);
                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                            @Override
                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                              super.onCompleted(gestureDescription);

                                                              try {Thread.sleep(1000);} catch (Exception e) {}
                                                            }}, null);
                                                        }
                                                      }, null);
                                                    }
                                                  }, null);

                                                  // attempt to re-do
//                                                try {Thread.sleep(1000);}catch (Exception e) {}
//                                                editor.putBoolean("isFailed", true);
//                                                editor.commit();

                                                }
                                              }

                                              @Override
                                              public void onFailure(Call<UploadObject> call, Throwable t) {

                                              }
                                            });
                                          }
                                        }
                                        else if (getRootNode.getChild(1).getText().toString().contains("You have reached the max allowed number of times")) {
                                          if (postFailOnceCounter == 0) {
                                            postFailOnceCounter += 1;

                                            JSONObject blockedActionObj = new JSONObject();
                                            blockedActionObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                            blockedActionObj.put("PhoneNumber", phoneNum);
                                            blockedActionObj.put("action", "Result");
                                            blockedActionObj.put("TranID", tranID);
                                            blockedActionObj.put("message", getRootNode.getChild(1).getText().toString());
                                            blockedActionObj.put("phoneStatus", "BLOCKED");

                                            Call<UploadObject> blockedActionCall = uploadTelcoInterface.uploadTelco(blockedActionObj);
                                            blockedActionCall.enqueue(new Callback<UploadObject>() {
                                              @Override
                                              public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                if (response.code() == 400) {
                                                  Log.i(TAG, "onResponse: posted and this exist 400");
                                                }
                                                else if (response.code() == 401) {
                                                  telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                                  getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                  editor.putBoolean("isFailed", true);
                                                  editor.commit();
                                                  postFailOnceCounter = 0;
                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                  Path path = new Path();
                                                  path.moveTo(0, 68);
                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                  try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                    @Override
                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                      super.onCompleted(gestureDescription);
                                                      try {
                                                        /** press hamburger menu **/
                                                        Thread.sleep(2000);
                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                        Path path = new Path();
                                                        path.moveTo(0, 68);
                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                        try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                          @Override
                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                            super.onCompleted(gestureDescription);
                                                            try {
                                                              Thread.sleep(2000);
                                                              /** press settings **/
                                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                              Path path = new Path();
                                                              path.moveTo(0, 582);
                                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                              try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                @Override
                                                                public void onCompleted(GestureDescription gestureDescription) {
                                                                  super.onCompleted(gestureDescription);
                                                                  try {
                                                                    Thread.sleep(2000);
                                                                    /** press sign out **/
                                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                    Path path = new Path();
                                                                    path.moveTo(0, 773);
                                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                    try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                      @Override
                                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                                        super.onCompleted(gestureDescription);
                                                                        try {
                                                                          Thread.sleep(1000);
                                                                          editor.putBoolean("isCalled", false);
                                                                          editor.commit();
                                                                        } catch (Exception e) {

                                                                        }
                                                                      }
                                                                    }, null);



                                                                  } catch (Exception e) {

                                                                  }
                                                                }
                                                              }, null);



                                                            } catch (Exception e) {

                                                            }
                                                          }
                                                        }, null);



                                                      } catch (Exception e) {

                                                      }
                                                    }
                                                  }, null);

                                                }
                                                else if (response.code() == 200) {
                                                  getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                }
                                              }

                                              @Override
                                              public void onFailure(Call<UploadObject> call, Throwable t) {

                                              }
                                            });
                                          }

                                        }
                                        else if (getRootNode.getChild(1).getText().toString().contains("Oops! Something")) {
                                          if (postFailOnceCounter == 0) {
                                            postFailOnceCounter += 1;

                                            JSONObject reloadFailedObj = new JSONObject();
                                            reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                            reloadFailedObj.put("PhoneNumber", phoneNum);
                                            reloadFailedObj.put("action", "Result");
                                            reloadFailedObj.put("TranID", tranID);
                                            reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                            Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                            reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                              @Override
                                              public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                if (response.code() == 400) {
                                                  Log.i(TAG, "onResponse: error msg 400");
                                                  getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                                }
                                                else if (response.code() == 401) {
                                                  Log.i(TAG, "onResponse: error msg 401");
                                                  getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                }
                                                else if (response.code() == 200) {
                                                  telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                                  editor.putBoolean("isFailed", true);
                                                  editor.commit();
                                                  postFailOnceCounter = 0;

                                                  getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                  try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                  Path path = new Path();
                                                  path.moveTo(0, 68);
                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                    @Override
                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                      super.onCompleted(gestureDescription);
                                                      try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                      Path path = new Path();
                                                      path.moveTo(26, 351);
                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                        @Override
                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                          super.onCompleted(gestureDescription);

                                                          try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                          /** navigate to PIN tab again **/
                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                          Path path = new Path();
                                                          path.moveTo(225, 177);
                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                            @Override
                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                              super.onCompleted(gestureDescription);

                                                              try {Thread.sleep(1000);} catch (Exception e) {}
                                                            }}, null);
                                                        }
                                                      }, null);
                                                    }
                                                  }, null);

                                                  // attempt to re-do
//                                                try {Thread.sleep(1000);}catch (Exception e) {}
//                                                editor.putBoolean("isFailed", true);
//                                                editor.commit();

                                                }
                                              }

                                              @Override
                                              public void onFailure(Call<UploadObject> call, Throwable t) {

                                              }
                                            });
                                          }

                                        }
                                        else if (getRootNode.getChild(1).getText().toString().contains("was redeemed")) {
                                          if (postFailOnceCounter == 0) {
                                            postFailOnceCounter += 1;

                                            JSONObject reloadFailedObj = new JSONObject();
                                            reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                            reloadFailedObj.put("PhoneNumber", phoneNum);
                                            reloadFailedObj.put("action", "Result");
                                            reloadFailedObj.put("TranID", tranID);
                                            reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                            Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                            reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                              @Override
                                              public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                if (response.code() == 400) {
                                                  Log.i(TAG, "onResponse: error msg 400");
                                                  getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                                }
                                                else if (response.code() == 401) {
                                                  Log.i(TAG, "onResponse: error msg 401");
                                                  getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                }
                                                else if (response.code() == 200) {
                                                  telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                                  editor.putBoolean("isFailed", true);
                                                  editor.commit();
                                                  postFailOnceCounter = 0;

                                                  getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                  try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                  Path path = new Path();
                                                  path.moveTo(0, 68);
                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                    @Override
                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                      super.onCompleted(gestureDescription);
                                                      try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                      Path path = new Path();
                                                      path.moveTo(26, 351);
                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                        @Override
                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                          super.onCompleted(gestureDescription);

                                                          try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                          /** navigate to PIN tab again **/
                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                          Path path = new Path();
                                                          path.moveTo(225, 177);
                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                            @Override
                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                              super.onCompleted(gestureDescription);

                                                              try {Thread.sleep(1000);} catch (Exception e) {}
                                                            }}, null);
                                                        }
                                                      }, null);
                                                    }
                                                  }, null);

                                                  // attempt to re-do
//                                                try {Thread.sleep(1000);}catch (Exception e) {}
//                                                editor.putBoolean("isFailed", true);
//                                                editor.commit();

                                                }
                                              }

                                              @Override
                                              public void onFailure(Call<UploadObject> call, Throwable t) {

                                              }
                                            });
                                          }

                                        }
                                      } catch (Exception e) {

                                      }

                                    }
                                  } catch (Exception e) {

                                  }
                                }
                              } catch (Exception e) {

                              }
                            }

                            @Override
                            public void onFailure(Call<UploadObject> call, Throwable t) {

                            }
                          });
                        } catch (Exception e) {}
                      }
                      else if (response.code() == 401) {
                        try {
                          /** press hamburger menu **/
                          Thread.sleep(2000);
                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                          Path path = new Path();
                          path.moveTo(0, 68);
                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                          try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                            @Override
                            public void onCompleted(GestureDescription gestureDescription) {
                              super.onCompleted(gestureDescription);
                              try {
                                Thread.sleep(2000);
                                /** press settings **/
                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                Path path = new Path();
                                path.moveTo(0, 582);
                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                  @Override
                                  public void onCompleted(GestureDescription gestureDescription) {
                                    super.onCompleted(gestureDescription);
                                    try {
                                      Thread.sleep(2000);
                                      /** press sign out **/
                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                      Path path = new Path();
                                      path.moveTo(0, 773);
                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                      try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                        @Override
                                        public void onCompleted(GestureDescription gestureDescription) {
                                          super.onCompleted(gestureDescription);
                                          try {
                                            Thread.sleep(1000);
                                            editor.putBoolean("isCalled", false);
                                            editor.commit();
                                          } catch (Exception e) {

                                          }
                                        }
                                      }, null);



                                    } catch (Exception e) {

                                    }
                                  }
                                }, null);



                              } catch (Exception e) {

                              }
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
            }
            else if (isCalled && !isFailed) {
              if (waitingToResolve) {
                Log.i(TAG, "resolving... (first time call)");
              } else if (!waitingToResolve) {
                try {
                  isFailedCounter = 0;
                  isSuccessCounter = 0;

                  Thread.sleep(1000);
                  JSONObject reloadPINActionObj = new JSONObject();
                  reloadPINActionObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                  reloadPINActionObj.put("PhoneNumber", phoneNum);
                  reloadPINActionObj.put("action", "ReloadPIN");

                  Call<UploadObject> reloadPINActionCall = uploadTelcoInterface.uploadTelco(reloadPINActionObj);
                  reloadPINActionCall.enqueue(new Callback<UploadObject>() {
                    @Override
                    public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                      try {
                        postMainOnceCounter = 0;
                        if (response.body() == null) {
                          Log.i(TAG, "Waiting for reload PIN...");

                          reloadPINHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {

                              call.clone().enqueue(new Callback<UploadObject>() {
                                @Override
                                public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                  if (response.body() != null) {
                                    try {
                                      JSONObject reloadPINActionResponseObj = new JSONObject(new Gson().toJson(response.body()));
                                      if (reloadPINActionResponseObj.get("status").equals("SUCCESS")) {
                                        reloadPINHandler.removeCallbacksAndMessages(null);

                                        String reloadPINString = reloadPINActionResponseObj.get("ReloadPIN").toString();
                                        tranID = reloadPINActionResponseObj.get("TranID").toString();

                                        saveReloadPIN(phoneNum, "Digi", tranID, reloadPINString);

                                        Bundle reloadPINBundle = new Bundle();
                                        reloadPINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reloadPINString);
                                        try {
                                          source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, reloadPINBundle);
                                          Thread.sleep(2000);
                                          source.getParent().getParent().getChild(4).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                          Thread.sleep(2000);

                                          getRootNode = getRootInActiveWindow();
                                          if (getRootNode.getChild(1).getText().toString().contains("Invalid")) {
                                            if (postFailOnceCounter == 0) {
                                              postFailOnceCounter += 1;
                                              JSONObject reloadFailedObj = new JSONObject();
                                              reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                              reloadFailedObj.put("PhoneNumber", phoneNum);
                                              reloadFailedObj.put("action", "Result");
                                              reloadFailedObj.put("TranID", tranID);
                                              reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                              Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                              reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                                @Override
                                                public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                  if (response.code() == 400) {
                                                    Log.i(TAG, "onResponse: error msg 400");
                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                                  }
                                                  else if (response.code() == 401) {
                                                    Log.i(TAG, "onResponse: error msg 401");
                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                  }
                                                  else if (response.code() == 200) {
                                                    telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                                    editor.putBoolean("isFailed", true);
                                                    editor.commit();
                                                    postFailOnceCounter = 0;

                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                    try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                    Path path = new Path();
                                                    path.moveTo(0, 68);
                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                      @Override
                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                        super.onCompleted(gestureDescription);
                                                        try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                        Path path = new Path();
                                                        path.moveTo(26, 351);
                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                          @Override
                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                            super.onCompleted(gestureDescription);

                                                            try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                            /** navigate to PIN tab again **/
                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                            Path path = new Path();
                                                            path.moveTo(225, 177);
                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                              @Override
                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                super.onCompleted(gestureDescription);

                                                                try {Thread.sleep(1000);} catch (Exception e) {}
                                                              }}, null);
                                                          }
                                                        }, null);
                                                      }
                                                    }, null);

                                                    // attempt to re-do
//                                                try {Thread.sleep(1000);}catch (Exception e) {}
//                                                editor.putBoolean("isFailed", true);
//                                                editor.commit();

                                                  }
                                                }

                                                @Override
                                                public void onFailure(Call<UploadObject> call, Throwable t) {

                                                }
                                              });
                                            }
                                          }
                                          else if (getRootNode.getChild(1).getText().toString().contains("You have reached the max allowed number of times")) {
                                            if (postFailOnceCounter == 0) {
                                              postFailOnceCounter += 1;

                                              JSONObject blockedActionObj = new JSONObject();
                                              blockedActionObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                              blockedActionObj.put("PhoneNumber", phoneNum);
                                              blockedActionObj.put("action", "Result");
                                              blockedActionObj.put("TranID", tranID);
                                              blockedActionObj.put("message", getRootNode.getChild(1).getText().toString());
                                              blockedActionObj.put("phoneStatus", "BLOCKED");

                                              Call<UploadObject> blockedActionCall = uploadTelcoInterface.uploadTelco(blockedActionObj);
                                              blockedActionCall.enqueue(new Callback<UploadObject>() {
                                                @Override
                                                public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                  if (response.code() == 400) {
                                                    Log.i(TAG, "onResponse: posted and this exist 400");
                                                  }
                                                  else if (response.code() == 401) {
                                                    telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                    editor.putBoolean("isFailed", true);
                                                    editor.commit();
                                                    postFailOnceCounter = 0;
                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                    Path path = new Path();
                                                    path.moveTo(0, 68);
                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                    try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                      @Override
                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                        super.onCompleted(gestureDescription);
                                                        try {
                                                          /** press hamburger menu **/
                                                          Thread.sleep(2000);
                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                          Path path = new Path();
                                                          path.moveTo(0, 68);
                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                          try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                            @Override
                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                              super.onCompleted(gestureDescription);
                                                              try {
                                                                Thread.sleep(2000);
                                                                /** press settings **/
                                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                Path path = new Path();
                                                                path.moveTo(0, 582);
                                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                  @Override
                                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                                    super.onCompleted(gestureDescription);
                                                                    try {
                                                                      Thread.sleep(2000);
                                                                      /** press sign out **/
                                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                      Path path = new Path();
                                                                      path.moveTo(0, 773);
                                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                      try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                        @Override
                                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                                          super.onCompleted(gestureDescription);
                                                                          try {
                                                                            Thread.sleep(1000);
                                                                            editor.putBoolean("isCalled", false);
                                                                            editor.commit();
                                                                          } catch (Exception e) {

                                                                          }
                                                                        }
                                                                      }, null);



                                                                    } catch (Exception e) {

                                                                    }
                                                                  }
                                                                }, null);



                                                              } catch (Exception e) {

                                                              }
                                                            }
                                                          }, null);



                                                        } catch (Exception e) {

                                                        }
                                                      }
                                                    }, null);

                                                  }
                                                  else if (response.code() == 200) {
                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                  }
                                                }

                                                @Override
                                                public void onFailure(Call<UploadObject> call, Throwable t) {

                                                }
                                              });
                                            }

                                          }
                                          else if (getRootNode.getChild(1).getText().toString().contains("Oops! Something")) {
                                            if (postFailOnceCounter == 0) {
                                              postFailOnceCounter += 1;

                                              JSONObject reloadFailedObj = new JSONObject();
                                              reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                              reloadFailedObj.put("PhoneNumber", phoneNum);
                                              reloadFailedObj.put("action", "Result");
                                              reloadFailedObj.put("TranID", tranID);
                                              reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                              Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                              reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                                @Override
                                                public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                  if (response.code() == 400) {
                                                    Log.i(TAG, "onResponse: error msg 400");
                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                                  }
                                                  else if (response.code() == 401) {
                                                    Log.i(TAG, "onResponse: error msg 401");
                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                  }
                                                  else if (response.code() == 200) {
                                                    telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                                    editor.putBoolean("isFailed", true);
                                                    editor.commit();
                                                    postFailOnceCounter = 0;

                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                    try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                    Path path = new Path();
                                                    path.moveTo(0, 68);
                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                      @Override
                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                        super.onCompleted(gestureDescription);
                                                        try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                        Path path = new Path();
                                                        path.moveTo(26, 351);
                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                          @Override
                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                            super.onCompleted(gestureDescription);

                                                            try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                            /** navigate to PIN tab again **/
                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                            Path path = new Path();
                                                            path.moveTo(225, 177);
                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                              @Override
                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                super.onCompleted(gestureDescription);

                                                                try {Thread.sleep(1000);} catch (Exception e) {}
                                                              }}, null);
                                                          }
                                                        }, null);
                                                      }
                                                    }, null);

                                                    // attempt to re-do
//                                                try {Thread.sleep(1000);}catch (Exception e) {}
//                                                editor.putBoolean("isFailed", true);
//                                                editor.commit();

                                                  }
                                                }

                                                @Override
                                                public void onFailure(Call<UploadObject> call, Throwable t) {

                                                }
                                              });
                                            }

                                          }
                                          else if (getRootNode.getChild(1).getText().toString().contains("was redeemed")) {
                                            if (postFailOnceCounter == 0) {
                                              postFailOnceCounter += 1;

                                              JSONObject reloadFailedObj = new JSONObject();
                                              reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                              reloadFailedObj.put("PhoneNumber", phoneNum);
                                              reloadFailedObj.put("action", "Result");
                                              reloadFailedObj.put("TranID", tranID);
                                              reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                              Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                              reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                                @Override
                                                public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                  if (response.code() == 400) {
                                                    Log.i(TAG, "onResponse: error msg 400");
                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                                  }
                                                  else if (response.code() == 401) {
                                                    Log.i(TAG, "onResponse: error msg 401");
                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                  }
                                                  else if (response.code() == 200) {
                                                    telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                                    editor.putBoolean("isFailed", true);
                                                    editor.commit();
                                                    postFailOnceCounter = 0;

                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                    try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                    Path path = new Path();
                                                    path.moveTo(0, 68);
                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                      @Override
                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                        super.onCompleted(gestureDescription);
                                                        try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                        Path path = new Path();
                                                        path.moveTo(26, 351);
                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                          @Override
                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                            super.onCompleted(gestureDescription);

                                                            try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                            /** navigate to PIN tab again **/
                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                            Path path = new Path();
                                                            path.moveTo(225, 177);
                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                              @Override
                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                super.onCompleted(gestureDescription);

                                                                try {Thread.sleep(1000);} catch (Exception e) {}
                                                              }}, null);
                                                          }
                                                        }, null);
                                                      }
                                                    }, null);

                                                    // attempt to re-do
//                                                try {Thread.sleep(1000);}catch (Exception e) {}
//                                                editor.putBoolean("isFailed", true);
//                                                editor.commit();

                                                  }
                                                }

                                                @Override
                                                public void onFailure(Call<UploadObject> call, Throwable t) {

                                                }
                                              });
                                            }

                                          }
                                        } catch (Exception e) {

                                        }

                                      }
                                    } catch (Exception e) {

                                    }
                                  }
                                }

                                @Override
                                public void onFailure(Call<UploadObject> call, Throwable t) {

                                }
                              });

                              reloadPINHandler.postDelayed(this, 5000);
                            }
                          }, 5000);
                        }
                        else {
                          try {
                            JSONObject reloadPINActionResponseObj = new JSONObject(new Gson().toJson(response.body()));
                            if (reloadPINActionResponseObj.get("status").equals("SUCCESS")) {
                              reloadPINHandler.removeCallbacksAndMessages(null);

                              String reloadPINString = reloadPINActionResponseObj.get("ReloadPIN").toString();
                              tranID = reloadPINActionResponseObj.get("TranID").toString();

                              saveReloadPIN(phoneNum, "Digi", tranID, reloadPINString);

                              Bundle reloadPINBundle = new Bundle();
                              reloadPINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reloadPINString);
                              try {
                                source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, reloadPINBundle);
                                Thread.sleep(2000);
                                source.getParent().getParent().getChild(4).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                Thread.sleep(2000);

                                getRootNode = getRootInActiveWindow();
                                if (getRootNode.getChild(1).getText().toString().contains("Invalid")) {
                                  if (postFailOnceCounter == 0) {
                                    postFailOnceCounter += 1;
                                    JSONObject reloadFailedObj = new JSONObject();
                                    reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                    reloadFailedObj.put("PhoneNumber", phoneNum);
                                    reloadFailedObj.put("action", "Result");
                                    reloadFailedObj.put("TranID", tranID);
                                    reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                    Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                    reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                      @Override
                                      public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                        if (response.code() == 400) {
                                          Log.i(TAG, "onResponse: error msg 400");
                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                        }
                                        else if (response.code() == 401) {
                                          Log.i(TAG, "onResponse: error msg 401");
                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        }
                                        else if (response.code() == 200) {
                                          telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                          editor.putBoolean("isFailed", true);
                                          editor.commit();
                                          postFailOnceCounter = 0;

                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                          try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                          Path path = new Path();
                                          path.moveTo(0, 68);
                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                            @Override
                                            public void onCompleted(GestureDescription gestureDescription) {
                                              super.onCompleted(gestureDescription);
                                              try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                              Path path = new Path();
                                              path.moveTo(26, 351);
                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                @Override
                                                public void onCompleted(GestureDescription gestureDescription) {
                                                  super.onCompleted(gestureDescription);

                                                  try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                  /** navigate to PIN tab again **/
                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                  Path path = new Path();
                                                  path.moveTo(225, 177);
                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                    @Override
                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                      super.onCompleted(gestureDescription);

                                                      try {Thread.sleep(1000);} catch (Exception e) {}
                                                    }}, null);
                                                }
                                              }, null);
                                            }
                                          }, null);

                                          // attempt to re-do
//                                                try {Thread.sleep(1000);}catch (Exception e) {}
//                                                editor.putBoolean("isFailed", true);
//                                                editor.commit();

                                        }
                                      }

                                      @Override
                                      public void onFailure(Call<UploadObject> call, Throwable t) {

                                      }
                                    });
                                  }
                                }
                                else if (getRootNode.getChild(1).getText().toString().contains("You have reached the max allowed number of times")) {
                                  if (postFailOnceCounter == 0) {
                                    postFailOnceCounter += 1;

                                    JSONObject blockedActionObj = new JSONObject();
                                    blockedActionObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                    blockedActionObj.put("PhoneNumber", phoneNum);
                                    blockedActionObj.put("action", "Result");
                                    blockedActionObj.put("TranID", tranID);
                                    blockedActionObj.put("message", getRootNode.getChild(1).getText().toString());
                                    blockedActionObj.put("phoneStatus", "BLOCKED");

                                    Call<UploadObject> blockedActionCall = uploadTelcoInterface.uploadTelco(blockedActionObj);
                                    blockedActionCall.enqueue(new Callback<UploadObject>() {
                                      @Override
                                      public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                        if (response.code() == 400) {
                                          Log.i(TAG, "onResponse: posted and this exist 400");
                                        }
                                        else if (response.code() == 401) {
                                          telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                          editor.putBoolean("isFailed", true);
                                          editor.commit();
                                          postFailOnceCounter = 0;
                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                          Path path = new Path();
                                          path.moveTo(0, 68);
                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                          try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                            @Override
                                            public void onCompleted(GestureDescription gestureDescription) {
                                              super.onCompleted(gestureDescription);
                                              try {
                                                /** press hamburger menu **/
                                                Thread.sleep(2000);
                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                Path path = new Path();
                                                path.moveTo(0, 68);
                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                  @Override
                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                    super.onCompleted(gestureDescription);
                                                    try {
                                                      Thread.sleep(2000);
                                                      /** press settings **/
                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                      Path path = new Path();
                                                      path.moveTo(0, 582);
                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                      try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                        @Override
                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                          super.onCompleted(gestureDescription);
                                                          try {
                                                            Thread.sleep(2000);
                                                            /** press sign out **/
                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                            Path path = new Path();
                                                            path.moveTo(0, 773);
                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                            try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                              @Override
                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                super.onCompleted(gestureDescription);
                                                                try {
                                                                  Thread.sleep(1000);
                                                                  editor.putBoolean("isCalled", false);
                                                                  editor.commit();
                                                                } catch (Exception e) {

                                                                }
                                                              }
                                                            }, null);



                                                          } catch (Exception e) {

                                                          }
                                                        }
                                                      }, null);



                                                    } catch (Exception e) {

                                                    }
                                                  }
                                                }, null);



                                              } catch (Exception e) {

                                              }
                                            }
                                          }, null);

                                        }
                                        else if (response.code() == 200) {
                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        }
                                      }

                                      @Override
                                      public void onFailure(Call<UploadObject> call, Throwable t) {

                                      }
                                    });
                                  }

                                }
                                else if (getRootNode.getChild(1).getText().toString().contains("Oops! Something")) {
                                  if (postFailOnceCounter == 0) {
                                    postFailOnceCounter += 1;

                                    JSONObject reloadFailedObj = new JSONObject();
                                    reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                    reloadFailedObj.put("PhoneNumber", phoneNum);
                                    reloadFailedObj.put("action", "Result");
                                    reloadFailedObj.put("TranID", tranID);
                                    reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                    Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                    reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                      @Override
                                      public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                        if (response.code() == 400) {
                                          Log.i(TAG, "onResponse: error msg 400");
                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                        }
                                        else if (response.code() == 401) {
                                          Log.i(TAG, "onResponse: error msg 401");
                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        }
                                        else if (response.code() == 200) {
                                          telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                          editor.putBoolean("isFailed", true);
                                          editor.commit();
                                          postFailOnceCounter = 0;

                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                          try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                          Path path = new Path();
                                          path.moveTo(0, 68);
                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                            @Override
                                            public void onCompleted(GestureDescription gestureDescription) {
                                              super.onCompleted(gestureDescription);
                                              try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                              Path path = new Path();
                                              path.moveTo(26, 351);
                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                @Override
                                                public void onCompleted(GestureDescription gestureDescription) {
                                                  super.onCompleted(gestureDescription);

                                                  try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                  /** navigate to PIN tab again **/
                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                  Path path = new Path();
                                                  path.moveTo(225, 177);
                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                    @Override
                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                      super.onCompleted(gestureDescription);

                                                      try {Thread.sleep(1000);} catch (Exception e) {}
                                                    }}, null);
                                                }
                                              }, null);
                                            }
                                          }, null);

                                          // attempt to re-do
//                                                try {Thread.sleep(1000);}catch (Exception e) {}
//                                                editor.putBoolean("isFailed", true);
//                                                editor.commit();

                                        }
                                      }

                                      @Override
                                      public void onFailure(Call<UploadObject> call, Throwable t) {

                                      }
                                    });
                                  }

                                }
                                else if (getRootNode.getChild(1).getText().toString().contains("was redeemed")) {
                                  if (postFailOnceCounter == 0) {
                                    postFailOnceCounter += 1;

                                    JSONObject reloadFailedObj = new JSONObject();
                                    reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                    reloadFailedObj.put("PhoneNumber", phoneNum);
                                    reloadFailedObj.put("action", "Result");
                                    reloadFailedObj.put("TranID", tranID);
                                    reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                    Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                    reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                      @Override
                                      public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                        if (response.code() == 400) {
                                          Log.i(TAG, "onResponse: error msg 400");
                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                        }
                                        else if (response.code() == 401) {
                                          Log.i(TAG, "onResponse: error msg 401");
                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        }
                                        else if (response.code() == 200) {
                                          telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                          editor.putBoolean("isFailed", true);
                                          editor.commit();
                                          postFailOnceCounter = 0;

                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                          try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                          Path path = new Path();
                                          path.moveTo(0, 68);
                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                            @Override
                                            public void onCompleted(GestureDescription gestureDescription) {
                                              super.onCompleted(gestureDescription);
                                              try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                              Path path = new Path();
                                              path.moveTo(26, 351);
                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                @Override
                                                public void onCompleted(GestureDescription gestureDescription) {
                                                  super.onCompleted(gestureDescription);

                                                  try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                  /** navigate to PIN tab again **/
                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                  Path path = new Path();
                                                  path.moveTo(225, 177);
                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                    @Override
                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                      super.onCompleted(gestureDescription);

                                                      try {Thread.sleep(1000);} catch (Exception e) {}
                                                    }}, null);
                                                }
                                              }, null);
                                            }
                                          }, null);

                                          // attempt to re-do
//                                                try {Thread.sleep(1000);}catch (Exception e) {}
//                                                editor.putBoolean("isFailed", true);
//                                                editor.commit();

                                        }
                                      }

                                      @Override
                                      public void onFailure(Call<UploadObject> call, Throwable t) {

                                      }
                                    });
                                  }

                                }
                              } catch (Exception e) {

                              }

                            }
                          } catch (Exception e) {

                          }
                        }
                      } catch (Exception e) {

                      }
                    }

                    @Override
                    public void onFailure(Call<UploadObject> call, Throwable t) {

                    }
                  });
                } catch (Exception e) {}
              }
            }
            else if (isCalled && isFailed) {
              if (waitingToResolve) {
                Log.i(TAG, "resolving... (first time call)");
              } else if (!waitingToResolve) {
                try {
                  isSuccessCounter = 0;
                  isFailedCounter = 0;

                  Thread.sleep(1000);
                  JSONObject reloadPINActionObj = new JSONObject();
                  reloadPINActionObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                  reloadPINActionObj.put("PhoneNumber", phoneNum);
                  reloadPINActionObj.put("action", "ReloadPIN");

                  Call<UploadObject> reloadPINActionCall = uploadTelcoInterface.uploadTelco(reloadPINActionObj);
                  reloadPINActionCall.enqueue(new Callback<UploadObject>() {
                    @Override
                    public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                      try {
                        postMainOnceCounter = 0;
                        if (response.body() == null) {
                          Log.i(TAG, "Waiting for reload PIN...");

                          reloadPINHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {

                              call.clone().enqueue(new Callback<UploadObject>() {
                                @Override
                                public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                  if (response.body() != null) {
                                    try {
                                      JSONObject reloadPINActionResponseObj = new JSONObject(new Gson().toJson(response.body()));
                                      if (reloadPINActionResponseObj.get("status").equals("SUCCESS")) {
                                        reloadPINHandler.removeCallbacksAndMessages(null);

                                        String reloadPINString = reloadPINActionResponseObj.get("ReloadPIN").toString();
                                        tranID = reloadPINActionResponseObj.get("TranID").toString();

                                        saveReloadPIN(phoneNum, "Digi", tranID, reloadPINString);

                                        Bundle reloadPINBundle = new Bundle();
                                        reloadPINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reloadPINString);
                                        try {
                                          source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, reloadPINBundle);
                                          Thread.sleep(2000);
                                          source.getParent().getParent().getChild(4).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                          Thread.sleep(2000);

                                          getRootNode = getRootInActiveWindow();
                                          if (getRootNode.getChild(1).getText().toString().contains("Invalid")) {
                                            if (postFailOnceCounter == 0) {
                                              postFailOnceCounter += 1;
                                              JSONObject reloadFailedObj = new JSONObject();
                                              reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                              reloadFailedObj.put("PhoneNumber", phoneNum);
                                              reloadFailedObj.put("action", "Result");
                                              reloadFailedObj.put("TranID", tranID);
                                              reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                              Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                              reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                                @Override
                                                public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                  if (response.code() == 400) {
                                                    Log.i(TAG, "onResponse: error msg 400");
                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                                  }
                                                  else if (response.code() == 401) {
                                                    Log.i(TAG, "onResponse: error msg 401");
                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                  }
                                                  else if (response.code() == 200) {
                                                    telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                                    editor.putBoolean("isFailed", true);
                                                    editor.commit();
                                                    postFailOnceCounter = 0;

                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                    try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                    Path path = new Path();
                                                    path.moveTo(0, 68);
                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                      @Override
                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                        super.onCompleted(gestureDescription);
                                                        try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                        Path path = new Path();
                                                        path.moveTo(26, 351);
                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                          @Override
                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                            super.onCompleted(gestureDescription);

                                                            try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                            /** navigate to PIN tab again **/
                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                            Path path = new Path();
                                                            path.moveTo(225, 177);
                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                              @Override
                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                super.onCompleted(gestureDescription);

                                                                try {Thread.sleep(1000);} catch (Exception e) {}
                                                              }}, null);
                                                          }
                                                        }, null);
                                                      }
                                                    }, null);

                                                    // attempt to re-do
//                                                try {Thread.sleep(1000);}catch (Exception e) {}
//                                                editor.putBoolean("isFailed", true);
//                                                editor.commit();

                                                  }
                                                }

                                                @Override
                                                public void onFailure(Call<UploadObject> call, Throwable t) {

                                                }
                                              });
                                            }
                                          }
                                          else if (getRootNode.getChild(1).getText().toString().contains("You have reached the max allowed number of times")) {
                                            if (postFailOnceCounter == 0) {
                                              postFailOnceCounter += 1;

                                              JSONObject blockedActionObj = new JSONObject();
                                              blockedActionObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                              blockedActionObj.put("PhoneNumber", phoneNum);
                                              blockedActionObj.put("action", "Result");
                                              blockedActionObj.put("TranID", tranID);
                                              blockedActionObj.put("message", getRootNode.getChild(1).getText().toString());
                                              blockedActionObj.put("phoneStatus", "BLOCKED");

                                              Call<UploadObject> blockedActionCall = uploadTelcoInterface.uploadTelco(blockedActionObj);
                                              blockedActionCall.enqueue(new Callback<UploadObject>() {
                                                @Override
                                                public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                  if (response.code() == 400) {
                                                    Log.i(TAG, "onResponse: posted and this exist 400");
                                                  }
                                                  else if (response.code() == 401) {
                                                    telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                                    editor.putBoolean("isFailed", true);
                                                    editor.commit();
                                                    postFailOnceCounter = 0;
                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                    Path path = new Path();
                                                    path.moveTo(0, 68);
                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                    try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                      @Override
                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                        super.onCompleted(gestureDescription);
                                                        try {
                                                          /** press hamburger menu **/
                                                          Thread.sleep(2000);
                                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                          Path path = new Path();
                                                          path.moveTo(0, 68);
                                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                          try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                            @Override
                                                            public void onCompleted(GestureDescription gestureDescription) {
                                                              super.onCompleted(gestureDescription);
                                                              try {
                                                                Thread.sleep(2000);
                                                                /** press settings **/
                                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                Path path = new Path();
                                                                path.moveTo(0, 582);
                                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                  @Override
                                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                                    super.onCompleted(gestureDescription);
                                                                    try {
                                                                      Thread.sleep(2000);
                                                                      /** press sign out **/
                                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                                      Path path = new Path();
                                                                      path.moveTo(0, 773);
                                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                                      try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                                        @Override
                                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                                          super.onCompleted(gestureDescription);
                                                                          try {
                                                                            Thread.sleep(1000);
                                                                            editor.putBoolean("isCalled", false);
                                                                            editor.commit();
                                                                          } catch (Exception e) {

                                                                          }
                                                                        }
                                                                      }, null);



                                                                    } catch (Exception e) {

                                                                    }
                                                                  }
                                                                }, null);



                                                              } catch (Exception e) {

                                                              }
                                                            }
                                                          }, null);



                                                        } catch (Exception e) {

                                                        }
                                                      }
                                                    }, null);

                                                  }
                                                  else if (response.code() == 200) {
                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                  }
                                                }

                                                @Override
                                                public void onFailure(Call<UploadObject> call, Throwable t) {

                                                }
                                              });
                                            }

                                          }
                                          else if (getRootNode.getChild(1).getText().toString().contains("Oops! Something")) {
                                            if (postFailOnceCounter == 0) {
                                              postFailOnceCounter += 1;

                                              JSONObject reloadFailedObj = new JSONObject();
                                              reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                              reloadFailedObj.put("PhoneNumber", phoneNum);
                                              reloadFailedObj.put("action", "Result");
                                              reloadFailedObj.put("TranID", tranID);
                                              reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                              Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                              reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                                @Override
                                                public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                  if (response.code() == 400) {
                                                    Log.i(TAG, "onResponse: error msg 400");
                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                                  }
                                                  else if (response.code() == 401) {
                                                    Log.i(TAG, "onResponse: error msg 401");
                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                  }
                                                  else if (response.code() == 200) {
                                                    telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                                    editor.putBoolean("isFailed", true);
                                                    editor.commit();
                                                    postFailOnceCounter = 0;

                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                    try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                    Path path = new Path();
                                                    path.moveTo(0, 68);
                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                      @Override
                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                        super.onCompleted(gestureDescription);
                                                        try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                        Path path = new Path();
                                                        path.moveTo(26, 351);
                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                          @Override
                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                            super.onCompleted(gestureDescription);

                                                            try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                            /** navigate to PIN tab again **/
                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                            Path path = new Path();
                                                            path.moveTo(225, 177);
                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                              @Override
                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                super.onCompleted(gestureDescription);

                                                                try {Thread.sleep(1000);} catch (Exception e) {}
                                                              }}, null);
                                                          }
                                                        }, null);
                                                      }
                                                    }, null);

                                                    // attempt to re-do
//                                                try {Thread.sleep(1000);}catch (Exception e) {}
//                                                editor.putBoolean("isFailed", true);
//                                                editor.commit();

                                                  }
                                                }

                                                @Override
                                                public void onFailure(Call<UploadObject> call, Throwable t) {

                                                }
                                              });
                                            }

                                          }
                                          else if (getRootNode.getChild(1).getText().toString().contains("was redeemed")) {
                                            if (postFailOnceCounter == 0) {
                                              postFailOnceCounter += 1;

                                              JSONObject reloadFailedObj = new JSONObject();
                                              reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                              reloadFailedObj.put("PhoneNumber", phoneNum);
                                              reloadFailedObj.put("action", "Result");
                                              reloadFailedObj.put("TranID", tranID);
                                              reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                              Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                              reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                                @Override
                                                public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                                  if (response.code() == 400) {
                                                    Log.i(TAG, "onResponse: error msg 400");
                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                                  }
                                                  else if (response.code() == 401) {
                                                    Log.i(TAG, "onResponse: error msg 401");
                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                  }
                                                  else if (response.code() == 200) {
                                                    telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                                    editor.putBoolean("isFailed", true);
                                                    editor.commit();
                                                    postFailOnceCounter = 0;

                                                    getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                    try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                    Path path = new Path();
                                                    path.moveTo(0, 68);
                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                      @Override
                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                        super.onCompleted(gestureDescription);
                                                        try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                        Path path = new Path();
                                                        path.moveTo(26, 351);
                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                          @Override
                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                            super.onCompleted(gestureDescription);

                                                            try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                            /** navigate to PIN tab again **/
                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                            Path path = new Path();
                                                            path.moveTo(225, 177);
                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                              @Override
                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                super.onCompleted(gestureDescription);

                                                                try {Thread.sleep(1000);} catch (Exception e) {}
                                                              }}, null);
                                                          }
                                                        }, null);
                                                      }
                                                    }, null);

                                                    // attempt to re-do
//                                                try {Thread.sleep(1000);}catch (Exception e) {}
//                                                editor.putBoolean("isFailed", true);
//                                                editor.commit();

                                                  }
                                                }

                                                @Override
                                                public void onFailure(Call<UploadObject> call, Throwable t) {

                                                }
                                              });
                                            }

                                          }
                                        } catch (Exception e) {

                                        }

                                      }
                                    } catch (Exception e) {

                                    }
                                  }
                                }

                                @Override
                                public void onFailure(Call<UploadObject> call, Throwable t) {

                                }
                              });

                              reloadPINHandler.postDelayed(this, 5000);
                            }
                          }, 5000);
                        }
                        else {
                          try {
                            JSONObject reloadPINActionResponseObj = new JSONObject(new Gson().toJson(response.body()));
                            if (reloadPINActionResponseObj.get("status").equals("SUCCESS")) {
                              reloadPINHandler.removeCallbacksAndMessages(null);

                              String reloadPINString = reloadPINActionResponseObj.get("ReloadPIN").toString();
                              tranID = reloadPINActionResponseObj.get("TranID").toString();

                              saveReloadPIN(phoneNum, "Digi", tranID, reloadPINString);

                              Bundle reloadPINBundle = new Bundle();
                              reloadPINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reloadPINString);
                              try {
                                source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, reloadPINBundle);
                                Thread.sleep(2000);
                                source.getParent().getParent().getChild(4).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                Thread.sleep(2000);

                                getRootNode = getRootInActiveWindow();
                                if (getRootNode.getChild(1).getText().toString().contains("Invalid")) {
                                  if (postFailOnceCounter == 0) {
                                    postFailOnceCounter += 1;
                                    JSONObject reloadFailedObj = new JSONObject();
                                    reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                    reloadFailedObj.put("PhoneNumber", phoneNum);
                                    reloadFailedObj.put("action", "Result");
                                    reloadFailedObj.put("TranID", tranID);
                                    reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                    Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                    reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                      @Override
                                      public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                        if (response.code() == 400) {
                                          Log.i(TAG, "onResponse: error msg 400");
                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                        }
                                        else if (response.code() == 401) {
                                          Log.i(TAG, "onResponse: error msg 401");
                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        }
                                        else if (response.code() == 200) {
                                          telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                          editor.putBoolean("isFailed", true);
                                          editor.commit();
                                          postFailOnceCounter = 0;

                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                          try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                          Path path = new Path();
                                          path.moveTo(0, 68);
                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                            @Override
                                            public void onCompleted(GestureDescription gestureDescription) {
                                              super.onCompleted(gestureDescription);
                                              try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                              Path path = new Path();
                                              path.moveTo(26, 351);
                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                @Override
                                                public void onCompleted(GestureDescription gestureDescription) {
                                                  super.onCompleted(gestureDescription);

                                                  try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                  /** navigate to PIN tab again **/
                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                  Path path = new Path();
                                                  path.moveTo(225, 177);
                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                    @Override
                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                      super.onCompleted(gestureDescription);

                                                      try {Thread.sleep(1000);} catch (Exception e) {}
                                                    }}, null);
                                                }
                                              }, null);
                                            }
                                          }, null);

                                          // attempt to re-do
//                                                try {Thread.sleep(1000);}catch (Exception e) {}
//                                                editor.putBoolean("isFailed", true);
//                                                editor.commit();

                                        }
                                      }

                                      @Override
                                      public void onFailure(Call<UploadObject> call, Throwable t) {

                                      }
                                    });
                                  }
                                }
                                else if (getRootNode.getChild(1).getText().toString().contains("You have reached the max allowed number of times")) {
                                  if (postFailOnceCounter == 0) {
                                    postFailOnceCounter += 1;

                                    JSONObject blockedActionObj = new JSONObject();
                                    blockedActionObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                    blockedActionObj.put("PhoneNumber", phoneNum);
                                    blockedActionObj.put("action", "Result");
                                    blockedActionObj.put("TranID", tranID);
                                    blockedActionObj.put("message", getRootNode.getChild(1).getText().toString());
                                    blockedActionObj.put("phoneStatus", "BLOCKED");

                                    Call<UploadObject> blockedActionCall = uploadTelcoInterface.uploadTelco(blockedActionObj);
                                    blockedActionCall.enqueue(new Callback<UploadObject>() {
                                      @Override
                                      public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                        if (response.code() == 400) {
                                          Log.i(TAG, "onResponse: posted and this exist 400");
                                        }
                                        else if (response.code() == 401) {
                                          telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                          editor.putBoolean("isFailed", true);
                                          editor.commit();
                                          postFailOnceCounter = 0;
                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                          Path path = new Path();
                                          path.moveTo(0, 68);
                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                          try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                            @Override
                                            public void onCompleted(GestureDescription gestureDescription) {
                                              super.onCompleted(gestureDescription);
                                              try {
                                                /** press hamburger menu **/
                                                Thread.sleep(2000);
                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                Path path = new Path();
                                                path.moveTo(0, 68);
                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                  @Override
                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                    super.onCompleted(gestureDescription);
                                                    try {
                                                      Thread.sleep(2000);
                                                      /** press settings **/
                                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                      Path path = new Path();
                                                      path.moveTo(0, 582);
                                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                      try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                        @Override
                                                        public void onCompleted(GestureDescription gestureDescription) {
                                                          super.onCompleted(gestureDescription);
                                                          try {
                                                            Thread.sleep(2000);
                                                            /** press sign out **/
                                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                            Path path = new Path();
                                                            path.moveTo(0, 773);
                                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                            try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                              @Override
                                                              public void onCompleted(GestureDescription gestureDescription) {
                                                                super.onCompleted(gestureDescription);
                                                                try {
                                                                  Thread.sleep(1000);
                                                                  editor.putBoolean("isCalled", false);
                                                                  editor.commit();
                                                                } catch (Exception e) {

                                                                }
                                                              }
                                                            }, null);



                                                          } catch (Exception e) {

                                                          }
                                                        }
                                                      }, null);



                                                    } catch (Exception e) {

                                                    }
                                                  }
                                                }, null);



                                              } catch (Exception e) {

                                              }
                                            }
                                          }, null);

                                        }
                                        else if (response.code() == 200) {
                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        }
                                      }

                                      @Override
                                      public void onFailure(Call<UploadObject> call, Throwable t) {

                                      }
                                    });
                                  }

                                }
                                else if (getRootNode.getChild(1).getText().toString().contains("Oops! Something")) {
                                  if (postFailOnceCounter == 0) {
                                    postFailOnceCounter += 1;

                                    JSONObject reloadFailedObj = new JSONObject();
                                    reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                    reloadFailedObj.put("PhoneNumber", phoneNum);
                                    reloadFailedObj.put("action", "Result");
                                    reloadFailedObj.put("TranID", tranID);
                                    reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                    Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                    reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                      @Override
                                      public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                        if (response.code() == 400) {
                                          Log.i(TAG, "onResponse: error msg 400");
                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                        }
                                        else if (response.code() == 401) {
                                          Log.i(TAG, "onResponse: error msg 401");
                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        }
                                        else if (response.code() == 200) {
                                          telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                          editor.putBoolean("isFailed", true);
                                          editor.commit();
                                          postFailOnceCounter = 0;

                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                          try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                          Path path = new Path();
                                          path.moveTo(0, 68);
                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                            @Override
                                            public void onCompleted(GestureDescription gestureDescription) {
                                              super.onCompleted(gestureDescription);
                                              try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                              Path path = new Path();
                                              path.moveTo(26, 351);
                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                @Override
                                                public void onCompleted(GestureDescription gestureDescription) {
                                                  super.onCompleted(gestureDescription);

                                                  try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                  /** navigate to PIN tab again **/
                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                  Path path = new Path();
                                                  path.moveTo(225, 177);
                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                    @Override
                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                      super.onCompleted(gestureDescription);

                                                      try {Thread.sleep(1000);} catch (Exception e) {}
                                                    }}, null);
                                                }
                                              }, null);
                                            }
                                          }, null);

                                          // attempt to re-do
//                                                try {Thread.sleep(1000);}catch (Exception e) {}
//                                                editor.putBoolean("isFailed", true);
//                                                editor.commit();

                                        }
                                      }

                                      @Override
                                      public void onFailure(Call<UploadObject> call, Throwable t) {

                                      }
                                    });
                                  }

                                }
                                else if (getRootNode.getChild(1).getText().toString().contains("was redeemed")) {
                                  if (postFailOnceCounter == 0) {
                                    postFailOnceCounter += 1;

                                    JSONObject reloadFailedObj = new JSONObject();
                                    reloadFailedObj.put("SecretKey", "f0b386405385851d939fc142cebc0985180e3a9f");
                                    reloadFailedObj.put("PhoneNumber", phoneNum);
                                    reloadFailedObj.put("action", "Result");
                                    reloadFailedObj.put("TranID", tranID);
                                    reloadFailedObj.put("message", getRootNode.getChild(1).getText().toString());

                                    Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                    reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                      @Override
                                      public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                        if (response.code() == 400) {
                                          Log.i(TAG, "onResponse: error msg 400");
                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                        }
                                        else if (response.code() == 401) {
                                          Log.i(TAG, "onResponse: error msg 401");
                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        }
                                        else if (response.code() == 200) {
                                          telcoDatabaseHandler.updateIsSuccess(false, phoneNum, "Digi", tranID);
                                          editor.putBoolean("isFailed", true);
                                          editor.commit();
                                          postFailOnceCounter = 0;

                                          getRootNode.getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                          try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                          Path path = new Path();
                                          path.moveTo(0, 68);
                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                            @Override
                                            public void onCompleted(GestureDescription gestureDescription) {
                                              super.onCompleted(gestureDescription);
                                              try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                              Path path = new Path();
                                              path.moveTo(26, 351);
                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                @Override
                                                public void onCompleted(GestureDescription gestureDescription) {
                                                  super.onCompleted(gestureDescription);

                                                  try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                  /** navigate to PIN tab again **/
                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                  Path path = new Path();
                                                  path.moveTo(225, 177);
                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                    @Override
                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                      super.onCompleted(gestureDescription);

                                                      try {Thread.sleep(1000);} catch (Exception e) {}
                                                    }}, null);
                                                }
                                              }, null);
                                            }
                                          }, null);

                                          // attempt to re-do
//                                                try {Thread.sleep(1000);}catch (Exception e) {}
//                                                editor.putBoolean("isFailed", true);
//                                                editor.commit();

                                        }
                                      }

                                      @Override
                                      public void onFailure(Call<UploadObject> call, Throwable t) {

                                      }
                                    });
                                  }

                                }
                              } catch (Exception e) {

                              }

                            }
                          } catch (Exception e) {

                          }
                        }
                      } catch (Exception e) {

                      }
                    }

                    @Override
                    public void onFailure(Call<UploadObject> call, Throwable t) {

                    }
                  });
                } catch (Exception e) {}
              }
            }
          } catch (Exception e) {}

        }
      }

      // may be only able to be detected outside, // Reload successful! Please wait a few minutes for your reload and status to be reflected
      try {
        getRootNode = getRootInActiveWindow();
        Log.i(TAG, "getViewableTransactionData: " + getRootNode.getChild(0).getChild(0).getChild(16).getChild(0));
        if (getRootNode.getChild(0).getChild(0).getChild(16).getChild(0).getClassName().equals("android.widget.TextView")) {
          Log.i(TAG, "reach 1");
          try {
            Thread.sleep(2000);
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            Path path = new Path();
            path.moveTo(0, 1420);
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
              @Override
              public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.i(TAG, "reach 2");
                editor.putBoolean("isFailed", false);
                editor.putBoolean("isCalled", true);
                editor.commit();
              }
            }, null);
          } catch (Exception e) {}
        }
      } catch (Exception e ) {}

    } catch (Exception e) {

    }
  }

  @Override
  public void onInterrupt() {

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
}
