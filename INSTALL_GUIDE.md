# Installation Troubleshooting Guide

## Method 1: Install via ADB (Most Reliable - Shows Actual Errors)

1. **Connect phone via USB** with USB debugging enabled
2. **Open Android Studio Terminal** (or Command Prompt)
3. **Check if phone is detected:**
   ```
   adb devices
   ```
   (Should show your device)

4. **Uninstall any existing version first:**
   ```
   adb uninstall com.gkohn11.spellcheckkeyboard
   ```

5. **Install the APK:**
   ```
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```
   The `-r` flag replaces existing installation

6. **If you get an error, it will show the actual problem!**

## Method 2: Check for Common Issues

### Issue: "App not installed" or "Package appears to be corrupt"
- **Solution:** Uninstall any existing Simple Keyboard first
- Settings → Apps → Simple Keyboard → Uninstall

### Issue: "Installation failed" or "App not installed as package appears to be invalid"
- **Solution:** Rebuild the APK
  - Build → Clean Project
  - Build → Rebuild Project  
  - Build → Build APK(s)

### Issue: Installation hangs/stuck
- **Solution:** 
  - Cancel and try ADB method (Method 1)
  - Check phone storage (need at least 50MB free)
  - Restart phone and try again

### Issue: "Unknown source blocked"
- **Solution:** 
  - Settings → Security → Enable "Install from unknown sources"
  - Or Settings → Apps → Special access → Install unknown apps
  - Enable for your file manager/browser

## Method 3: Install Directly from Android Studio

1. **Connect phone via USB** (USB debugging enabled)
2. **In Android Studio:**
   - Click the **Run** button (green play icon)
   - Or: **Run → Run 'app'**
   - Select your phone from the device list
3. **Android Studio will build and install automatically**

## Method 4: Check APK File

1. **Verify APK exists:**
   - Location: `app\build\outputs\apk\debug\app-debug.apk`
   - File size should be a few MB (not 0 bytes)

2. **Try building again if file seems wrong:**
   - Build → Clean Project
   - Build → Build APK(s)

## Method 5: Phone-Specific Issues

### If phone is from Spain/Europe:
- Some phones have extra security
- Try: Settings → Security → Disable "Play Protect" temporarily
- Or: Settings → Apps → Google Play Services → Disable "Verify apps"

### If phone is Samsung:
- May need to enable "Install unknown apps" per app
- Settings → Apps → [Your file manager] → Install unknown apps → Enable

### If phone is Xiaomi:
- Settings → Additional settings → Privacy → Enable "Install via USB"
- May need to disable MIUI optimization

## Getting Error Messages

**The ADB method (Method 1) is best because it shows actual error messages!**

Run this and share the error:
```
adb install app\build\outputs\apk\debug\app-debug.apk
```

