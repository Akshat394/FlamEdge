# Install APK on Android 14 Device - Complete Guide

## 📱 Your APK is Ready!

**APK Location**: `E:\Flam Assignment\edge-viewer\app\build\outputs\apk\debug\app-debug.apk`

**File Size**: ~13 MB (approximately)

**Compatibility**: 
- ✅ Works on Android 14 (API 34)
- ✅ Minimum Android 7.0 (API 24)
- ✅ Supports ARM64 and ARM devices

---

## 🔧 Method 1: Install via USB (Recommended)

### **Step 1: Enable USB Debugging on Your Phone**

1. Go to **Settings** → **About phone**
2. Tap **Build number** 7 times until you see "You are now a developer!"
3. Go back to **Settings** → **System** → **Developer options**
4. Enable **USB debugging**
5. Enable **Install via USB** (if available)
6. Connect your phone to your computer via USB cable

### **Step 2: Transfer APK to Your Phone**

**Option A: Copy File Manually**
1. Copy `app-debug.apk` from your computer
2. Connect phone via USB
3. Transfer the APK to your phone's Downloads folder
4. Disconnect and install on phone (see Step 3 below)

**Option B: Use ADB (If you have it set up)**
```powershell
cd "E:\Flam Assignment\edge-viewer"
C:\Users\dell\AppData\Local\Android\Sdk\platform-tools\adb.exe install app\build\outputs\apk\debug\app-debug.apk
```

### **Step 3: Install on Your Phone**

1. On your phone, open **Files** or **My Files** app
2. Navigate to **Downloads** folder (or wherever you saved the APK)
3. Tap on `app-debug.apk`
4. If you see "Install blocked", tap **Settings** and enable **Install unknown apps**
5. Tap **Install**
6. Wait for installation to complete
7. Tap **Open** to launch the app!

---

## 📧 Method 2: Install via Email/Cloud

### **Step 1: Upload APK**

1. Upload `app-debug.apk` to:
   - Google Drive
   - Dropbox
   - Email it to yourself
   - Any cloud storage service

### **Step 2: Download on Phone**

1. Open the link/email on your Android phone
2. Download the APK file
3. Follow **Method 1, Step 3** to install

---

## 🔐 Method 3: Install via QR Code (Advanced)

1. Upload APK to a file sharing service
2. Generate a QR code with the download link
3. Scan QR code with your phone
4. Download and install

---

## ⚠️ Important: Allow Unknown Apps on Android 14

Android 14 has stricter security. Here's how to allow installation:

1. When you tap the APK file, you might see **"Install blocked"**
2. Tap **Settings** or **More details**
3. Enable **Install unknown apps** for that app (Files, Chrome, etc.)
4. Go back and tap **Install** again

### **For Specific Apps:**

**For Chrome/Edge (if downloading from browser):**
- Settings → Apps → Chrome → Install unknown apps → Enable

**For Files/My Files app:**
- Settings → Apps → Files → Install unknown apps → Enable

---

## ✅ Verification

After installation, you should:
1. ✅ See "EdgeViewer" app icon in your app drawer
2. ✅ App opens without errors
3. ✅ Camera permission prompt appears (grant it)
4. ✅ App can access camera

---

## 🚨 Troubleshooting

### **Problem: "App not installed" or "Installation failed"**

**Solutions:**
1. Make sure you uninstalled any previous version first
2. Enable **Install unknown apps** for the file manager app
3. Check if your phone has enough storage space
4. Make sure the APK wasn't corrupted during transfer

### **Problem: "Package appears to be corrupt"**

**Solutions:**
1. Re-download/re-transfer the APK file
2. Make sure file transfer completed successfully
3. Try building a fresh APK:
   ```powershell
   cd "E:\Flam Assignment\edge-viewer"
   .\gradlew.bat clean assembleDebug
   ```

### **Problem: Camera doesn't work**

**Solutions:**
1. Grant camera permission when prompted
2. Check Settings → Apps → EdgeViewer → Permissions → Camera
3. Make sure it's enabled

### **Problem: App crashes on startup**

**Solutions:**
1. Check if your device is ARM64 or ARM (not x86)
2. Make sure Android version is 7.0 or higher
3. Check device logs for errors (use `adb logcat` if connected via USB)

---

## 📋 Quick Checklist

- [ ] APK file copied to phone
- [ ] USB debugging enabled (if using Method 1)
- [ ] Install unknown apps enabled
- [ ] APK installation completed
- [ ] Camera permission granted
- [ ] App opens and works correctly

---

## 🎯 Next Steps

Once installed:
1. Open the app
2. Grant camera permissions
3. Test the camera features
4. Try switching between cameras
5. Export processed frames

---

## 📝 Notes

- This is a **debug APK** (for testing)
- For production release, you'd need a **signed release APK**
- The app works offline once installed
- All camera features require camera permission

Enjoy your app! 🎉

