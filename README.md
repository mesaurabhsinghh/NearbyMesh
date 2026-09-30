# 📡 NearbyMesh - Zero-Internet P2P & Multi-Hop Mesh Network

> **NearbyMesh** — A Zero-Internet Decentralized P2P Mesh Communication App for Android! Chatting, sending gigabytes of files, and making voice calls even when cellular networks fail, mobile towers go down, or in remote off-grid areas with **0 MB internet**!

**NearbyMesh** is a pure native Android application (Kotlin + Jetpack Compose) designed for offline, peer-to-peer (P2P) mesh networking. It enables nearby phones to discover each other, chat with end-to-end encryption, and transfer large files at high speeds without cellular internet, Wi-Fi routers, or cloud servers (**0 MB Data Used**).

---

## 🎨 Screens & Features (Matching Reference UI 100%)

### 1. 🌟 Splash Screen (`SplashScreen.kt`)
- Cosmic mesh globe graphic with glowing pulses.
- Brand Title: **NearbyMesh** ("Connect Beyond Internet").
- Core capability pills: *No Internet Needed*, *Private & Secure*, *Share Everything Nearby*.
- "Get Started ➔" call to action.

### 2. 🏠 Home & Device Discovery (`HomeScreen.kt`)
- Top Logo: **NearbyMesh** ("No Internet. Still Together").
- Central Animated Radar Disc: Concentric range rings with glowing emerald sweep and "Scan for nearby devices".
- 4 Quick Action Cards:
  - 💬 **Chat**
  - 📁 **Send Files**
  - 👥 **Create Group**
  - 📥 **Receive**
- **Nearby Devices List**: Live nodes (Rahul, Priya, Aman's Phone, Device-92, Laptop) with real-time distance (`2.3 m away`), Wi-Fi signal strength bars, and tap-to-inspect.

### 3. 🕸️ Mesh Network Constellation (`MeshNetworkScreen.kt`)
- Central "You" node connected to surrounding nodes in a radial constellation map.
- Connection indicators:
  - **Direct Connection**: Solid emerald line.
  - **Relay Connection**: Cyan dashed line.
- Device count status banner: *"5 devices in mesh • All connected locally • Extending range"*.
- Live switches:
  - **Mesh Mode**: Automatically relay messages for others.
  - **Discovery**: Visible to nearby devices.

### 4. 👤 Device Profile & Quick Connect (`DeviceProfileSheet.kt`)
- Glowing avatar, device name (e.g. `Rahul's Phone`), handle (`@rahul_device`), status quote (*"Let's stay connected"*).
- 4 Metric Badges:
  - 📍 `2.3 m Distance`
  - 🔋 `92% Battery`
  - 📱 `Android 14 Device`
  - 📶 `High Signal`
- Direct actions: **Chat**, **Send Files**, **View Shared Files**, **Pair & Connect**.

### 5. 💬 Chat Interface (`ChatDetailScreen.kt`)
- Top Bar: Avatar, Name (`Rahul`), `● Online (Mesh)` status, Audio call & options.
- Rich Offline Message Bubbles:
  - Incoming clean bubbles with timestamps.
  - Outgoing emerald green bubbles with double-tick delivery status.
  - 🌄 **Photo Cards**: Image preview with caption (*"Beautiful place ❤️"*).
  - 🎙️ **Voice Notes**: Waveform player with Play/Pause button and `0:18` duration.
  - 📍 **Offline Location Cards**: Map pin card (*"Main location share kar raha hoon (offline)"*).
- Bottom Input Bar: Attachment `+` button, text input, mic button for voice recording.

### 6. 📤 Send Files (`SendFilesScreen.kt`)
- Category Filter Tabs: **Files**, **Photos**, **Videos**, **Apps**, **Folders**.
- Selected Files List with colored file badges:
  - `IMG_20250924.jpg` (2.4 MB)
  - `Video_4K.mp4` (1.8 GB)
  - `Notes.pdf` (1.2 MB)
  - `App.apk` (54 MB)
  - `Project.zip` (320 MB)
- "Send to" Target Recipient Selector Card (`Rahul • 2.3 m away`).
- Bottom Floating Action Button: **"Send 5 Files (~ 2.1 GB)"**.

### 7. ⏱️ Transfer Progress (`TransferProgressScreen.kt`)
- Large Circular Progress Gauge:
  - `78%`
  - `1.64 GB / 2.1 GB`
  - `42 MB/s` Speed
- Queue List with individual progress bars:
  - Active file with live progress bar.
  - Completed files with green checkmark.
  - Waiting files.
- Bottom Stats Card: `42 MB/s Speed`, `25 sec Time Left`, `5 Files`.

### 8. 📥 Receive File (`ReceiveFileScreen.kt`)
- Animated receiving halo ring around file icon.
- Status: *"Receiving... Video_4K.mp4 (1.8 GB)"*.
- Live speed & ETA: `38 MB/s • 45 seconds left` (`52%`).
- Pause and Cancel controls.

### 9. 🚨 Emergency Mode (`EmergencyScreen.kt`)
- Concentric red shockwave rings expanding outwards.
- Large tactile **SOS** button.
- Status: *"Broadcasting to nearby devices... 'Need medical assistance'"*.
- 3 Emergency Action Cards:
  1. `Send Emergency Message` (Alert nearby people)
  2. `Share Live Location` (Works offline)
  3. `Emergency File Share` (Share medical info)
- Start / Stop Broadcast button.

### 10. ⚙️ Settings & Customization (`SettingsScreen.kt`)
- User Profile: Avatar, Name (`Saurabh`), Handle (`@saurabh_device`), Status (*"Stay connected, offline"*).
- Settings items:
  - 👁️ **Visibility**: Anyone nearby can find you (toggle).
  - 📱 **Device Name**: Tap to rename device.
  - 🔒 **Security**: End-to-end encryption keys.
  - 📁 **Storage**: Received files directory.
  - ⚡ **Mesh Settings**: Relay messages for others (toggle).
  - 🌓 **Appearance**: Instant Clean Light / Dark Theme toggle.
  - 🔋 **Battery Optimization**: Background mesh permissions.
  - ℹ️ **About**: NearbyMesh v1.0.0.

---

## 🛠️ Build & Installation

Ready-to-install Debug APK:
```
c:\NearbyMesh\app\build\outputs\apk\debug\app-debug.apk
```

Install via ADB:
```powershell
$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe install -r "c:\NearbyMesh\app\build\outputs\apk\debug\app-debug.apk"
```
>>>>>>> 268c4ce (Initial commit: NearbyMesh - Zero-Internet P2P & Multi-Hop Decentralized Mesh App)
