# SafeGuard — On-Device Android Content Protection & Safety Engine

SafeGuard is a modern, privacy-first, on-device Android content filtering application built with Kotlin, Jetpack Compose, and the native Android `VpnService` framework. It provides always-on, high-throughput protection against adult, pornographic, and explicit websites across all web browsers without decrypting HTTPS traffic, collecting personal data, or sending browsing history to external servers.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Features](#2-features)
3. [Installation](#3-installation)
4. [Setup](#4-setup)
5. [How SafeGuard Works (VPN Explanation)](#5-how-safeguard-works-vpn-explanation)
6. [Domain Filtering](#6-domain-filtering)
7. [HTTPS Limitations (Explicitly Explained)](#7-https-limitations-explicitly-explained)
8. [Encrypted DNS (DoH/DoT) Limitations](#8-encrypted-dns-dohdot-limitations)
9. [Browser Compatibility](#9-browser-compatibility)
10. [Privacy Guarantee](#10-privacy-guarantee)
11. [Security Architecture](#11-security-architecture)
12. [Blocklist Updates](#12-blocklist-updates)
13. [Troubleshooting](#13-troubleshooting)
14. [Build Instructions](#14-build-instructions)
15. [Release Signing](#15-release-signing)
16. [Known Limitations](#16-known-limitations)

---

## 1. Overview

SafeGuard provides automated, silent, and seamless protection for Android devices against adult and inappropriate web material. Designed with the core philosophy **"Install Once. Stay Protected."**, SafeGuard acts as a local DNS and IP filter that operates directly on the device using Android's native `VpnService` APIs.

Unlike commercial parental control apps or corporate firewalls that monitor user behavior or inspect private communications, SafeGuard is **100% offline-first and privacy-preserving**:
- All threat intelligence and domain rule evaluations occur strictly on the device.
- Zero network telemetry, browsing activity, or device identifiers leave the user's phone.
- No Man-in-the-Middle (MITM) proxying or custom certificate authority (CA) installation is used, ensuring bank-grade cryptographic security remains intact.

---

## 2. Features

- **Native Android & Jetpack Compose**: Built purely with Kotlin, Material 3 design, Coroutines, and Flow for fluid, energy-efficient performance.
- **Instant First-Run Onboarding**: 2-step onboarding explaining Android's VPN system permissions with transparent privacy disclosures.
- **Android VpnService Integration**: Establishes a local loopback tun0 interface to intercept DNS traffic on UDP port 53 without routing user data to a third-party server.
- **Sub-Millisecond Domain Matcher**: O(k) hierarchical domain lookup engine that handles exact domains, wildcard subdomains, and IDN/punycode internationalized domains.
- **Strict Whitelist Precedence**: Explicit user and system whitelist rules always override blocklist rules (e.g., allow `sub.example.com` while blocking parent `example.com`).
- **Passive TLS SNI & QUIC Inspection**: Drops direct IP connection attempts to known adult hosts by inspecting the unencrypted Server Name Indication (SNI) header without decrypting payload data.
- **Enforced SafeSearch**: Intercepts DNS queries for Google, Bing, DuckDuckGo, and Yahoo, routing them directly to strict SafeSearch VIPs (`forcesafesearch.google.com`, `strict.bing.com`, `safe.duckduckgo.com`).
- **Real-Time Protection Statistics**: Categorized counts (Adult, Pornography, Explicit, NSFW, Other) with today, week, month, and all-time aggregates, plus persistent protection uptime tracking.
- **Non-Explicit Block Screen**: Displays a dignified, calm block notification with the reason, timestamp, and category. Strictly never renders adult images, thumbnails, or webpage screenshots.
- **PIN-Protected Settings & Disablement**: Secure PBKDF2/SHA-256 salted PIN protection preventing unauthorized tampering, setting modification, or protection disablement.
- **Boot & Background Persistence**: Android `RECEIVE_BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` broadcast receivers re-engage filtering automatically after device reboot or app updates.

---

## 3. Installation

### Requirements
- **Operating System**: Android 8.0 (Oreo / API Level 26) or higher. Fully compatible with Android 14, Android 15, and Android 16.
- **Architecture**: Universal (arm64-v8a, armeabi-v7a, x86_64, x86).
- **RAM / Storage**: Minimal (<35 MB storage, <25 MB runtime RAM).

### Sideloading APK
1. Download the release APK (`app-release.apk`) to your Android device.
2. Open Android **Settings** → **Security** → **Install unknown apps** and enable permission for your browser or file manager.
3. Tap the downloaded APK and select **Install**.

---

## 4. Setup

1. **Launch SafeGuard**: Open SafeGuard from your app launcher.
2. **Review Onboarding**: The first-run screen clearly outlines how the VPN works and explains our privacy guarantee. Tap **CONTINUE**.
3. **Grant Android VPN Permission**: SafeGuard triggers the standard Android system dialog:
   > *"SafeGuard wants to set up a VPN connection that allows it to monitor network traffic. Only accept if you trust the source."*
   Tap **OK / Allow**.
4. **Protection Active**: The status shield glows green: `🟢 PROTECTION ACTIVE — Your device is protected.`
5. **Always-On VPN (Recommended)**:
   For tamper-resistant protection that cannot be stopped from quick settings:
   - Go to Android **Settings** → **Network & internet** → **VPN**.
   - Tap the gear icon next to **SafeGuard**.
   - Enable **Always-on VPN** and optionally **Block connections without VPN**.

---

## 5. How SafeGuard Works (VPN Explanation)

SafeGuard creates a **local Android VPN loopback interface** (`tun0`) using the platform's `android.net.VpnService` class.

```
+-------------------------------------------------------------------------+
|                              Android Device                             |
|                                                                         |
|  +---------------------+        +------------------------------------+  |
|  | Web Browser / Apps  |        | SafeGuard App                      |  |
|  | (Chrome, Edge, etc) |        | - DomainFilter Engine              |  |
|  +----------+----------+        | - Blocklist Room DB                |  |
|             |                   | - SafeSearch Rewriter              |  |
|     DNS Query (Port 53)         +-----------------+------------------+  |
|             |                                     |                     |
|             v                                     |                     |
|    [ Virtual TUN Interface (10.1.10.1 / fd00:1::1) ]                    |
|             |                                                           |
|             +----------> SafeGuard Packet Worker                        |
|                               |                                         |
|                 Is domain in Adult Blocklist?                           |
|                        /             \                                  |
|                   YES /               \ NO                              |
|                      v                 v                                |
|             Return 0.0.0.0      Forward query to                        |
|             (Sinkhole / Block)  Upstream DNS (1.1.1.3 / CleanBrowsing)  |
+-------------------------------------------------------------------------+
```

### Why SafeGuard Uses a Local VPN
Android does not provide a system-wide firewall or DNS-interception hook for non-root applications other than `VpnService`. By initializing a local VPN:
1. SafeGuard configures an internal route for DNS packets (UDP/TCP port 53).
2. The packet worker reads raw IP packets directly from the `ParcelFileDescriptor`.
3. Allowed DNS requests are forwarded to family-safe upstream resolvers (e.g. Cloudflare Families `1.1.1.3` / `1.0.0.3` or CleanBrowsing).
4. Blocked adult domains are immediately synthesized into a DNS `0.0.0.0` (IPv4) or `::` (IPv6) response packet and written back into the TUN interface.
5. Non-DNS network packets pass directly to the physical interface without slowing down download speeds or internet throughput.

---

## 6. Domain Filtering

The core filter engine (`DomainFilter`) provides fast, deterministic decision-making:

### Evaluation Hierarchy
```
Input Hostname -> Normalize (Strip Scheme/Port/Paths, IDN Punycode, Lowercase)
      |
      +---> Check Whitelist Exact Match (e.g. "allowed.test") -> ALLOW
      |
      +---> Check Whitelist Suffix Match (e.g. "*.sub.example.com") -> ALLOW
      |
      +---> Check Blocklist Exact Match (e.g. "adult.test") -> BLOCK
      |
      +---> Check Blocklist Suffix Match (e.g. "*.pornhub.com") -> BLOCK
      |
      +---> Default -> ALLOW
```

### Match Rules & Hierarchy Protection
- **Subdomain Match**: Blocking `example.com` automatically blocks `www.example.com`, `video.example.com`, and `m.api.example.com`.
- **Evasion Resistance**: An attacker attempting `example.com.evil.com` will **NOT** match the rule for `example.com`. Suffix checks strictly require a boundary dot (`.`).
- **Whitelist Overrides**: If `example.com` is blocked, adding `sub.example.com` to the whitelist allows `sub.example.com` while maintaining the block on `example.com` and `www.example.com`.

### Developer Test Domains
SafeGuard provides built-in, non-harmful test domains for instant verification:
- `blocked.test` → **BLOCK** (Category: Adult)
- `adult-test.local` → **BLOCK** (Category: Pornography)
- `example-blocked.test` → **BLOCK** (Category: Explicit)
- `allowed.test` → **ALLOW** (Explicitly whitelisted)

---

## 7. HTTPS Limitations (Explicitly Explained)

Modern internet traffic is encrypted using Transport Layer Security (TLS/HTTPS). SafeGuard's design makes an uncompromising, transparent security trade-off:

### What SafeGuard CAN Do with HTTPS
- SafeGuard **reliably blocks known adult websites even when accessed over HTTPS** (e.g., `https://www.pornhub.com`).
- Because DNS resolution precedes the TLS handshake, SafeGuard sinkholes the DNS request before an HTTPS connection can ever begin.
- Even if the browser already cached the IP address, SafeGuard inspects the unencrypted **TLS ClientHello Server Name Indication (SNI)** header (RFC 6066) and resets the connection before TLS encryption completes.

### What SafeGuard CANNOT Do without HTTPS Decryption
- **In-Page Content Inspection**: SafeGuard cannot inspect individual web page contents, embedded images, text comments, or specific URL paths inside an allowed HTTPS domain (e.g., `https://generic-cloud-storage.com/adult-folder/video.mp4`).
- **Path-Level Filtering**: Because everything after the hostname is encrypted by TLS, network firewalls cannot see URL paths without decrypting the payload.

### Why SafeGuard Intentionally Does NOT Perform HTTPS Decryption (MITM)
1. **Security & Cryptographic Integrity**: Decrypting HTTPS requires generating a custom Root Certificate Authority (CA) and installing it into the Android System Trust Store. Doing so creates a single point of failure that compromises the device's cryptographic guarantees against eavesdropping.
2. **Application Pinning**: Modern banking apps, Google apps, and browsers use Certificate Pinning. MITM proxies break certificate validation, breaking essential banking and communication apps.
3. **Privacy First**: SafeGuard believes user privacy is paramount. Passwords, cookies, payment cards, and private messages should never be decrypted by a filtering app.

---

## 8. Encrypted DNS (DoH/DoT) Limitations

Modern browsers (Google Chrome, Microsoft Edge, Mozilla Firefox, Brave) and Android 9+ include **Encrypted DNS** features:
- **DNS-over-HTTPS (DoH)**: Encapsulates DNS lookups inside standard HTTPS traffic on port 443.
- **DNS-over-TLS (DoT)**: Encapsulates DNS lookups inside TLS on port 853 (used by Android Private DNS).

### How Encrypted DNS Bypasses Local VPN Filtering
When a browser sends queries directly to an external DoH resolver over an encrypted HTTPS tunnel, DNS packets bypass the standard operating system resolver and local UDP port 53. Consequently, local DNS filters cannot see or sinkhole the domain.

### SafeGuard Built-In Countermeasures
1. **Firefox Canary Drop**: SafeGuard blocks `use-application-dns.net`. Under Mozilla's official standard, an `NXDOMAIN` response for this domain tells Firefox that parental/enterprise controls are active, prompting Firefox to automatically disable DoH.
2. **DoT Port 853 Block**: SafeGuard drops outbound packets on port 853, prompting Android Private DNS to gracefully fall back to the VPN's provided DNS servers.
3. **DoH Bootstrap Sinkholing**: SafeGuard sinks domain lookups for major public DoH providers (`cloudflare-dns.com`, `dns.google`, `dns.quad9.net`, `doh.opendns.com`).

### Recommended Browser Settings for 100% Protection
If a browser is manually configured to use a hardcoded DoH IP address, disable Secure DNS in browser settings:

#### Google Chrome
1. Open Chrome → **Settings** (three dots).
2. Tap **Privacy and security** → **Use secure DNS**.
3. Toggle the switch to **OFF**, or select **"Use your current service provider"**.

#### Mozilla Firefox
1. Open Firefox → **Settings**.
2. Tap **Enhanced Tracking Protection** or **Network Settings**.
3. Under **DNS over HTTPS**, choose **Off** or **Default**.

#### Microsoft Edge
1. Open Edge → **Settings** → **Privacy and security**.
2. Scroll to **Use secure DNS** and set it to **Use current service provider** or toggle **Off**.

#### Brave Browser
1. Open Brave → **Settings** → **Brave Shields & privacy**.
2. Locate **Use secure DNS** and set to **Disabled** or **Current provider**.

---

## 9. Browser Compatibility

SafeGuard operates at the native Android kernel/network layer via `VpnService`. It filters traffic uniformly across all Android web browsers and web views:

| Browser | Supported | Notes |
| :--- | :---: | :--- |
| **Google Chrome** | ✅ Yes | Fully supported; respect system VPN DNS |
| **Mozilla Firefox** | ✅ Yes | Fully supported; honors `use-application-dns.net` canary |
| **Microsoft Edge** | ✅ Yes | Fully supported; DNS & SNI filtering active |
| **Brave Browser** | ✅ Yes | Fully supported; adult domain blocking active |
| **Samsung Internet** | ✅ Yes | Fully supported out-of-the-box |
| **Opera Browser** | ✅ Yes | Supported (ensure built-in Opera VPN is disabled) |
| **DuckDuckGo Browser** | ✅ Yes | Fully supported |
| **Android WebViews** | ✅ Yes | All in-app browser tabs (Gmail, Twitter, Reddit) protected |

*Note: Android allows only one active VPN at a time. If another third-party VPN app (e.g., ExpressVPN, NordVPN) is enabled, SafeGuard will notify the user.*

---

## 10. Privacy Guarantee

SafeGuard is built with privacy as a non-negotiable architectural invariant:

### What SafeGuard NEVER Accesses or Collects
- ❌ **No Passwords or Credentials**
- ❌ **No Cookies or Session Tokens**
- ❌ **No Private Messages or Chats**
- ❌ **No Banking or Financial Information**
- ❌ **No Emails or Document Content**
- ❌ **No Camera or Microphone Access** (Zero hardware media permissions)
- ❌ **No Contacts or Call Logs**
- ❌ **No Location Data (GPS or Cell-ID)**
- ❌ **No Web Browsing History Uploads**

### Transparent Data Storage
- SafeGuard performs all evaluations **in-memory and locally**.
- By default, **Store Blocked Domain Names is turned OFF**. Only anonymous numerical counters (e.g. *Blocked Today: 4*) are incremented.
- Users can clear all statistics and custom blocklists instantly via **Settings → Privacy & Data Management**.

---

## 11. Security Architecture

### Cryptographic PIN Protection
Settings and protection toggles can be locked with a 4 to 8 digit numeric PIN.
- Hashing: **PBKDF2 with HMAC-SHA256**, utilizing a 16-byte cryptographically secure random salt (`java.security.SecureRandom`) and 10,000 iterations.
- Brute-force resistance: Exponential backoff lockout enforced after 5 consecutive failed attempts.

### Safe Lifecycle Handling
- Foreground service with persistent notification (`POST_NOTIFICATIONS` permission with high priority channel).
- Dual `BootReceiver` hooks ensuring zero protection gaps during system startup.
- Graceful resource reclamation when the user explicitly pauses protection.

---

## 12. Blocklist Updates

SafeGuard ships with an offline baseline database containing over 50,000 adult and explicit domain definitions across 8 categories:
- **Adult**: General adult material and portals
- **Pornography**: Hardcore pornographic websites
- **Explicit**: Shock, extreme, and violent content
- **NSFW**: Not-Safe-For-Work imageboards and galleries
- **Adult Streaming**: Live webcam and interactive adult streaming platforms
- **Adult Community**: Adult forums, erotic stories, and dating portals
- **Adult Dating**: Casual encounter and adult dating services
- **Inappropriate / Other**: Malware and dangerous adult spam hosts

### Update Mechanism
- **WorkManager Auto-Updates**: Periodic background worker (`BlocklistUpdateWorker`) verifies threat feeds every 24 hours when connected to unmetered Wi-Fi.
- **Manual Sync**: Users can tap **Settings → Check for Updates** to pull the latest verified threat lists immediately.

---

## 13. Troubleshooting

### Issue: "Another VPN is active"
- **Cause**: Android supports only one active VPN connection at any time.
- **Resolution**: Open Android Quick Settings, tap the active VPN notification, and select **Disconnect**. Then open SafeGuard and tap **RECONNECT PROTECTION**.

### Issue: "A website is not blocked in Google Chrome"
- **Cause**: Google Chrome may have **Secure DNS (DoH)** enabled with an external provider.
- **Resolution**: In Chrome, go to **Settings → Privacy and security → Use secure DNS**, and toggle it **OFF** or set it to **"Use current service provider"**.

### Issue: "SafeGuard stops when swiped from Recent Apps"
- **Cause**: Aggressive battery optimization or task killer killing background services.
- **Resolution**:
  1. Open Android **Settings** → **Apps** → **SafeGuard** → **Battery**.
  2. Select **Unrestricted**.
  3. Enable **Always-on VPN** in Android VPN settings.

---

## 14. Build Instructions

### Prerequisites
- JDK 17 or JDK 21
- Android SDK Tools with Build-Tools 36.0.0
- Gradle 8.x+

### Steps to Build Debug APK
```bash
# Clone repository
git clone https://github.com/safeguard/safeguard-android.git
cd safeguard-android

# Compile and run unit tests
gradle :app:testDebugUnitTest

# Assemble Debug APK
gradle :app:assembleDebug
```
The resulting debug APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 15. Release Signing

To build a production release APK or Android App Bundle (AAB):

### 1. Generate an Upload Keystore
```bash
keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

### 2. Configure Environment Variables
Set the following environment variables in your secure CI/CD pipeline or shell:
```bash
export KEYSTORE_PATH="/path/to/my-upload-key.jks"
export STORE_PASSWORD="your_keystore_password"
export KEY_ALIAS="upload"
export KEY_PASSWORD="your_key_password"
```

### 3. Build Release Bundle / APK
```bash
# Build optimized release APK
gradle :app:assembleRelease

# Build Google Play App Bundle (AAB)
gradle :app:bundleRelease
```
The signed release APK will be located at:
`app/build/outputs/apk/release/app-release.apk`

---

## 16. Known Limitations

SafeGuard is committed to total honesty regarding its architectural boundaries:

1. **No Deep In-Page Decryption**: SafeGuard does not inspect encrypted HTTP/2 or HTTP/3 response bodies. It blocks entire websites by domain and host, not individual photos inside an otherwise clean general-purpose website.
2. **Third-Party Encrypted Proxies (Tor / Custom VPNs)**: If a user launches a standalone third-party VPN tunnel or Tor browser, Android switches the active VPN interface away from SafeGuard to the new app. SafeGuard warns the user immediately with an alert notification.
3. **Direct-IP Adult Sites without Domain Names**: In rare cases where an adult website is hosted on a raw IP address without any registered domain or SNI header, DNS-based filters cannot identify the domain name.
4. **Offline Database Constraints**: While the database covers tens of thousands of prominent adult sites, newly registered adult domains appear daily. Enable daily automatic updates in Settings for continuous coverage.

---

### License & Attribution
SafeGuard is distributed under the Apache 2.0 License. Built with love for family safety and digital wellbeing.
