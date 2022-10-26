package com.example.myapplication;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.myapplication.SQLiteDatabaseHandler.KasikornDatabaseHandler;
import com.example.myapplication.interfaces.CheckBotStatusInterface;
import com.example.myapplication.interfaces.UploadTelcoInterface;
import com.example.myapplication.models.CheckBotStatus;
import com.example.myapplication.models.databaseModels.KasikornModel;
import com.example.myapplication.utils.ApiUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class KasikornService extends AccessibilityService {
    private static final String TAG = "access";
    public int counter = 0;
    public int dispatchCounter = 0;
    boolean posted;
    private static Runnable myRunnable;
    public static Handler myHandler = new Handler();
    public static Handler botHandler = new Handler();
    public static Runnable botRunnable;
    public static Runnable restartServRunnable;
    public static Handler restartServHandler = new Handler();
    Context context;
    private static final String CHANNEL_ID = "CustomChannel";
    private static final String CHANNEL_NAME = "CustomChannelName";

    CheckBotStatusInterface checkBotStatusInterface;

    // Replace with KK:mma if you want 0-11 interval
    private DateFormat TWELVE_TF = new SimpleDateFormat("hh:mma");
    // Replace with kk:mm if you want 1-24 interval
    private DateFormat TWENTY_FOUR_TF = new SimpleDateFormat("HH:mm");

    double amount;
    String datetime;
    String referenceNo; //amount24hrformatdatetime
    KasikornDatabaseHandler transactionDatabaseHandler;
    List<KasikornModel> transactions = new ArrayList<>();
    Timer timer = new Timer();

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand: " + flags + " " + startId);
        startServiceForeground();

        TimerTask hourlyTask = new TimerTask () {
            @Override
            public void run () {
                performGlobalAction(GLOBAL_ACTION_RECENTS);

                try {
                    Thread.sleep(2000);
                    DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                    int middleYValue = displayMetrics.heightPixels / 2;
                    final int leftSideOfScreen = displayMetrics.widthPixels / 4;
                    final int rightSizeOfScreen = leftSideOfScreen * 3;
                    GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                    Path path = new Path();

                    //Swipe left
                    path.moveTo(250, middleYValue);
                    path.lineTo(50, middleYValue);

                    gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 300));
                    dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                            Log.i(TAG, "Gesture Completed");
                            super.onCompleted(gestureDescription);
                            performGlobalAction(GLOBAL_ACTION_HOME);

                            try {
                                Thread.sleep(2000);
                                GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                                Path path = new Path();
                                path.moveTo(320, 700);
                                gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 100, 100));
                                dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                                    @Override
                                    public void onCompleted(GestureDescription gestureDescription) {
                                        super.onCompleted(gestureDescription);
                                        Log.i(TAG, "g2clicked" );
                                    }
                                }, null);
                            } catch (Exception e) {

                            }
                        }
                    }, null);
                } catch (Exception e) {

                }
            }
        };

        timer.scheduleAtFixedRate(hourlyTask, 0, 1800000);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "destroy!");
        stopForeground(true);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());

//        PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT);
        PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_MUTABLE);
        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        Log.i(TAG, "onTaskRemoved: task removed!");
        alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent);

        super.onTaskRemoved(rootIntent);
    }

    @Override
    protected void onServiceConnected() {
        Log.i(TAG, "onServiceConnected: kasikorn connected");
        Intent launchIntent1 = getPackageManager().getLaunchIntentForPackage("com.kasikorn.retail.mbanking.wap");
        if (launchIntent1 != null) {
            startActivity(launchIntent1);
        }

        super.onServiceConnected();
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            context = MyApplication.getAppContext();
            transactionDatabaseHandler = new KasikornDatabaseHandler(context);
            getViewableTransactionData(event);
            disableSSLCertificateChecking();
            checkBotStatusInterface = ApiUtils.getCheckBotStatusInterface();


        } catch (Exception e) {
            Log.i(TAG, "except: " + e);
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void getViewableTransactionData(AccessibilityEvent event) {
        try {
            AccessibilityNodeInfo source = event.getSource();

            // banking btn
            List<AccessibilityNodeInfo> bankingBtn = source.findAccessibilityNodeInfosByViewId("com.kasikorn.retail.mbanking.wap:id/footer_bank_imagebutton");
            for (AccessibilityNodeInfo bankingBtnNode: bankingBtn) {
                bankingBtnNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }

            // PIN input (112233)
            List<AccessibilityNodeInfo> checkForPinInput = source.findAccessibilityNodeInfosByText("Please Enter Your PIN");
            if (checkForPinInput.size() != 0) {
                List<AccessibilityNodeInfo> firstNumpad = source.findAccessibilityNodeInfosByViewId("com.kasikorn.retail.mbanking.wap:id/linear_layout_button_activity_1");
                for (AccessibilityNodeInfo node1 : firstNumpad) {
                    node1.performAction(AccessibilityNodeInfo.ACTION_CLICK);

                    List<AccessibilityNodeInfo> secondNumpad = source.findAccessibilityNodeInfosByViewId("com.kasikorn.retail.mbanking.wap:id/linear_layout_button_activity_1");
                    for (AccessibilityNodeInfo node2 : secondNumpad) {
                        node2.performAction(AccessibilityNodeInfo.ACTION_CLICK);

                        List<AccessibilityNodeInfo> thirdNumpad = source.findAccessibilityNodeInfosByViewId("com.kasikorn.retail.mbanking.wap:id/linear_layout_button_activity_2");
                        for (AccessibilityNodeInfo node3 : thirdNumpad) {
                            node3.performAction(AccessibilityNodeInfo.ACTION_CLICK);

                            List<AccessibilityNodeInfo> fourthNumpad = source.findAccessibilityNodeInfosByViewId("com.kasikorn.retail.mbanking.wap:id/linear_layout_button_activity_2");
                            for (AccessibilityNodeInfo node4 : fourthNumpad) {
                                node4.performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                List<AccessibilityNodeInfo> fifthNumpad = source.findAccessibilityNodeInfosByViewId("com.kasikorn.retail.mbanking.wap:id/linear_layout_button_activity_3");
                                for (AccessibilityNodeInfo node5 : fifthNumpad) {
                                    node5.performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                    List<AccessibilityNodeInfo> sixthNumpad = source.findAccessibilityNodeInfosByViewId("com.kasikorn.retail.mbanking.wap:id/linear_layout_button_activity_3");
                                    for (AccessibilityNodeInfo node6 : sixthNumpad) {
                                        node6.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // idle session
            List<AccessibilityNodeInfo> idleSessionBtn = source.findAccessibilityNodeInfosByViewId("com.kasikorn.retail.mbanking.wap:id/layout_dialog_confirm");
            for (AccessibilityNodeInfo idleSessionBtnNode: idleSessionBtn) {
                idleSessionBtnNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }

            // statement btn
            List<AccessibilityNodeInfo> statementBtn = source.findAccessibilityNodeInfosByText("Debit Card");
            for (AccessibilityNodeInfo statementBtnNode: statementBtn) {
                statementBtnNode.getParent().getParent().getParent().getChild(4).getChild(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }

            // statement page navigation tab
            List<AccessibilityNodeInfo> tabScrollView = source.findAccessibilityNodeInfosByViewId("com.kasikorn.retail.mbanking.wap:id/tabLayout_accountAndCreditCardActivities_accountActivities");
            for (AccessibilityNodeInfo tabScrollViewNode: tabScrollView) {
                tabScrollViewNode.getChild(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);

                List<AccessibilityNodeInfo> backForRefresh = source.findAccessibilityNodeInfosByViewId("com.kasikorn.retail.mbanking.wap:id/linearlayout_back_button");
                for (AccessibilityNodeInfo backForRefreshNode: backForRefresh) {
                    myHandler.postDelayed(myRunnable = new Runnable() {
                        @Override
                        public void run() {
                            backForRefreshNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);

                            RequestBody secret_key = RequestBody.create(MediaType.parse("text/plain"), "9dc81d4e75a2a7bdda05f88abf22d5f20fc6fcf5");

                            Call<CheckBotStatus> call = checkBotStatusInterface.checkBotStatus(secret_key);
                            call.enqueue(new Callback<CheckBotStatus>() {
                                @Override
                                public void onResponse(@NonNull Call<CheckBotStatus> call, @NonNull Response<CheckBotStatus> response) {
                                    Log.i(TAG, "message: " + response.message());
                                }

                                @Override
                                public void onFailure(Call<CheckBotStatus> call, Throwable t) {
                                    t.printStackTrace();
                                }
                            });
                        }
                    }, 10000);
                }
            }

            // transaction records
            List<AccessibilityNodeInfo> transactionRecords = source.findAccessibilityNodeInfosByViewId("com.kasikorn.retail.mbanking.wap:id/viewPager_accountAndCreditCardActivities_tabContent");
            for (AccessibilityNodeInfo transactionRecordsNode: transactionRecords) {
                String formattedDate = null;
                AccessibilityNodeInfo topDateNode = null;
                AccessibilityNodeInfo paymentTimeNode = null;
                AccessibilityNodeInfo paymentAmountNode = null;

                topDateNode =  transactionRecordsNode.getChild(0).getChild(0).getChild(0).getChild(0);

                for (int i = 1; i < transactionRecordsNode.getChild(0).getChild(0).getChildCount(); i++) {
                    AccessibilityNodeInfo nodeInfoChild = transactionRecordsNode.getChild(0).getChild(0).getChild(i);

                    if (nodeInfoChild.getChild(0).getChild(0).getText().toString().equals("Transfer Deposit")) {
                        paymentAmountNode = nodeInfoChild.getChild(0).getChild(1);
                        paymentTimeNode = nodeInfoChild.getChild(0).getChild(3);

                        // date formatter
                        String startDateString = topDateNode.getText().toString();
                        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yy", Locale.ENGLISH);
                        Log.i(TAG, "sdf: " + sdf);
                        try {
                            Date d = sdf.parse(startDateString);
                            Log.i(TAG, "d: " + d);
                            DateFormat targetFormat = new SimpleDateFormat("yyyy-MM-dd");
                            formattedDate = targetFormat.format(d);
                        } catch (ParseException ex) {
                            Log.e("Exception", ex.getLocalizedMessage());
                        }

                        String combinedDateTime = formattedDate + " " + paymentTimeNode.getText().toString().substring(0, paymentTimeNode.getText().toString().length() - 3);

                        // reference No (amount+24hrtime+formattedDate)
                        String trimmedPaymentTime = paymentTimeNode.getText().toString().replace(" ", "");
                        String refNum = paymentAmountNode.getText().toString() + convertTo24HoursFormat(trimmedPaymentTime).replace(":", "") + formattedDate.replace("-", "");

                        if (!transactionDatabaseHandler.checkTransactionExistence(refNum, formattedDate)) {
                            saveToDb(formattedDate, paymentTimeNode.getText().toString(), paymentAmountNode.getText().toString(), refNum, combinedDateTime, "false");
                        } else if (transactionDatabaseHandler.checkTransactionExistence(refNum, formattedDate)) {
                            transactions = transactionDatabaseHandler.getTransaction();
                            for (int k = 0; k < transactions.size(); k++) {
                                if (transactions.get(k).isApiSent) {
                                    Log.i(TAG, "Api already sent.");
                                } else if (!transactions.get(k).isApiSent) {
                                    postData postData = new postData();
                                    postData.execute(Double.parseDouble(transactions.get(k).getAmount()), transactions.get(k).combinedDateTime, transactions.get(k).getReferenceNo());

                                    if (posted) {
                                        transactionDatabaseHandler.updateTransactionsIsSent("true", transactions.get(k).getReferenceNo(), transactions.get(k).getCombinedDateTime());
                                    } else {
                                        Log.i(TAG, "api not posted yet.");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {

        }
    }

    private class postData extends AsyncTask<Object, Void, Void> {
        @Override
        protected Void doInBackground(Object... params) {
            try {
                amount = (Double) params[0];
                datetime = (String) params[1];
                referenceNo = (String) params[2];

                URL url = new URL("https://bo.rapidpays.com/rapidpay_api/get_qrcode_notification");
//        URL url = new URL("https://demo.rapidpays.com/rapidpay_api/get_tng_notification");
//        URL url = new URL("http://192.168.0.184/bo.rapidpays.com/index.php/rapidpay_api/get_qrcode_notification");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                conn.setRequestProperty("Accept","application/json");

                JSONArray jsonArray = new JSONArray();
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("SecretKey", "9dc81d4e75a2a7bdda05f88abf22d5f20fc6fcf5");
                jsonObject.put("Amount", amount);
                jsonObject.put("Datetime", datetime);
                jsonObject.put("ReferenceNumber", referenceNo);
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

        @Override
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);
            posted = true;
        }
    }


    private void saveToDb(String date, String time, String amount, String referenceNo, String combinedDateTime, String isApiSent) {
        KasikornModel transactionModel = new KasikornModel();

        transactionModel.setDate(date);
        transactionModel.setTime(time);
        transactionModel.setAmount(amount);
        transactionModel.setReferenceNo(referenceNo);
        transactionModel.setCombinedDateTime(combinedDateTime);
        transactionModel.setApiSent(Boolean.parseBoolean(isApiSent));

        transactionDatabaseHandler.addTransactionEntry(transactionModel);

    }

    public String convertTo24HoursFormat(String twelveHourTime)
            throws ParseException {
        return TWENTY_FOUR_TF.format(TWELVE_TF.parse(twelveHourTime));
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "onInterrupt: interrupted");

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public int startServiceForeground() {
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Test Title")
                .setContentText("Test Content")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MAX);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
//        notificationManager.notify(123123, builder.build());
        startForeground(111111, builder.build());

        return START_STICKY;
    }


    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            String description = "Simple Channel Description";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public static void disableSSLCertificateChecking() {
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                // Not implemented
            }

            @Override
            public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                // Not implemented
            }
        } };

        try {
            SSLContext sc = SSLContext.getInstance("TLS");

            sc.init(null, trustAllCerts, new java.security.SecureRandom());

            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() { @Override public boolean verify(String hostname, SSLSession session) { return true; } });
            Log.i(TAG, "disableSSLCertificateChecking");
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

}
