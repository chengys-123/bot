package com.example.myapplication.SQLiteDatabaseHandler;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.example.myapplication.models.databaseModels.KasikornModel;
import com.example.myapplication.models.databaseModels.TelcoDatabaseModel;

import java.util.ArrayList;

public class TelcoDatabaseHandler extends SQLiteOpenHelper {
  private static final String TAG = "access" ;
  public static String DATABASENAME = "telcosqlite";
  public static String TELCOTABLE = "telco";

  private ArrayList<TelcoDatabaseModel> telcoList = new ArrayList<TelcoDatabaseModel>();
  Context c;

  public TelcoDatabaseHandler(Context context) {
    super(context, DATABASENAME, null, 31);
    c = context;
  }
  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE if not exists telco(_id INTEGER PRIMARY KEY AUTOINCREMENT DEFAULT 1,"
            + "phoneNumber" + " TEXT,"
            + "telcoType" + " TEXT,"
            + "tranID" + " TEXT,"
            + "reloadPIN" + " TEXT,"
            + "isSuccess" + " TEXT)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    db.execSQL("DROP TABLE IF EXISTS " + TELCOTABLE);
    onCreate(db);
  }

  public void addTelcoEntry(TelcoDatabaseModel telcoDatabaseModel) {
    SQLiteDatabase db = this.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put("phoneNumber", telcoDatabaseModel.phoneNumber);
    contentValues.put("telcoType", telcoDatabaseModel.telcoType);
    contentValues.put("tranID", telcoDatabaseModel.tranID);
    contentValues.put("reloadPIN", telcoDatabaseModel.reloadPIN);
    contentValues.put("isSuccess", telcoDatabaseModel.isSuccess);

    db.insert("telco", null, contentValues);

    Log.i(TAG, "Entry inserted: " + contentValues);
  }

  @SuppressLint("Range")
  public ArrayList<TelcoDatabaseModel> getTelco(String telcoType) {
    telcoList.clear();
    SQLiteDatabase db = this.getWritableDatabase();
    Cursor cursor = db.rawQuery("select * from telco where telcoType = ?", new String[] {telcoType});

    if (cursor.getCount() != 0) {
      if (cursor.moveToFirst()) {
        do {
          TelcoDatabaseModel telcoDatabaseModel = new TelcoDatabaseModel();
          telcoDatabaseModel.telcoID = cursor.getInt(cursor.getColumnIndex("_id"));
          telcoDatabaseModel.phoneNumber = cursor.getString(cursor.getColumnIndex("phoneNumber"));
          telcoDatabaseModel.telcoType = cursor.getString(cursor.getColumnIndex("telcoType"));
          telcoDatabaseModel.tranID = cursor.getString(cursor.getColumnIndex("tranID"));
          telcoDatabaseModel.reloadPIN = cursor.getString(cursor.getColumnIndex("reloadPIN"));
          telcoDatabaseModel.isSuccess = Boolean.parseBoolean(cursor.getString(cursor.getColumnIndex("isSuccess")));

          telcoList.add(telcoDatabaseModel);
        } while (cursor.moveToNext());
      }
    }
    cursor.close();

    return telcoList;
  }

  @SuppressLint("Range")
  public String getPhoneNumber(String telcoType) {
    SQLiteDatabase db = this.getWritableDatabase();
    String result = "";
    Cursor cursor = db.rawQuery("select phoneNumber from telco where telcoType = ?", new String[] {telcoType});

    if (cursor.getCount() != 0) {
      if (cursor.moveToFirst()) {
        do {
          result = cursor.getString(cursor.getColumnIndex("phoneNumber"));
        } while (cursor.moveToNext());
      }
    }
    cursor.close();

    return result;
  }


  @SuppressLint("Range")
  public ArrayList<TelcoDatabaseModel> updateTelco(String phoneNumber, String isBalanceCalled, String telcoType) {
    telcoList.clear();
    SQLiteDatabase db = this.getWritableDatabase();
    Cursor cursor = db.rawQuery("update telco set phoneNumber = ? and isBalanceCalled = ? where telcoType = ?",
            new String[] {phoneNumber, isBalanceCalled, telcoType});

    if (cursor.getCount() != 0) {
      if (cursor.moveToFirst()) {
        do {
          TelcoDatabaseModel telcoDatabaseModel = new TelcoDatabaseModel();
          telcoDatabaseModel.telcoID = cursor.getInt(cursor.getColumnIndex("_id"));
          telcoDatabaseModel.phoneNumber = cursor.getString(cursor.getColumnIndex("phoneNumber"));
          telcoDatabaseModel.telcoType = cursor.getString(cursor.getColumnIndex("telcoType"));
          telcoDatabaseModel.reloadPIN = cursor.getString(cursor.getColumnIndex("reloadPIN"));
          telcoDatabaseModel.isSuccess = Boolean.parseBoolean(cursor.getString(cursor.getColumnIndex("isSuccess")));

          telcoList.add(telcoDatabaseModel);
        } while (cursor.moveToNext());
      }
    }
    cursor.close();
    Log.i(TAG, "updated!" );
    return telcoList;
  }

  @SuppressLint("Range")
  public ArrayList<TelcoDatabaseModel> updateIsSuccess(boolean isSuccess, String phoneNumber, String telcoType, String tranID) {
    telcoList.clear();
    SQLiteDatabase db = this.getWritableDatabase();
    Cursor cursor = db.rawQuery("update telco set isSuccess = ? where phoneNumber = ? and telcoType = ? and tranID = ?",
            new String[] {String.valueOf(isSuccess), phoneNumber, telcoType, tranID});

    if (cursor.getCount() != 0) {
      if (cursor.moveToFirst()) {
        do {
          TelcoDatabaseModel telcoDatabaseModel = new TelcoDatabaseModel();
          telcoDatabaseModel.telcoID = cursor.getInt(cursor.getColumnIndex("_id"));
          telcoDatabaseModel.phoneNumber = cursor.getString(cursor.getColumnIndex("phoneNumber"));
          telcoDatabaseModel.tranID = cursor.getString(cursor.getColumnIndex("tranID"));
          telcoDatabaseModel.telcoType = cursor.getString(cursor.getColumnIndex("telcoType"));
          telcoDatabaseModel.isSuccess = Boolean.parseBoolean(cursor.getString(cursor.getColumnIndex("isSuccess")));
          telcoList.add(telcoDatabaseModel);
        } while (cursor.moveToNext());
      }
    }
    cursor.close();
    Log.i(TAG, "updated is balanced called!" );
    return telcoList;
  }


  public boolean checkTelcoExistence(String phoneNumber, String telcoType) {
    SQLiteDatabase db = this.getWritableDatabase();
    Cursor cursor = db.rawQuery("select * from telco where phoneNumber = ? and telcoType = ?", new String[] {phoneNumber, telcoType});
    if(cursor.getCount() <= 0){
      cursor.close();
      return false;
    }
    cursor.close();

    return true;
  }
}
