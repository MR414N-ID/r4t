#!/bin/bash
echo "======================================"
echo "   RAT PAYLOAD AUTO-COMPILER (JAVA->SMALI) "
echo "======================================"
if ! command -v wget &> /dev/null; then sudo apt install wget -y; fi
if ! command -v javac &> /dev/null; then echo "❌ Install JDK dulu"; exit 1; fi

mkdir -p payload_src/com/system/update
mkdir -p payload_smali
cd payload_src/com/system/update
rm -f *.class classes.dex

if [ ! -f r8.jar ]; then wget -q https://maven.google.com/com/android/tools/r8/8.3.37/r8-8.3.37.jar -O r8.jar; fi
if [ ! -f baksmali.jar ]; then wget -q https://github.com/baksmali/smali/releases/download/v2.5.2/baksmali-2.5.2.jar -O baksmali.jar; fi
if [ ! -f android.jar ]; then wget -q https://github.com/Sable/android-platforms/raw/master/android-30/android.jar; fi

echo "[~] Mengcompile Java..."
javac -cp "android.jar:." --release 8 *.java
if [ $? -ne 0 ]; then echo "❌ GAGAL javac."; exit 1; fi

echo "[~] Mengubah ke DEX..."
java -cp r8.jar com.android.tools.r8.D8 --lib android.jar *.class --output .
if [ $? -ne 0 ]; then echo "❌ GAGAL DEX."; exit 1; fi

echo "[~] Mengubah ke Smali..."
java -jar baksmali.jar d classes.dex -o smali_out
if [ $? -ne 0 ]; then echo "❌ GAGAL Smali."; exit 1; fi

echo "[~] Memindahkan file..."
cp -r smali_out/com ../../../../payload_smali/
echo " ✅ COMPILE SUKSES! "
