package com.example.myapplication;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.RequiresApi;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MyService extends AccessibilityService{

  Context context;
  private static final String TAG = "access";
  private static final String TRANSACTION_RECEIVED = "You just got paid";
  private static Runnable myRunnable;
  public static Handler myHandler = new Handler();
  SharedPreferences preferences;
  SharedPreferences.Editor editor;
  HashCode hashCode;

  // layout types

  // constants of tng account owner
//  private final String acc_1 =

  @RequiresApi(api = Build.VERSION_CODES.O)
  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {

    try {
      if(event.getSource().getPackageName().equals("my.com.tngdigital.ewallet")) {
        context = MyApplication.getAppContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        editor = preferences.edit();

        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
//        Log.i(TAG, "eventType is notification, packageName is " + event.getPackageName());
//          onNotificationClickTngApps(event);
        }

        getViewableTransactionData(event);
        getTngScanButtonClick(event);
        getScanFromGalleryClick(event);
      } else if (event.getSource().getPackageName().equals("com.android.documentsui")) {
        fetchGalleryImage(event);
      }




    } catch (Exception e) {

    }
  }

  @Override
  public void onInterrupt() {

  }

  public void getViewableTransactionData(AccessibilityEvent event) {
    // Only listen to
    if(event.getPackageName().equals("my.com.tngdigital.ewallet")) {

      AccessibilityNodeInfo source = event.getSource();
      AccessibilityNodeInfo parentalNode = source.getParent();

      // login page
      List<AccessibilityNodeInfo> editText = source.findAccessibilityNodeInfosByViewId("my.com.tngdigital.ewallet:id/et_center");
      String input = "102192763";
      Bundle bundle = new Bundle();
      bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, input);

      for (AccessibilityNodeInfo node : editText) {
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle);

        // login to pin page
        List<AccessibilityNodeInfo> nextButton = source.findAccessibilityNodeInfosByViewId("my.com.tngdigital.ewallet:id/cbb_botton");
        for (AccessibilityNodeInfo nextNode: nextButton) {
          nextNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
      }

      // PIN inout page
      List<AccessibilityNodeInfo> PINeditText = source.findAccessibilityNodeInfosByViewId("my.com.tngdigital.ewallet:id/pin_pcv");
      String PINinput = "157882";
      Bundle PINbundle = new Bundle();
      PINbundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, PINinput);

      for (AccessibilityNodeInfo node : PINeditText) {
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle);
      }

      // profile to transactions page click
      //Transactions page. Find first webview to start scraping
      if(parentalNode != null && parentalNode.getChild(0).getClassName().equals("android.webkit.WebView")) {
        // Set the webview node into a variable
        AccessibilityNodeInfo firstWebView = parentalNode.getChild(0);

        // Hardcode get the transaction nodes

//        AccessibilityNodeInfo noTransactionNodes = source.getChild(0).getChild(0).getChild(0).getChild(0).getChild(0).getChild(0).
//                getChild(0).getChild(1).getChild(0).getChild(0).getChild(0).getChild(1);


        try {
          if (source.getChild(0).getChild(0).getChild(0).getChild(0).getChild(0).getChild(1).
                  getChild(0).getChild(1).getChild(0).getChild(0).getChild(0).getChild(1) != null && source.getChild(0).getChild(0).getChild(0).getChild(0).getChild(0).getChild(1).
                  getChild(0).getChild(1).getChild(0).getChild(0).getChild(0).getChild(1).getText().equals("No transaction found")) {
            Log.i(TAG, "No transaction found");
          } else {
            myRunnable = new Runnable() {
              @Override
              public void run() {

                DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                Log.i(TAG, "runnable running" + myRunnable);
                final int bottom = (int) (displayMetrics.heightPixels * 0.97);
                final int midX = displayMetrics.widthPixels / 2;

                //call function
                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                Path path = new Path();
                path.moveTo(midX, bottom);
                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));

                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                  @Override
                  public void onCompleted(GestureDescription gestureDescription) {
                    AccessibilityNodeInfo mainDateNode ;
                    AccessibilityNodeInfo paymentTimeNode;
                    AccessibilityNodeInfo paymentAmountNode;
                    String formattedDate = null;

                    // Loop through each transaction nodes
//                  mainDateNode = firstWebView.getChild(0).getChild(0).getChild(0).getChild(0).getChild(0).getChild(1).
//                          getChild(0).getChild(1).getChild(0).getChild(0).getChild(0).getChild(0);

                    try {
                      if (firstWebView.getChild(0).getChild(0).getChild(0).getChild(0).getChild(0).getChild(1).
                              getChild(0).getChild(1).getChild(0).getChild(0).getChild(0).getChild(0) != null) {
                        Log.i(TAG, "Payment Date : " + firstWebView.getChild(0).getChild(0).getChild(0).getChild(0).getChild(0).getChild(1).
                                getChild(0).getChild(1).getChild(0).getChild(0).getChild(0).getChild(0).getText());

                        String startDateString = String.valueOf(firstWebView.getChild(0).getChild(0).getChild(0).getChild(0).getChild(0).getChild(1).
                                getChild(0).getChild(1).getChild(0).getChild(0).getChild(0).getChild(0).getText());
                        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy");
                        try {
                          Date d = sdf.parse(startDateString);
                          DateFormat targetFormat = new SimpleDateFormat("yyyy-MM-dd");
                          formattedDate = targetFormat.format(d);
                        } catch (ParseException ex) {
                          Log.v("Exception", ex.getLocalizedMessage());
                        }
                      }


                      for(int i = 1; i < firstWebView.getChild(0).getChild(0).getChild(0).getChild(0).getChild(0).getChild(1).
                              getChild(0).getChild(1).getChild(0).getChild(0).getChild(0).getChildCount(); i++) {
                        paymentTimeNode = firstWebView.getChild(0).getChild(0).getChild(0).getChild(0).getChild(0).getChild(1).
                                getChild(0).getChild(1).getChild(0).getChild(0).getChild(0).getChild(i).getChild(0).getChild(1).getChild(2);
                        paymentAmountNode = firstWebView.getChild(0).getChild(0).getChild(0).getChild(0).getChild(0).getChild(1).
                                getChild(0).getChild(1).getChild(0).getChild(0).getChild(0).getChild(i).getChild(0).getChild(2).getChild(0);

                        Log.i(TAG, "Payment Time : " + paymentTimeNode.getText());
                        Log.i(TAG, "Amount : " + paymentAmountNode.getText());

                        String combinedDateTime = formattedDate + " " + paymentTimeNode.getText().toString().substring(0, paymentTimeNode.getText().toString().length() - 3);

                        // Send API to server for the first time
                        String valueToCompare = preferences.getString("valueToCompare", "");
                        if (valueToCompare.equals("")) {
                          sha1Hash(firstWebView.getChild(0).getChild(0).getChild(0).getChild(0).getChild(0).getChild(1).
                                  getChild(0).getChild(1).getChild(0).getChild(0).getChild(0).getChild(0).getText().toString() +
                                  paymentTimeNode.getText().toString() + paymentAmountNode.getText().toString(), i);
                          postData postData = new postData();
                          postData.execute(Double.parseDouble(paymentAmountNode.getText().toString().substring(5)), combinedDateTime);
                          if (i == 1) {
                            editor.putString("valueToCompare", String.valueOf(hashCode));
                            Log.i(TAG, "first time call code: " + hashCode);
                            editor.commit();
                          }
                        }
                        else {
                          Log.i(TAG, "Subsequent calls.");
                          sha1Hash(firstWebView.getChild(0).getChild(0).getChild(0).getChild(0).getChild(0).getChild(1).
                                  getChild(0).getChild(1).getChild(0).getChild(0).getChild(0).getChild(0).getText().toString() +
                                  paymentTimeNode.getText().toString() + paymentAmountNode.getText().toString(), i);
                          Log.i(TAG, "subsequent call code: " + hashCode);
                          if (String.valueOf(hashCode).equals(valueToCompare)) {
                            Log.i(TAG, "Same signature. API not called.");
                            break;
                          } else  if (!String.valueOf(hashCode).equals(valueToCompare)){
                            // new entry found. api call again
                            postData postData = new postData();
                            postData.execute(Double.parseDouble(paymentAmountNode.getText().toString().substring(5)), combinedDateTime);

                            if (i == 1) {
                              editor.putString("valueToCompare", String.valueOf(hashCode));
                              editor.commit();
                            }
                          }
                        }
                      }
                    } catch (Exception e) {
                      Log.e(TAG, "onCompleted: ", e);
                    }

                    super.onCompleted(gestureDescription);
                  }
                }, null);
              }
            };

            myHandler.postDelayed(myRunnable, 10000);
//            myHandler.postDelayed(myRunnable, 300000);
          }

        } catch (Exception e) {
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
          Log.i(TAG, "eventType is notification, packageName is " + event.getPackageName());
          if (myRunnable != null) {
            Log.i(TAG, "runnable removed.");
            Log.i(TAG, "getViewableTransactionData: " + myRunnable);
            myHandler.removeCallbacks(myRunnable);
//            onNotificationClickTngApps(event);
          }
        }
      }
    }
  }

  public void getTngScanButtonClick(AccessibilityEvent event) {
    if(event.getPackageName().equals("my.com.tngdigital.ewallet")) {
      AccessibilityNodeInfo source = event.getSource();

      if (source.getClassName().equals("androidx.recyclerview.widget.RecyclerView")) {
        AccessibilityNodeInfo scanButtonSource =  source.getChild(0);
        Log.i(TAG, "getTngScanButtonClick: " + scanButtonSource);
        if (scanButtonSource.getClassName().equals("android.view.ViewGroup")) {
          Log.i(TAG, "getTngScanButtonClick: yeah boi");
          scanButtonSource.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } else if (!scanButtonSource.getClassName().equals("android.view.ViewGroup")) {
          DisplayMetrics displayMetrics = getResources().getDisplayMetrics();

          final int height = displayMetrics.heightPixels;
          final int top = height / 2;
          final int bottom = height * 9/10;
          final int midX = displayMetrics.widthPixels / 2;
          GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
          Path path = new Path();
          path.moveTo(midX, top);
          path.lineTo(midX, bottom);
          gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 1000));

          dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
              super.onCompleted(gestureDescription);
              Log.i(TAG, "onCompleted: gesture complete");
            }
          }, null);
        }
      }
    }
  }

  public void getScanFromGalleryClick(AccessibilityEvent event) {
    if(event.getPackageName().equals("my.com.tngdigital.ewallet")) {
      AccessibilityNodeInfo source = event.getSource();
      Log.i(TAG, "getScanFromGalleryClick: " + source);
      AccessibilityNodeInfo scanGallerySource =  source.getChild(4);

      if (scanGallerySource.getClassName().equals("android.widget.Button")) {
        scanGallerySource.performAction(AccessibilityNodeInfo.ACTION_CLICK);
      }
    }
  }

  public void fetchGalleryImage(AccessibilityEvent event) {
    Log.i(TAG, "fetchGalleryImage: lol" + event);
  }

  private class postData extends AsyncTask<Object, Void, Void> {
    @Override
    protected Void doInBackground(Object... params) {
      try {
        double amount = (Double) params[0];
        String datetime = (String) params[1];

        URL url = new URL("https://bo.rapidpays.com/rapidpay_api/get_qrcode_notification");
//        URL url = new URL("https://demo.rapidpays.com/rapidpay_api/get_tng_notification");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        conn.setRequestProperty("Accept","application/json");

        JSONArray jsonArray = new JSONArray();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("SecretKey", "1399b536d02dd54048e67e8956f6b6021f08fd8e");
        jsonObject.put("Amount", amount);
        jsonObject.put("Datetime", datetime);
        jsonArray.put(jsonObject);
        Log.i("Json ARR: " + TAG, String.valueOf(jsonArray));

        DataOutputStream os = new DataOutputStream(conn.getOutputStream());
        os.writeBytes(jsonArray.toString());
        os.flush();
        os.close();

        Log.i(TAG, String.valueOf(conn.getResponseCode()));
        Log.i(TAG , conn.getResponseMessage());

        try(BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "utf-8"))) {
          StringBuilder response = new StringBuilder();
          String responseLine = null;
          while ((responseLine = br.readLine()) != null) {
            response.append(responseLine.trim());
          }
          Log.i(TAG, "Response: " + response.toString());
        }
        conn.disconnect();
      } catch (Exception e) {
        e.printStackTrace();
      }
      return null;
    }
  }

  @SuppressLint("DefaultLocale")
  private void sha1Hash(String string, int count) {
    hashCode = Hashing.sha1().hashString(string, Charset.defaultCharset());
  }
}

/** auto click
 *   public void autoClick(int startTimeMs, int durationMs, int x, int y) {
 *     boolean isCalled = dispatchGesture(gestureDescription(startTimeMs, durationMs, x, y), null, null);
 *   }
 *
 *   public GestureDescription gestureDescription(int startTimeMs, int durationMs, int x, int y) {
 *     Path path = new Path();
 *     path.moveTo(x, y);
 *     return createGestureDescription(new GestureDescription.StrokeDescription(path, startTimeMs, durationMs));
 *   }
 *
 *   public GestureDescription createGestureDescription(GestureDescription.StrokeDescription... strokes) {
 *     GestureDescription.Builder builder = new GestureDescription.Builder();
 *     for (GestureDescription.StrokeDescription stroke : strokes) {
 *       builder.addStroke(stroke);
 *     }
 *     return builder.build();
 *   }
 * **/


/** get notif
 *
 *   public void onNotificationClickTngApps(AccessibilityEvent event) {
 *
 *     // 1. Check if is notification event
 *     // 2. Check notification date, if is "You have received..."
 *     // 3. Dispatch Gesture click to swipe off notification OR dispatch gesture click get focus back on TNG apps
 *     // 4. Recursive call back getViewableTransactionData on gesture dispatch done
 *
 *     Log.i(TAG, "Come in here 1");
 *     // Read the notification
 *     Notification notification = (Notification) event.getParcelableData();
 *
 *     // If notification null, return;
 *     if (notification == null) return;
 *
 *     // Harvest title and text
 *     String title = notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString();
 *     String text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString();
 *
 *     // If notification contains the needed words
 *     if (title.contains(TRANSACTION_RECEIVED)) {
 *
 *       Log.i(TAG, "Come in here 2");
 *
 *       try {
 *         // Dispatch gesture click twice on to main screen
 *         DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
 *
 *         final int bottom = (int) (displayMetrics.heightPixels * 0.15);
 *         final int midX = displayMetrics.widthPixels / 2;
 *
 *         GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
 *         Path path = new Path();
 *         path.moveTo(midX, bottom);
 *         gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
 *         dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
 *           @Override
 *           public void onCompleted(GestureDescription gestureDescription) {
 *             try {
 *               getTransactionDataAfterReceivingNotification(event);
 *             } catch (Exception e) {
 *               Log.e(TAG, "onCompleted: ", e);
 *             }
 *
 *             super.onCompleted(gestureDescription);
 *           }
 *         }, null);
 *
 *         Log.i(TAG, "Come in here 3");
 *       } catch (Exception e) {
 *
 *       }
 *     }
 *   }
 **/


/**   public void getTransactionDataAfterReceivingNotification(AccessibilityEvent event) {
 if(event.getPackageName().equals("my.com.tngdigital.ewallet")) {

 AccessibilityNodeInfo source = event.getSource();
 AccessibilityNodeInfo parentalNode = source.getParent();

 // Find the first webview in order to start scrape
 if(parentalNode != null && parentalNode.getChild(0).getClassName().equals("android.webkit.WebView")) {
 //
 //        // Set the webview node into a variable
 //        AccessibilityNodeInfo firstWebView = parentalNode.getChild(0);
 //
 //        // Hardcode get the transaction nodes
 //        // get all
 ////        AccessibilityNodeInfo transactionNodes = firstWebView.getChild(0).getChild(0).getChild(0).getChild(0).getChild(0).getChild(1).
 ////                getChild(0).getChild(1).getChild(0).getChild(0);
 //        AccessibilityNodeInfo transactionNodes = firstWebView.getChild(0).getChild(0).getChild(0).getChild(0).getChild(0).getChild(1).
 //                getChild(0).getChild(1).getChild(0).getChild(0).getChild(0);
 //        if (transactionNodes.getChildCount() != -1) {
 //          Log.i(TAG, "No. of rows " + (transactionNodes.getChildCount() - 1));
 //        }
 //
 //        AccessibilityNodeInfo mainDateNode ;
 //        AccessibilityNodeInfo paymentTimeNode;
 //        AccessibilityNodeInfo paymentAmountNode;
 //        String formattedDate = null;
 //
 //        // Loop through each transaction nodes
 //        mainDateNode = transactionNodes.getChild(0);
 //
 //        Log.i(TAG, "Payment Date : " + mainDateNode.getText());
 //
 //        String startDateString = String.valueOf(mainDateNode.getText());
 //        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy");
 //        try {
 //          Date d = sdf.parse(startDateString);
 //          DateFormat targetFormat = new SimpleDateFormat("yyyy-MM-dd");
 //          formattedDate = targetFormat.format(d);
 //        } catch (ParseException ex) {
 //          Log.v("Exception", ex.getLocalizedMessage());
 //        }
 //
 //        for(int i = 1; i < transactionNodes.getChildCount(); i++) {
 //          paymentTimeNode = transactionNodes.getChild(i).getChild(0).getChild(1).getChild(2);
 //          paymentAmountNode = transactionNodes.getChild(i).getChild(0).getChild(2).getChild(0);
 //
 //          Log.i(TAG, "Payment Time : " + paymentTimeNode.getText());
 //          Log.i(TAG, "Amount : " + paymentAmountNode.getText());
 //
 //          String combinedDateTime = formattedDate + " " + paymentTimeNode.getText().toString().substring(0, paymentTimeNode.getText().toString().length() - 3);
 //
 //          // Send API to server for the first time
 //          String valueToCompare = preferences.getString("valueToCompare", "");
 //          if (valueToCompare.equals("")) {
 //            sha1Hash(mainDateNode.getText().toString() +
 //                    paymentTimeNode.getText().toString() + paymentAmountNode.getText().toString(), i);
 //            postData postData = new postData();
 //            postData.execute(Double.parseDouble(paymentAmountNode.getText().toString().substring(5)), combinedDateTime);
 //            if (i == 1) {
 //              editor.putString("valueToCompare", String.valueOf(hashCode));
 //              Log.i(TAG, "first time call code: " + hashCode);
 //              editor.commit();
 //            }
 //          }
 //          else {
 //            Log.i(TAG, "Subsequent calls.");
 //            sha1Hash(mainDateNode.getText().toString() +
 //                    paymentTimeNode.getText().toString() + paymentAmountNode.getText().toString(), i);
 //            Log.i(TAG, "subsequent call code: " + hashCode);
 //            if (String.valueOf(hashCode).equals(valueToCompare)) {
 //              Log.i(TAG, "Same signature. API not called.");
 //              break;
 //            } else  if (!String.valueOf(hashCode).equals(valueToCompare)){
 //              // new entry found. api call again
 //              postData postData = new postData();
 //              postData.execute(Double.parseDouble(paymentAmountNode.getText().toString().substring(5)), combinedDateTime);
 //
 //              if (i == 1) {
 //                editor.putString("valueToCompare", String.valueOf(hashCode));
 //                editor.commit();
 //              }
 //            }
 //          }
 //        }
 Log.i(TAG, "runnable restarted");
 myHandler.postDelayed(myRunnable, 10000);
 //        myHandler.postDelayed(myRunnable, 300000);
 }
 }
 }
 **/