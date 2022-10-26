package com.example.myapplication;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.RequiresApi;

import com.example.myapplication.SQLiteDatabaseHandler.TelcoDatabaseHandler;
import com.example.myapplication.interfaces.UploadTelcoInterface;
import com.example.myapplication.models.Error400POJO;
import com.example.myapplication.models.Error401POJO;
import com.example.myapplication.models.UploadObject;
import com.example.myapplication.models.databaseModels.TelcoDatabaseModel;
import com.example.myapplication.utils.ApiUtils;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UMobileService extends AccessibilityService {

  Context context;
  private static final String TAG = "access";
  private static final DecimalFormat df = new DecimalFormat("0.00");
  private DateFormat TWELVE_TF = new SimpleDateFormat("hh:mma");
  private DateFormat TWENTY_FOUR_TF = new SimpleDateFormat("HH:mm");
  private DateFormat DATE_TF = new SimpleDateFormat("yyyy-MM-dd");

  TelcoDatabaseHandler telcoDatabaseHandler;
  TelcoDatabaseModel telcoDatabaseModel;

  private static Runnable myRunnable;
  Handler otpHandler = new Handler();
  Handler phoneNumberHandler = new Handler();
  Handler OTPHandler2;
  Handler reloadPINHandler = new Handler();

  String otp = "";
  String balance = "";
  String tranID = "";

  public int ctr = 0;
  public int callApiOnceCtr = 0;
  public int wrongOTPSendCtr = 0;
  public int performOnce = 0;
  public int isSuccessCounter = 0;

  boolean isCalled, isFailed, isChangeNum;

  public String currentFetchedNumber;

  AccessibilityNodeInfo getNodes;
  AccessibilityNodeInfo getInnerNodes;

  UploadTelcoInterface uploadTelcoInterface;

  SharedPreferences preferences;
  SharedPreferences.Editor editor;

  @Override
  protected void onServiceConnected() {
    super.onServiceConnected();
    Log.i(TAG, "onServiceConnected: umobile connected!");

    if (ctr == 0) {
      Intent launchIntent1 = getPackageManager().getLaunchIntentForPackage("com.omesti.myumobile");
      if (launchIntent1 != null) {
        startActivity(launchIntent1);
      }
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {

    try {
      context = MyApplication.getAppContext();
      preferences = PreferenceManager.getDefaultSharedPreferences(context);
      telcoDatabaseHandler = new TelcoDatabaseHandler(context);
      editor = preferences.edit();
      getViewableTransactionData(event);

    } catch (Exception e) {
      Log.i(TAG, "except: " + e);
    }
  }

  @Override
  public void onInterrupt() {

  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  public void getViewableTransactionData(AccessibilityEvent event) {
    TWELVE_TF.setTimeZone(TimeZone.getTimeZone("GMT+8"));
    DATE_TF.setTimeZone(TimeZone.getTimeZone("GMT+8"));

    if (ctr == 0) {
      try {
        ctr += 1; // reset to 0 elsewhere
//        startActivity(new Intent(Settings.ACTION_SETTINGS).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        performGlobalAction(GLOBAL_ACTION_RECENTS);
        Thread.sleep(2000);
        Intent launchIntent2 = getPackageManager().getLaunchIntentForPackage("com.omesti.myumobile");
        if (launchIntent2 != null) {
          startActivity(launchIntent2);
          Thread.sleep(2000);
        }
      } catch (Exception e) {

      }
    }

    /** things to enhance: OTP retries, first time login, and maybe occassional popup handling **/
    try {
      AccessibilityNodeInfo source = event.getSource();
      uploadTelcoInterface = ApiUtils.getUploadTelcoInterface();
      // landing page
      List<AccessibilityNodeInfo> welcomeAboard = source.findAccessibilityNodeInfosByText("Welcome Aboard");
      for (AccessibilityNodeInfo welcomeAboardNode: welcomeAboard) {
        try {
          if (callApiOnceCtr == 0) {
            callApiOnceCtr += 1;

            JSONObject postLoginJsonObj = new JSONObject();
            postLoginJsonObj.put("SecretKey", "dde0823be88140da54210bb55939245e6937e680");
            postLoginJsonObj.put("PhoneNumber", "");

            Call<UploadObject> call = uploadTelcoInterface.uploadTelco(postLoginJsonObj);
            call.enqueue(new Callback<UploadObject>() {
              @Override
              public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                try {
                  if (response.body() == null) {
                    Log.i(TAG, "Waiting for phone num...");

                    phoneNumberHandler.postDelayed(new Runnable() {
                      @Override
                      public void run() {
                        //call function
                        call.clone().enqueue(new Callback<UploadObject>() {
                          @Override
                          public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                            try {
                              if (response.body() == null) {
                                Log.i(TAG, "Waiting for phone num...");
                              }
                              else {
                                JSONObject loginObj = new JSONObject(new Gson().toJson(response.body()));
                                if (loginObj.get("status").equals("SUCCESS")) {
                                  phoneNumberHandler.removeCallbacksAndMessages(null);
                                  editor.putString("phoneNum", loginObj.get("PhoneNumber").toString());
                                  editor.commit();
                                  Thread.sleep(1000);
                                  welcomeAboardNode.getParent().getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
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

                        phoneNumberHandler.postDelayed(this, 5000);
                      }
                    }, 5000);
                  } else {
                    JSONObject loginObj = new JSONObject(new Gson().toJson(response.body()));

                    if (loginObj.get("status").equals("SUCCESS")) {
                      phoneNumberHandler.removeCallbacksAndMessages(null);
                      editor.putString("phoneNum", loginObj.get("PhoneNumber").toString());
                      editor.commit();
                      Thread.sleep(1000);

                      welcomeAboardNode.getParent().getChild(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);
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
          }

        } catch (Exception e) {
          e.printStackTrace();
        }
      }

      /** first time login (enhance this) **/
      // first time
      if (source.getClassName().equals("android.widget.EditText")) {
        // First time open app, getting started by logging in to number below
        String MobileNoInput = preferences.getString("phoneNum", "");

        Bundle MobileNoBundle = new Bundle();
        MobileNoBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, MobileNoInput);
        if (source.getText().toString().contains("0181234567")) {
          source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, MobileNoBundle);

          // get pin
          try {
            JSONObject otpActionObj = new JSONObject();
            otpActionObj.put("SecretKey", "dde0823be88140da54210bb55939245e6937e680");
            otpActionObj.put("PhoneNumber", MobileNoInput);
            otpActionObj.put("action", "OTP");

            Call<UploadObject> otpActionCall = uploadTelcoInterface.uploadTelco(otpActionObj);
            otpActionCall.enqueue(new Callback<UploadObject>() {
              @Override
              public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                if (response.body() != null) {
                  try {
                    JSONObject otpActionResponseObj = new JSONObject(new Gson().toJson(response.body()));
                    if (otpActionResponseObj.get("status").equals("SUCCESS")) {
                      otp = otpActionResponseObj.get("OTP").toString();

                      String PINInput = otpActionResponseObj.get("OTP").toString();
                      Bundle PINBundle = new Bundle();

                      PINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, PINInput);
                      source.getParent().getChild(7).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, PINBundle);
                      otpHandler.removeCallbacksAndMessages(null);

                    }
                  } catch (Exception e) {

                  }
                }
                else if (response.body() == null){
                  try {
                    otpHandler.postDelayed(new Runnable() {
                      @Override
                      public void run() {
                        call.clone().enqueue(new Callback<UploadObject>() {
                          @Override
                          public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                            try {
                              if (response.body() == null) {
                                Log.i(TAG, "Waiting for OTP...");
                              } else {
                                JSONObject otpActionResponseObj;
                                otpActionResponseObj = new JSONObject(new Gson().toJson(response.body()));
                                if (otpActionResponseObj.get("status").equals("SUCCESS")) {

                                  String PINInput = otpActionResponseObj.get("OTP").toString();
                                  Bundle PINBundle = new Bundle();

                                  PINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, PINInput);
                                  source.getParent().getChild(7).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, PINBundle);
                                  otpHandler.removeCallbacksAndMessages(null);

                                  if (wrongOTPSendCtr == 0) {
                                    wrongOTPSendCtr += 1;
                                    OTPHandler2 = new Handler(Looper.getMainLooper());
                                    OTPHandler2.postDelayed(new Runnable() {
                                      @Override
                                      public void run() {

                                      }
                                    }, 2000);

                                  }
                                }
                              }
                            } catch (Exception e ){

                            }
                          }

                          @Override
                          public void onFailure(Call<UploadObject> call, Throwable t) {

                          }
                        });

                        otpHandler.postDelayed(this, 5000);
                      }
                    },5000);
                  } catch (Exception e) {
                    e.printStackTrace();
                  }

                }
              }

              @Override
              public void onFailure(Call<UploadObject> call, Throwable t) {

              }
            });

          } catch (Exception e) {}

          // if invalid otp:


        }

      }
      /** subsequent logins **/
      // this sp is true when: logged out due to blocked(might not?) / logged out due to balance limit reached.
      isChangeNum = preferences.getBoolean("isChangeNum", false);
      if (!isChangeNum) {
        if (source.getClassName().equals("android.widget.FrameLayout")) {
          if (source.getChild(3).getChild(5).getText().toString().equals("Enter your 6 digits PIN") && source.getChild(3).getChild(5) != null) {
            String MobileNoInput = preferences.getString("phoneNum", "");

            // get pin
            try {
              JSONObject otpActionObj = new JSONObject();
              otpActionObj.put("SecretKey", "dde0823be88140da54210bb55939245e6937e680");
              otpActionObj.put("PhoneNumber", MobileNoInput);
              otpActionObj.put("action", "OTP");

              Call<UploadObject> otpActionCall = uploadTelcoInterface.uploadTelco(otpActionObj);
              otpActionCall.enqueue(new Callback<UploadObject>() {
                @Override
                public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                  if (response.body() != null) {
                    try {
                      JSONObject otpActionResponseObj = new JSONObject(new Gson().toJson(response.body()));
                      if (otpActionResponseObj.get("status").equals("SUCCESS")) {
                        otp = otpActionResponseObj.get("OTP").toString();

                        String PINInput = otpActionResponseObj.get("OTP").toString();
                        Bundle PINBundle = new Bundle();

                        PINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, PINInput);
                        source.getChild(3).getChild(5).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, PINBundle);
                        otpHandler.removeCallbacksAndMessages(null);

                      }
                    } catch (Exception e) {

                    }
                  }
                  else if (response.body() == null){
                    try {
                      otpHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                          call.clone().enqueue(new Callback<UploadObject>() {
                            @Override
                            public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                              try {
                                if (response.body() == null) {
                                  Log.i(TAG, "Waiting for OTP...");
                                } else {
                                  JSONObject otpActionResponseObj;
                                  otpActionResponseObj = new JSONObject(new Gson().toJson(response.body()));
                                  if (otpActionResponseObj.get("status").equals("SUCCESS")) {

                                    String PINInput = otpActionResponseObj.get("OTP").toString();
                                    Bundle PINBundle = new Bundle();

                                    PINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, PINInput);
                                    source.getChild(3).getChild(5).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, PINBundle);
                                    otpHandler.removeCallbacksAndMessages(null);

                                    if (wrongOTPSendCtr == 0) {
                                      wrongOTPSendCtr += 1;
                                      OTPHandler2 = new Handler(Looper.getMainLooper());
                                      OTPHandler2.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {

                                        }
                                      }, 2000);

                                    }
                                  }
                                }
                              } catch (Exception e ){

                              }
                            }

                            @Override
                            public void onFailure(Call<UploadObject> call, Throwable t) {

                            }
                          });

                          otpHandler.postDelayed(this, 5000);
                        }
                      },5000);
                    } catch (Exception e) {
                      e.printStackTrace();
                    }

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
      else if (isChangeNum) {
        // click "change number" button, and login. Then set sp to false
        if (source.getClassName().equals("android.widget.FrameLayout")) {
          source.getChild(3).getChild(3).performAction(AccessibilityNodeInfo.ACTION_CLICK);
          editor.putBoolean("isChangeNum", false);
          editor.commit();
        }
      }

      /** welcome to umobile app **/
//      getNodes = getRootInActiveWindow();
//      try {
//        if (getNodes.getChild(1) != null && getNodes.getChild(1).getText().toString().contains("Welcome to MyUMobile App")) {
//          getNodes.getChild(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//        }
//      } catch (Exception e) {}

      /** for main page operations**/
      // main page, get inactive account dialog
      getNodes = getRootInActiveWindow();
      try {
        if (getNodes.getChild(7) != null && getNodes.getChild(7).getChild(0).getText().toString().contains("Your account is inactive/suspended.")) {
          getNodes.getChild(9).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
      } catch (Exception e) {}

      // main page, get 1 day left dialog
      getNodes = getRootInActiveWindow();
      try {
        if (getNodes.getChild(7) != null && getNodes.getChild(7).getChild(0).getText().toString().contains("Your account will be inactive in 1 day")) {
          getNodes.getChild(9).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }

      } catch (Exception e) {}

      // get balance attempt
      if (source.getClassName().equals("android.widget.HorizontalScrollView")) {

//        try {Thread.sleep(2000);} catch (Exception e) {};
        if (source.getParent().getChild(7).getChild(1).getClassName().equals("android.widget.TextView")) {
          if (performOnce == 0) {
            performOnce += 1;

            isFailed = preferences.getBoolean("isFailed", false);
            isCalled = preferences.getBoolean("isCalled", false);

            if (!isCalled) {
              Log.i(TAG, "getViewableTransactionData: !iscalled ");
              balance = source.getParent().getChild(7).getChild(1).getText().toString().substring(2);
              editor.putString("oldBalance", balance);
              editor.commit();

              String phoneNumber = preferences.getString("phoneNum", "");

              // to topup page
              JSONObject balanceActionObj = new JSONObject();
              balanceActionObj.put("SecretKey", "dde0823be88140da54210bb55939245e6937e680");
              balanceActionObj.put("PhoneNumber", phoneNumber);
              balanceActionObj.put("action", "MAIN");
              balanceActionObj.put("Balance", balance);

              Call<UploadObject> balanceActionCall = uploadTelcoInterface.uploadTelco(balanceActionObj);
              balanceActionCall.enqueue(new Callback<UploadObject>() {
                @Override
                public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                  if (response.body() != null) {
                    try {
                      Thread.sleep(2000);
                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                      Path path = new Path();
                      path.moveTo(713, 472);
                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                          super.onCompleted(gestureDescription);
                          try {
                            Thread.sleep(1000);
                          } catch (Exception e) {}

                          // Switch to reload via PIN tab
                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                          Path path = new Path();
                          path.moveTo(450, 132);
                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                            @Override
                            public void onCompleted(GestureDescription gestureDescription) {
                              super.onCompleted(gestureDescription);
                              try {
                                Thread.sleep(2000);
                              } catch (Exception e) {}
                            }
                          }, null);
                        }
                      }, null);
                    } catch (Exception e) {}
                  }
                  else if (response.code() == 401) {
                    Log.i(TAG, "onResponse: huh");
                    try {
                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                      Path path = new Path();
                      path.moveTo(12, 48);
                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                          super.onCompleted(gestureDescription);
                          try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                          Path path = new Path();
                          path.moveTo(96, 1052);
                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                            @Override
                            public void onCompleted(GestureDescription gestureDescription) {
                              super.onCompleted(gestureDescription);
                              try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                              Path path = new Path();
                              path.moveTo(128, 798);
                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                @Override
                                public void onCompleted(GestureDescription gestureDescription) {
                                  super.onCompleted(gestureDescription);
                                  performGlobalAction(GLOBAL_ACTION_RECENTS);
                                  try {
                                    Thread.sleep(1000);
                                    Intent launchIntent2 = getPackageManager().getLaunchIntentForPackage("com.omesti.myumobile");
                                    if (launchIntent2 != null) {
                                      startActivity(launchIntent2);
                                    }
                                  } catch (Exception e) {

                                  }

                                }
                              }, null);
                            }
                          }, null);
                        }
                      }, null);
                    } catch (Exception e) {
                    }
                  }
                }

                @Override
                public void onFailure(Call<UploadObject> call, Throwable t) {}
              });
            }
            else if (isCalled && !isFailed) {
              Log.i(TAG, "getViewableTransactionData: iscalled + !isfailed");
              try {
                Thread.sleep(2000);
                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                Path path = new Path();
                path.moveTo(713, 472);
                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                  @Override
                  public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    try {
                      Thread.sleep(1000);
                    } catch (Exception e) {}

                    // Switch to reload via PIN tab
                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                    Path path = new Path();
                    path.moveTo(450, 132);
                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                      @Override
                      public void onCompleted(GestureDescription gestureDescription) {
                        super.onCompleted(gestureDescription);
                        try {
                          Thread.sleep(2000);
                        } catch (Exception e) {}
                      }
                    }, null);
                  }
                }, null);
              } catch (Exception e) {}
            }
            else if (isCalled && isFailed) {
              Log.i(TAG, "getViewableTransactionData: iscalled + !isfailed");
              try {
                Thread.sleep(2000);
                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                Path path = new Path();
                path.moveTo(713, 472);
                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                  @Override
                  public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    try {
                      Thread.sleep(1000);
                    } catch (Exception e) {}

                    // Switch to reload via PIN tab
                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                    Path path = new Path();
                    path.moveTo(450, 132);
                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                      @Override
                      public void onCompleted(GestureDescription gestureDescription) {
                        super.onCompleted(gestureDescription);
                        try {
                          Thread.sleep(2000);
                        } catch (Exception e) {}
                      }
                    }, null);
                  }
                }, null);
              } catch (Exception e) {}
            }
          }
        }
      }

      // navigate to reload PIN
      if (source.getText().equals("eg. 12345678901234")) {
        /** ================================================================== reload ops ================================================================== **/
        String phoneNumber = preferences.getString("phoneNum", "");

        JSONObject reloadPINActionObj = new JSONObject();
        reloadPINActionObj.put("SecretKey", "dde0823be88140da54210bb55939245e6937e680");
        reloadPINActionObj.put("PhoneNumber", phoneNumber);
        reloadPINActionObj.put("action", "ReloadPIN");

        Call<UploadObject> reloadPINActionCall = uploadTelcoInterface.uploadTelco(reloadPINActionObj);
        reloadPINActionCall.enqueue(new Callback<UploadObject>() {
          @Override
          public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
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

                            saveReloadPIN(phoneNumber, "Umobile", tranID, reloadPINString);
                            Bundle reloadPINBundle = new Bundle();
                            reloadPINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reloadPINString);
                            source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, reloadPINBundle);
                            Thread.sleep(1000);
                            source.getParent().getParent().getChild(1).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Thread.sleep(2000);

                            performGlobalAction(GLOBAL_ACTION_RECENTS);
                            Thread.sleep(2000);
                            Intent launchIntent2 = getPackageManager().getLaunchIntentForPackage("com.omesti.myumobile");
                            if (launchIntent2 != null) {
                              startActivity(launchIntent2);
                              Thread.sleep(10000);
                            }

                            try {
                              getNodes = getRootInActiveWindow();
                              if (getNodes.getChild(7).getClassName().equals("android.widget.TextView")) {
                                if (getNodes.getChild(7).getText().toString().contains("the voucher you applied is not valid")) {
                                  Log.i(TAG, "onResponse: " + getNodes.getChild(8));
                                  JSONObject reloadFailedObj = new JSONObject();
                                  reloadFailedObj.put("SecretKey", "dde0823be88140da54210bb55939245e6937e680");
                                  reloadFailedObj.put("PhoneNumber", phoneNumber);
                                  reloadFailedObj.put("action", "Result");
                                  reloadFailedObj.put("TranID", tranID);
                                  reloadFailedObj.put("message", getNodes.getChild(7).getText().toString());

                                  Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                  reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                    @Override
                                    public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                      if (response.code() == 400) {
                                        Log.i(TAG, "onResponse: error msg 400");
                                        getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                      }
                                      else if (response.code() == 401) {
                                        Log.i(TAG, "onResponse: error msg 401");
                                        getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                      }
                                      else if (response.body() != null) {
                                        telcoDatabaseHandler.updateIsSuccess(false, phoneNumber, "Umobile", tranID);
                                        editor.putBoolean("isFailed", true);
                                        editor.commit();

                                        getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        performOnce = 0;
                                        try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                        Path path = new Path();
                                        path.moveTo(17, 56);
                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                          @Override
                                          public void onCompleted(GestureDescription gestureDescription) {
                                            super.onCompleted(gestureDescription);
                                            try {Thread.sleep(1000);} catch (Exception e) {}
                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                            Path path = new Path();
                                            path.moveTo(713, 472);
                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                              @Override
                                              public void onCompleted(GestureDescription gestureDescription) {
                                                super.onCompleted(gestureDescription);
                                                try {Thread.sleep(1000);} catch (Exception e) {}

                                                // Switch to reload via PIN tab
                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                Path path = new Path();
                                                path.moveTo(450, 132);
                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                  @Override
                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                    super.onCompleted(gestureDescription);
                                                    try {Thread.sleep(2000);} catch (Exception e) {}
                                                  }
                                                }, null);
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
                                else if (getNodes.getChild(7).getText().toString().contains("the voucher you have entered has already been used")) {
                                  JSONObject reloadFailedObj = new JSONObject();
                                  reloadFailedObj.put("SecretKey", "dde0823be88140da54210bb55939245e6937e680");
                                  reloadFailedObj.put("PhoneNumber", phoneNumber);
                                  reloadFailedObj.put("action", "Result");
                                  reloadFailedObj.put("TranID", tranID);
                                  reloadFailedObj.put("message", getNodes.getChild(7).getText().toString());

                                  Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                  reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                    @Override
                                    public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                      if (response.code() == 400) {
                                        Log.i(TAG, "onResponse: error msg 400");
                                        getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                                      }
                                      else if (response.code() == 401) {
                                        Log.i(TAG, "onResponse: error msg 401");
                                        getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                      }
                                      else if (response.code() == 200) {
                                        telcoDatabaseHandler.updateIsSuccess(false, phoneNumber, "Umobile", tranID);
                                        editor.putBoolean("isFailed", true);
                                        editor.commit();

                                        getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        performOnce = 0;
                                        try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                        Path path = new Path();
                                        path.moveTo(17, 56);
                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                          @Override
                                          public void onCompleted(GestureDescription gestureDescription) {
                                            super.onCompleted(gestureDescription);
                                            try {Thread.sleep(1000);} catch (Exception e) {}
                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                            Path path = new Path();
                                            path.moveTo(713, 472);
                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                              @Override
                                              public void onCompleted(GestureDescription gestureDescription) {
                                                super.onCompleted(gestureDescription);
                                                try {Thread.sleep(1000);} catch (Exception e) {}

                                                // Switch to reload via PIN tab
                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                Path path = new Path();
                                                path.moveTo(450, 132);
                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                  @Override
                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                    super.onCompleted(gestureDescription);
                                                    try {Thread.sleep(2000);} catch (Exception e) {}
                                                  }
                                                }, null);
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
                              else if (getNodes.getChild(7).getClassName().equals("android.widget.ScrollView")) {
                                if (getNodes.getChild(7).getChild(0).getText().toString().contains("you have been barred from the subscription")) {
                                  getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                  Thread.sleep(3000);

                                  getNodes = getRootInActiveWindow();
                                  String receivedPhoneNum = preferences.getString("phoneNum", "");

                                  if (getNodes.getChild(2).getChild(0).getText().toString().contains("Limit Exceeded")) {
                                    editor.putBoolean("isChangeNum", true);
                                    editor.commit();

                                    JSONObject blockedActionObj = new JSONObject();
                                    blockedActionObj.put("SecretKey", "dde0823be88140da54210bb55939245e6937e680");
                                    blockedActionObj.put("PhoneNumber", receivedPhoneNum);
                                    blockedActionObj.put("action", "Result");
                                    blockedActionObj.put("TranID", tranID);
                                    blockedActionObj.put("message", getNodes.getChild(1).getText().toString());
                                    blockedActionObj.put("phoneStatus", "BLOCKED");

                                    Call<UploadObject> blockedActionCall = uploadTelcoInterface.uploadTelco(blockedActionObj);
                                    blockedActionCall.enqueue(new Callback<UploadObject>() {
                                      @Override
                                      public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                        if (response.code() == 400) {
                                          Log.i(TAG, "onResponse: posted and this exist 400");
                                        }
                                        else if (response.code() == 401) {
                                          try {
                                            telcoDatabaseHandler.updateIsSuccess(false, receivedPhoneNum, "Umobile", tranID);
                                            editor.putBoolean("isFailed", true);
                                            editor.commit();

                                            Thread.sleep(1000);
                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                            Path path = new Path();
                                            path.moveTo(0, 65);
                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                              @Override
                                              public void onCompleted(GestureDescription gestureDescription) {
                                                super.onCompleted(gestureDescription);
                                                try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                Path path = new Path();
                                                path.moveTo(12, 48);
                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                  @Override
                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                    super.onCompleted(gestureDescription);
                                                    try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                    Path path = new Path();
                                                    path.moveTo(96, 1052);
                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                      @Override
                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                        super.onCompleted(gestureDescription);
                                                        try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                        Path path = new Path();
                                                        path.moveTo(128, 798);
                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                        dispatchGesture(gestureBuilder.build(), null, null);
                                                      }
                                                    }, null);
                                                  }
                                                }, null);
                                              }
                                            }, null);
                                          } catch (Exception e) {

                                          }
                                        }
                                        else if (response.code() == 200) {

                                        }
                                      }

                                      @Override
                                      public void onFailure(Call<UploadObject> call, Throwable t) {

                                      }
                                    });
                                  }
                                  else if (getNodes.getChild(2).getChild(0).getText().toString().equals("Request Approved")) {
                                    JSONObject reloadFailedObj = new JSONObject();
                                    reloadFailedObj.put("SecretKey", "dde0823be88140da54210bb55939245e6937e680");
                                    reloadFailedObj.put("PhoneNumber", receivedPhoneNum);
                                    reloadFailedObj.put("action", "Result");
                                    reloadFailedObj.put("TranID", tranID);
                                    reloadFailedObj.put("message", "Transaction failed. Telco account barred and unlock operations had been done. Try reload again.");

                                    Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                                    reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                                      @Override
                                      public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                        if (response.code() == 400) {
                                          Log.i(TAG, "onResponse: error msg 400");
//
                                        }
                                        else if (response.code() == 401) {
                                          Log.i(TAG, "onResponse: error msg 401");
                                        }
                                        else if (response.code() == 200) {
                                          telcoDatabaseHandler.updateIsSuccess(false, receivedPhoneNum, "Umobile", tranID);
                                          editor.putBoolean("isFailed", true);
                                          editor.commit();

                                          performOnce = 0;
                                          try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                          Path path = new Path();
                                          path.moveTo(0, 65);
                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                            @Override
                                            public void onCompleted(GestureDescription gestureDescription) {
                                              super.onCompleted(gestureDescription);
                                              try {Thread.sleep(1000);} catch (Exception e) {}
                                              GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                              Path path = new Path();
                                              path.moveTo(713, 472);
                                              gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                              dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                @Override
                                                public void onCompleted(GestureDescription gestureDescription) {
                                                  super.onCompleted(gestureDescription);
                                                  try {Thread.sleep(1000);} catch (Exception e) {}

                                                  // Switch to reload via PIN tab
                                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                  Path path = new Path();
                                                  path.moveTo(450, 132);
                                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                    @Override
                                                    public void onCompleted(GestureDescription gestureDescription) {
                                                      super.onCompleted(gestureDescription);
                                                      try {Thread.sleep(2000);} catch (Exception e) {}
                                                    }
                                                  }, null);
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
                                else if (getNodes.getChild(7).getChild(0).getText().toString().contains("Top Up successful")) {
                                  if (isSuccessCounter == 0) {
                                    isSuccessCounter += 1;

                                    String receivedPhoneNumber = preferences.getString("phoneNum", "");
                                    String newBalanceAttempt = getSubString(getNodes.getChild(7).getChild(0).getText().toString(), " is", "RM").substring(2);
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
                                        successActionObj.put("SecretKey", "dde0823be88140da54210bb55939245e6937e680");
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
                                              performOnce = 0;
                                              isSuccessCounter = 0;
                                              telcoDatabaseHandler.updateIsSuccess(true, receivedPhoneNumber, "Umobile", tranID);
                                              Log.i(TAG, "onCompleted: not equals. save to sp(iscalled and new value)");
                                              editor.putString("oldBalance", newBalanceAttempt);
                                              editor.commit();

                                              try {
                                                Thread.sleep(2000);
                                                getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                editor.putBoolean("isFailed", false);
                                                editor.putBoolean("isCalled", true);
                                                editor.commit();

                                                Thread.sleep(3000);
                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                Path path = new Path();
                                                path.moveTo(713, 472);
                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                  @Override
                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                    super.onCompleted(gestureDescription);
                                                    try {
                                                      Thread.sleep(1000);
                                                    } catch (Exception e) {}

                                                    // Switch to reload via PIN tab
                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                    Path path = new Path();
                                                    path.moveTo(450, 132);
                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                      @Override
                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                        super.onCompleted(gestureDescription);
                                                        try {
                                                          Thread.sleep(2000);
                                                        } catch (Exception e) {}
                                                      }
                                                    }, null);
                                                  }
                                                }, null);
//          }
                                              } catch (Exception e) {}

                                            }
                                            else if (response.code() == 401) {
                                              try {
                                                performOnce = 0;

                                                getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                editor.putBoolean("isCalled", false);
                                                editor.commit();

                                                // logout press
                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                Path path = new Path();
                                                path.moveTo(12, 48);
                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                  @Override
                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                    super.onCompleted(gestureDescription);
                                                    try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                    Path path = new Path();
                                                    path.moveTo(96, 1052);
                                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                      @Override
                                                      public void onCompleted(GestureDescription gestureDescription) {
                                                        super.onCompleted(gestureDescription);
                                                        try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                        Path path = new Path();
                                                        path.moveTo(128, 798);
                                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                          @Override
                                                          public void onCompleted(GestureDescription gestureDescription) {
                                                            super.onCompleted(gestureDescription);
                                                            performGlobalAction(GLOBAL_ACTION_RECENTS);
                                                            try {
                                                              Thread.sleep(1000);
                                                              Intent launchIntent2 = getPackageManager().getLaunchIntentForPackage("com.omesti.myumobile");
                                                              if (launchIntent2 != null) {
                                                                startActivity(launchIntent2);
                                                              }
                                                            } catch (Exception e) {

                                                            }

                                                          }
                                                        }, null);
                                                      }
                                                    }, null);
                                                  }
                                                }, null);
                                              } catch (Exception e) {
                                              }
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
                            } catch (Exception e) {}
                          }
                        } catch (Exception e) {}
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
              if (response.body() != null) {
                try {
                  JSONObject reloadPINActionResponseObj = new JSONObject(new Gson().toJson(response.body()));
                  if (reloadPINActionResponseObj.get("status").equals("SUCCESS")) {
                    reloadPINHandler.removeCallbacksAndMessages(null);

                    String reloadPINString = reloadPINActionResponseObj.get("ReloadPIN").toString();
                    tranID = reloadPINActionResponseObj.get("TranID").toString();

                    saveReloadPIN(phoneNumber, "Umobile", tranID, reloadPINString);
                    Bundle reloadPINBundle = new Bundle();
                    reloadPINBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reloadPINString);
                    source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, reloadPINBundle);
                    Thread.sleep(1000);
                    source.getParent().getParent().getChild(1).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Thread.sleep(2000);

                    performGlobalAction(GLOBAL_ACTION_RECENTS);
                    Thread.sleep(2000);
                    Intent launchIntent2 = getPackageManager().getLaunchIntentForPackage("com.omesti.myumobile");
                    if (launchIntent2 != null) {
                      startActivity(launchIntent2);
                      Thread.sleep(10000);
                    }

                    try {
                      getNodes = getRootInActiveWindow();
                      if (getNodes.getChild(7).getClassName().equals("android.widget.TextView")) {
                        if (getNodes.getChild(7).getText().toString().contains("the voucher you applied is not valid")) {
                          Log.i(TAG, "onResponse: " + getNodes.getChild(8));
                          JSONObject reloadFailedObj = new JSONObject();
                          reloadFailedObj.put("SecretKey", "dde0823be88140da54210bb55939245e6937e680");
                          reloadFailedObj.put("PhoneNumber", phoneNumber);
                          reloadFailedObj.put("action", "Result");
                          reloadFailedObj.put("TranID", tranID);
                          reloadFailedObj.put("message", getNodes.getChild(7).getText().toString());

                          Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                          reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                            @Override
                            public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                              if (response.code() == 400) {
                                Log.i(TAG, "onResponse: error msg 400");
                                getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                              }
                              else if (response.code() == 401) {
                                Log.i(TAG, "onResponse: error msg 401");
                                getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                              }
                              else if (response.body() != null) {
                                telcoDatabaseHandler.updateIsSuccess(false, phoneNumber, "Umobile", tranID);
                                editor.putBoolean("isFailed", true);
                                editor.commit();

                                getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                performOnce = 0;
                                try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                Path path = new Path();
                                path.moveTo(17, 56);
                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                  @Override
                                  public void onCompleted(GestureDescription gestureDescription) {
                                    super.onCompleted(gestureDescription);
                                    try {Thread.sleep(1000);} catch (Exception e) {}
                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                    Path path = new Path();
                                    path.moveTo(713, 472);
                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                      @Override
                                      public void onCompleted(GestureDescription gestureDescription) {
                                        super.onCompleted(gestureDescription);
                                        try {Thread.sleep(1000);} catch (Exception e) {}

                                        // Switch to reload via PIN tab
                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                        Path path = new Path();
                                        path.moveTo(450, 132);
                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                          @Override
                                          public void onCompleted(GestureDescription gestureDescription) {
                                            super.onCompleted(gestureDescription);
                                            try {Thread.sleep(2000);} catch (Exception e) {}
                                          }
                                        }, null);
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
                        else if (getNodes.getChild(7).getText().toString().contains("the voucher you have entered has already been used")) {
                          JSONObject reloadFailedObj = new JSONObject();
                          reloadFailedObj.put("SecretKey", "dde0823be88140da54210bb55939245e6937e680");
                          reloadFailedObj.put("PhoneNumber", phoneNumber);
                          reloadFailedObj.put("action", "Result");
                          reloadFailedObj.put("TranID", tranID);
                          reloadFailedObj.put("message", getNodes.getChild(7).getText().toString());

                          Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                          reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                            @Override
                            public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                              if (response.code() == 400) {
                                Log.i(TAG, "onResponse: error msg 400");
                                getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
                              }
                              else if (response.code() == 401) {
                                Log.i(TAG, "onResponse: error msg 401");
                                getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                              }
                              else if (response.code() == 200) {
                                telcoDatabaseHandler.updateIsSuccess(false, phoneNumber, "Umobile", tranID);
                                editor.putBoolean("isFailed", true);
                                editor.commit();

                                getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                performOnce = 0;
                                try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                Path path = new Path();
                                path.moveTo(17, 56);
                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                  @Override
                                  public void onCompleted(GestureDescription gestureDescription) {
                                    super.onCompleted(gestureDescription);
                                    try {Thread.sleep(1000);} catch (Exception e) {}
                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                    Path path = new Path();
                                    path.moveTo(713, 472);
                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                      @Override
                                      public void onCompleted(GestureDescription gestureDescription) {
                                        super.onCompleted(gestureDescription);
                                        try {Thread.sleep(1000);} catch (Exception e) {}

                                        // Switch to reload via PIN tab
                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                        Path path = new Path();
                                        path.moveTo(450, 132);
                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                          @Override
                                          public void onCompleted(GestureDescription gestureDescription) {
                                            super.onCompleted(gestureDescription);
                                            try {Thread.sleep(2000);} catch (Exception e) {}
                                          }
                                        }, null);
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
                      else if (getNodes.getChild(7).getClassName().equals("android.widget.ScrollView")) {
                        if (getNodes.getChild(7).getChild(0).getText().toString().contains("you have been barred from the subscription")) {
                          getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                          Thread.sleep(3000);

                          getNodes = getRootInActiveWindow();
                          String receivedPhoneNum = preferences.getString("phoneNum", "");

                          if (getNodes.getChild(2).getChild(0).getText().toString().contains("Limit Exceeded")) {
                            editor.putBoolean("isChangeNum", true);
                            editor.commit();

                            JSONObject blockedActionObj = new JSONObject();
                            blockedActionObj.put("SecretKey", "dde0823be88140da54210bb55939245e6937e680");
                            blockedActionObj.put("PhoneNumber", receivedPhoneNum);
                            blockedActionObj.put("action", "Result");
                            blockedActionObj.put("TranID", tranID);
                            blockedActionObj.put("message", getNodes.getChild(1).getText().toString());
                            blockedActionObj.put("phoneStatus", "BLOCKED");

                            Call<UploadObject> blockedActionCall = uploadTelcoInterface.uploadTelco(blockedActionObj);
                            blockedActionCall.enqueue(new Callback<UploadObject>() {
                              @Override
                              public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                if (response.code() == 400) {
                                  Log.i(TAG, "onResponse: posted and this exist 400");
                                }
                                else if (response.code() == 401) {
                                  try {
                                     telcoDatabaseHandler.updateIsSuccess(false, receivedPhoneNum, "Umobile", tranID);
                                    editor.putBoolean("isFailed", true);
                                    editor.commit();

                                    Thread.sleep(1000);
                                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                    Path path = new Path();
                                    path.moveTo(0, 65);
                                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                      @Override
                                      public void onCompleted(GestureDescription gestureDescription) {
                                        super.onCompleted(gestureDescription);
                                        try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                        Path path = new Path();
                                        path.moveTo(12, 48);
                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                          @Override
                                          public void onCompleted(GestureDescription gestureDescription) {
                                            super.onCompleted(gestureDescription);
                                            try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                            Path path = new Path();
                                            path.moveTo(96, 1052);
                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                              @Override
                                              public void onCompleted(GestureDescription gestureDescription) {
                                                super.onCompleted(gestureDescription);
                                                try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                Path path = new Path();
                                                path.moveTo(128, 798);
                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                dispatchGesture(gestureBuilder.build(), null, null);
                                              }
                                            }, null);
                                          }
                                        }, null);
                                      }
                                    }, null);
                                  } catch (Exception e) {

                                  }
                                }
                                else if (response.code() == 200) {

                                }
                              }

                              @Override
                              public void onFailure(Call<UploadObject> call, Throwable t) {

                              }
                            });
                          }
                          else if (getNodes.getChild(2).getChild(0).getText().toString().equals("Request Approved")) {
                            JSONObject reloadFailedObj = new JSONObject();
                            reloadFailedObj.put("SecretKey", "dde0823be88140da54210bb55939245e6937e680");
                            reloadFailedObj.put("PhoneNumber", receivedPhoneNum);
                            reloadFailedObj.put("action", "Result");
                            reloadFailedObj.put("TranID", tranID);
                            reloadFailedObj.put("message", "Transaction failed. Telco account barred and unlock operations had been done. Try reload again.");

                            Call<UploadObject> reloadFailedObjCall = uploadTelcoInterface.uploadTelco(reloadFailedObj);
                            reloadFailedObjCall.enqueue(new Callback<UploadObject>() {
                              @Override
                              public void onResponse(Call<UploadObject> call, Response<UploadObject> response) {
                                if (response.code() == 400) {
                                  Log.i(TAG, "onResponse: error msg 400");
//
                                }
                                else if (response.code() == 401) {
                                  Log.i(TAG, "onResponse: error msg 401");
                                }
                                else if (response.code() == 200) {
                                  telcoDatabaseHandler.updateIsSuccess(false, receivedPhoneNum, "Umobile", tranID);
                                  editor.putBoolean("isFailed", true);
                                  editor.commit();

                                  performOnce = 0;
                                  try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                  GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                  Path path = new Path();
                                  path.moveTo(0, 65);
                                  gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                  dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                    @Override
                                    public void onCompleted(GestureDescription gestureDescription) {
                                      super.onCompleted(gestureDescription);
                                      try {Thread.sleep(1000);} catch (Exception e) {}
                                      GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                      Path path = new Path();
                                      path.moveTo(713, 472);
                                      gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                      dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                        @Override
                                        public void onCompleted(GestureDescription gestureDescription) {
                                          super.onCompleted(gestureDescription);
                                          try {Thread.sleep(1000);} catch (Exception e) {}

                                          // Switch to reload via PIN tab
                                          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                          Path path = new Path();
                                          path.moveTo(450, 132);
                                          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                            @Override
                                            public void onCompleted(GestureDescription gestureDescription) {
                                              super.onCompleted(gestureDescription);
                                              try {Thread.sleep(2000);} catch (Exception e) {}
                                            }
                                          }, null);
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
                        else if (getNodes.getChild(7).getChild(0).getText().toString().contains("Top Up successful")) {
                          if (isSuccessCounter == 0) {
                            isSuccessCounter += 1;

                            String receivedPhoneNumber = preferences.getString("phoneNum", "");
                            String newBalanceAttempt = getSubString(getNodes.getChild(7).getChild(0).getText().toString(), " is", "RM").substring(2);
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
                                successActionObj.put("SecretKey", "dde0823be88140da54210bb55939245e6937e680");
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
                                      performOnce = 0;
                                      isSuccessCounter = 0;
                                      telcoDatabaseHandler.updateIsSuccess(true, receivedPhoneNumber, "Umobile", tranID);
                                      Log.i(TAG, "onCompleted: not equals. save to sp(iscalled and new value)");
                                      editor.putString("oldBalance", newBalanceAttempt);
                                      editor.commit();

                                      try {
                                        Thread.sleep(2000);
                                        getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        editor.putBoolean("isFailed", false);
                                        editor.putBoolean("isCalled", true);
                                        editor.commit();

                                        Thread.sleep(3000);
                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                        Path path = new Path();
                                        path.moveTo(713, 472);
                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                          @Override
                                          public void onCompleted(GestureDescription gestureDescription) {
                                            super.onCompleted(gestureDescription);
                                            try {
                                              Thread.sleep(1000);
                                            } catch (Exception e) {}

                                            // Switch to reload via PIN tab
                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                            Path path = new Path();
                                            path.moveTo(450, 132);
                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                              @Override
                                              public void onCompleted(GestureDescription gestureDescription) {
                                                super.onCompleted(gestureDescription);
                                                try {
                                                  Thread.sleep(2000);
                                                } catch (Exception e) {}
                                              }
                                            }, null);
                                          }
                                        }, null);
//          }
                                      } catch (Exception e) {}

                                    }
                                    else if (response.code() == 401) {
                                      try {
                                        performOnce = 0;

                                        getNodes.getChild(8).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        editor.putBoolean("isCalled", false);
                                        editor.commit();

                                        // logout press
                                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                        Path path = new Path();
                                        path.moveTo(12, 48);
                                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                          @Override
                                          public void onCompleted(GestureDescription gestureDescription) {
                                            super.onCompleted(gestureDescription);
                                            try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                            Path path = new Path();
                                            path.moveTo(96, 1052);
                                            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                              @Override
                                              public void onCompleted(GestureDescription gestureDescription) {
                                                super.onCompleted(gestureDescription);
                                                try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                                Path path = new Path();
                                                path.moveTo(128, 798);
                                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                                  @Override
                                                  public void onCompleted(GestureDescription gestureDescription) {
                                                    super.onCompleted(gestureDescription);
                                                    performGlobalAction(GLOBAL_ACTION_RECENTS);
                                                    try {
                                                      Thread.sleep(1000);
                                                      Intent launchIntent2 = getPackageManager().getLaunchIntentForPackage("com.omesti.myumobile");
                                                      if (launchIntent2 != null) {
                                                        startActivity(launchIntent2);
                                                      }
                                                    } catch (Exception e) {

                                                    }

                                                  }
                                                }, null);
                                              }
                                            }, null);
                                          }
                                        }, null);
                                      } catch (Exception e) {
                                      }
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
                    } catch (Exception e) {}
                  }
                } catch (Exception e) {}
              }
            }

          }

          @Override
          public void onFailure(Call<UploadObject> call, Throwable t) {

          }
        });
//        getNodes = getRootInActiveWindow();
//        if (getNodes.getChild(5).getChild(1).getChild(0).getChild(2)!= null && getNodes.getChild(5).getChild(1).getChild(0).getChild(2).getClassName().equals("android.widget.EditText")) {
//
//        }
        /** ================================================================== end of reload ops ================================================================== **/
      }

    } catch (Exception e) {

    }
  }

  public String convertTo24HoursFormat(String twelveHourTime)
          throws ParseException {
    return TWENTY_FOUR_TF.format(TWELVE_TF.parse(twelveHourTime));
  }

  public void saveReloadPIN(String phoneNum, String telcoType, String tranID, String reloadPIN) {
    telcoDatabaseModel = new TelcoDatabaseModel();

    telcoDatabaseModel.setPhoneNumber(phoneNum);
    telcoDatabaseModel.setTelcoType(telcoType);
    telcoDatabaseModel.setTranID(tranID);
    telcoDatabaseModel.setReloadPIN(reloadPIN);

    telcoDatabaseHandler.addTelcoEntry(telcoDatabaseModel);
  }

  public static String getSubString(String mainString, String lastString, String startString) {
    String endString = "";
    int endIndex = mainString.indexOf(lastString);
    int startIndex = mainString.indexOf(startString);
    Log.d("message", "" + mainString.substring(startIndex, endIndex));
    endString = mainString.substring(startIndex, endIndex);
    return endString;
  }
}


