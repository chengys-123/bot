package com.example.myapplication.SQLiteDatabaseHandler;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.example.myapplication.models.databaseModels.KasikornModel;

import java.util.ArrayList;

public class KasikornDatabaseHandler extends SQLiteOpenHelper {
    private static final String TAG = "access" ;
    public static String DATABASENAME = "androidkasikornsqlite";
    public static String KASIKORNTABLE = "kasikorn";

    private ArrayList<KasikornModel> tansactionList = new ArrayList<KasikornModel>();
    Context c;

    public KasikornDatabaseHandler(Context context) {
        super(context, DATABASENAME, null, 31);
        c = context;
    }
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE if not exists kasikorn(_id INTEGER PRIMARY KEY AUTOINCREMENT DEFAULT 1,"
                + "date" + " TEXT,"
                + "time" + " TEXT,"
                + "amount" + " TEXT,"
                + "referenceNo" + " TEXT,"
                + "combinedDateTime" + " TEXT,"
                + "isApiSent" + " TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + KASIKORNTABLE);
        onCreate(db);
    }

    public void addTransactionEntry(KasikornModel transactionModel) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("date", transactionModel.date);
        contentValues.put("time", transactionModel.time);
        contentValues.put("amount", transactionModel.amount);
        contentValues.put("referenceNo", transactionModel.referenceNo);
        contentValues.put("combinedDateTime", transactionModel.combinedDateTime);
        contentValues.put("isApiSent", transactionModel.isApiSent);

        db.insert("kasikorn", null, contentValues);

        Log.i(TAG, "Entry inserted: " + contentValues);
    }

    @SuppressLint("Range")
    public ArrayList<KasikornModel> getTransaction() {
        tansactionList.clear();
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("select * from kasikorn", null);

        if (cursor.getCount() != 0) {
            if (cursor.moveToFirst()) {
                do {
                    KasikornModel transactionModel = new KasikornModel();
                    transactionModel.transactionID = cursor.getInt(cursor.getColumnIndex("_id"));
                    transactionModel.date = cursor.getString(cursor.getColumnIndex("date"));
                    transactionModel.time = cursor.getString(cursor.getColumnIndex("time"));
                    transactionModel.amount = cursor.getString(cursor.getColumnIndex("amount"));
                    transactionModel.referenceNo = cursor.getString(cursor.getColumnIndex("referenceNo"));
                    transactionModel.combinedDateTime = cursor.getString(cursor.getColumnIndex("combinedDateTime"));
                    transactionModel.isApiSent = Boolean.parseBoolean(cursor.getString(cursor.getColumnIndex("isApiSent")));

                    tansactionList.add(transactionModel);
                } while (cursor.moveToNext());
            }
        }
        cursor.close();

        return tansactionList;
    }

    @SuppressLint("Range")
    public ArrayList<KasikornModel> updateTransactionsIsSent(String isApiSent, String referenceNo, String combinedDateTime) {
        tansactionList.clear();
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("update kasikorn set isApiSent = ? where referenceNo = ? and combinedDateTime = ?",
                new String[] {isApiSent, referenceNo, combinedDateTime});

        if (cursor.getCount() != 0) {
            if (cursor.moveToFirst()) {
                do {
                    KasikornModel transactionModel = new KasikornModel();
                    transactionModel.transactionID = cursor.getInt(cursor.getColumnIndex("_id"));
                    transactionModel.amount = cursor.getString(cursor.getColumnIndex("amount"));
                    transactionModel.date = cursor.getString(cursor.getColumnIndex("date"));
                    transactionModel.time = cursor.getString(cursor.getColumnIndex("time"));
                    transactionModel.combinedDateTime = cursor.getString(cursor.getColumnIndex("combinedDateTime"));
                    transactionModel.referenceNo = cursor.getString(cursor.getColumnIndex("referenceNo"));
                    transactionModel.isApiSent = Boolean.parseBoolean(cursor.getString(cursor.getColumnIndex("isApiSent")));

                    tansactionList.add(transactionModel);
                } while (cursor.moveToNext());
            }
        }
        cursor.close();
        Log.i(TAG, "updated!" );
        return tansactionList;
    }

    public boolean checkTransactionExistence(String referenceNo) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("select * from kasikorn where referenceNo = ?", new String[] {referenceNo});
        if(cursor.getCount() <= 0){
            cursor.close();
            return false;
        }
        cursor.close();

        return true;
    }

    public boolean validateIfTableHasData(String tableName){
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + tableName + " LIMIT 1",null);
        if(cursor.getCount() <= 0){
            cursor.close();
            return false;
        }
        cursor.close();

        return true;
    }
}
