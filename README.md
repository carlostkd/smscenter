
# 📡 SMSCenter Detective

> 🕵️‍♂️ **Unmask the SMS Sender!**  
> Reveal the hidden SMSC (SMS Service Center) number in every incoming message so you can tell **where** 

that text really came from — and keep phishers, scammers & mystery numbers at bay.

---

## 🤔 Why care about the SMSC?

* Every SMS travels through a **Service Center**.  
* That center’s number (`+447785016005` → UK, Vodafone 🇬🇧, for example) tells you:
  * Which **operator** handled it  
  * Which **country** it originated from  
* If the SMSC doesn’t match the story… **something fishy!** 🐟

---

## 🏗 What’s in this repo?

| Item | Folder / File | What it does |
|------|---------------|--------------|
| **Android APK** | `smscviewer.apk` | Touch‑friendly app that lists inbox + sent SMS with colored SMSC field, filters, CSV export. |
| **PowerShell Script** | `smsc.ps1` | Reads SMS over ADB, pretty‑prints + exports on Windows. |
| **Linux/Mac Shell Script** | `smsc.sh` | Same as above but pure bash. |
| **Linux/Mac Shell Script** | `gui_smsc.sh` | Same as above but with a GUI |


---

## 📲 Installing the Android App (sideload)

1. Enable **“Install unknown apps”** for your browser or file manager.  
2. Copy `smsviewer.apk` to your phone.  
3. Tap it → Install.  
4. Grant the **SMS permission** on first launch.  
   > ☝️ *Google Play restrictions block SMS apps that aren’t full messaging clients, so we distribute via sideload.*

### Why no Play Store?
Because Google *really* dislikes apps that peek at SMS unless they **are** the default SMS handler. We only read, never send — so sideloading is the friendly workaround. 🤝

---


### 🔐 Verifying the APK (Optional, but smart!)

To confirm the APK hasn’t been tampered with, you can verify its checksum.

#### On Windows (PowerShell):

```powershell
Get-FileHash Smscviewer.apk -Algorithm SHA256
```

#### On Linux/macOS:

```bash
sha256sum SmsCenterDetective.apk
```

Compare the output with the original SHA-256 hash:

```
SHA-256: ccef372f7976a79bf96389bf333d65935fa8090116594100d9f19d652b518b4c

If the hash matches ✅ — you’re good. If not ⚠️ — don’t install it.


---

## 🪟 Windows PowerShell (+ADB)

```powershell
cd desktop

powershell -ExecutionPolicy Bypass -File sms.ps1

on linux

pwsh -ExecutionPolicy Bypass -File sms.ps1
```

* Prompts for counts & CSV export  
* Outputs in the console **and** writes `sms_export.csv` next to the script.  
* Needs ADB in `%PATH%` and USB‑debugging ON.

---

## 🐚 Bash / macOS / Linux

```bash
sudo chmod +x smsc.sh
./smsc.sh
```

Same features, pure bash.

---

## 🔒 Privacy

* **Everything runs locally.**  
* **Zero** network calls.  
* Your messages stay on your device — the only “cloud” here is the one outside ☁️😎



## 🙌 Credits

  
* myself (obviously....)  
* Coffee ☕ and late‑night debugging

---

*Detect those shady texts & sleep better!* 😴  
**— SMSCenter Detective by @carlostkd 2025**
