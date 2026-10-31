# BT Chat — Bluetooth se chat aur photo bhejne wala Android app

Internet ke bina, do Android phones Bluetooth se jud kar text message aur photos bhej sakte hain.

## Features
- Text chat (Hindi/English/emoji sab chalega)
- Gallery se photo bhejna (progress bar ke saath)
- Aayi hui photo par tap karke bada dekhna aur Gallery mein save karna
- Android 6 se Android 14+ tak

## APK kaise banaye
1. Computer par **Android Studio** install karo (free): https://developer.android.com/studio
2. Yeh zip extract karo.
3. Android Studio → **File → Open** → `BluetoothChat` folder chuno.
4. Gradle sync hone do (pehli baar internet chahiye, kuch minute lagenge).
5. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**
6. APK yahan milega: `app/build/outputs/apk/debug/app-debug.apk`
7. Yeh APK dono phones mein copy karke install karo (Settings mein "Unknown sources" allow karna padega).

Ya phone ko USB se jod kar (USB debugging ON) Android Studio mein seedha ▶ Run dabao.

## Kaise use kare
1. Dono phones ki **Bluetooth settings** mein jaakar ek baar dono ko **pair** kar lo.
2. Dono phones par app kholo, permissions **Allow** karo.
3. **Phone A**: `Wait` dabao (yeh doosre phone ka intezaar karega).
4. **Phone B**: `Connect` dabao → list mein Phone A chuno.
5. Upar "Connected" dikhega — ab message likho ya 🖼 button se photo bhejo.

## Kuch baatein
- Bluetooth ki range lagbhag 10 metre hoti hai.
- Photo bhejne se pehle app usse chhota (1600px) kar deta hai, phir bhi ek photo mein 5–20 second lag sakte hain — Bluetooth slow hota hai.
- Chat app band karne par mit jaati hai (save nahi hoti). Photos ko "Gallery mein save karo" se rakh sakte ho.

## Files
- `MainActivity.kt` — chat screen, buttons, photo bhejna/dikhana
- `BluetoothService.kt` — Bluetooth connection aur data bhejna/lena
- `DeviceListActivity.kt` — paired devices ki list
- `MessageAdapter.kt` — chat bubbles
