# SafeGuard - Android Network Protection & Content Filtering Engine

SafeGuard is an offline-first, privacy-preserving Android network filtering application built using Jetpack Compose, Kotlin Coroutines, and the Android `VpnService` framework.

---

## Part 4: Modern HTTPS & Encrypted DNS Protection Architecture

SafeGuard Part 4 strengthens protection for modern Android web browsers (Google Chrome, Mozilla Firefox, Microsoft Edge, Brave, Opera, Samsung Internet, and Android System WebViews) without violating user privacy or decrypting network traffic.

### 1. The HTTPS Privacy Rule (Strictly Enforced)

SafeGuard is architected to remain **100% privacy-preserving**:
- **NO TLS Decryption / MITM**: SafeGuard does not terminate, decrypt, or intercept TLS sessions.
- **NO Root Certificates**: No custom certificate authorities (CAs) are generated or installed on the device.
- **NO Sensitive Data Inspection**: Passwords, session cookies, payment cards, personal messages, and webpage body contents are never inspected, captured, or stored.
- **100% Local On-Device Operation**: Filtering rules are evaluated locally without telemetry or logging to remote servers.

---

### 2. Dual-Layer Protection for HTTPS Browsers

When a modern browser requests `https://known-adult-domain.example`:

1. **Layer 1 - DNS Sinkhole (IPv4 & IPv6)**:
   - SafeGuard intercepts standard UDP port 53 DNS queries on both IPv4 and IPv6.
   - For blocked adult domains, Type A queries return sinkhole `0.0.0.0`, and Type AAAA queries return IPv6 sinkhole `::`.
   - The browser never resolves the adult server's IP address.

2. **Layer 2 - Passive TLS ClientHello SNI Extraction (TCP Port 443)**:
   - If a browser connects directly via a cached socket or IP address, SafeGuard parses the unencrypted **Server Name Indication (SNI)** extension from the TLS ClientHello packet (RFC 6066).
   - If the SNI hostname matches an adult domain rule, the initial handshake packet is dropped.
   - The connection is cleanly terminated before any encrypted TLS tunnel is established.

---

### 3. Encrypted DNS (DoT & DoH) Mitigation

Modern browsers and Android Private DNS can attempt encrypted DNS resolution to bypass local network filters. SafeGuard deploys mitigation mechanisms that prompt browsers to fall back to the protected VPN DNS:

- **DNS-over-TLS (DoT / Port 853)**: SafeGuard drops traffic destined for TCP/UDP port 853. When port 853 fails, Android and browser networking stacks automatically fall back to the system DNS configured on the VPN.
- **Firefox Canary Domain (`use-application-dns.net`)**: Mozilla RFC standard. SafeGuard returns `NXDOMAIN` for this canary domain, signaling that network-level content filtering is active and prompting Firefox to disable opportunistic DoH.
- **DoH Bootstrap Domain Sinkholing**: SafeGuard sinks DNS lookups for known public DoH bootstrap resolvers (`cloudflare-dns.com`, `dns.google`, `dns.quad9.net`, `doh.opendns.com`, `dns.adguard.com`), preventing browsers from bootstrapping DoH tunnels.

---

### 4. QUIC / HTTP/3 Bypass Prevention (RFC 9000 Fallback)

HTTP/3 runs over QUIC on UDP port 443:
- SafeGuard identifies UDP port 443 traffic and inspects initial QUIC packets for SNI hostnames.
- For adult domains, QUIC flows are dropped.
- In accordance with RFC 9000, when QUIC packets are dropped or unanswered, standard web browsers immediately and seamlessly fall back to HTTP/2 or HTTP/1.1 over TCP port 443, where SafeGuard's DNS and SNI filtering operates reliably.
- Legitimate, allowed traffic continues normally.

---

### 5. SafeSearch DNS Alias Architecture

SafeGuard forces strict SafeSearch mode across major search engines without capturing user search keywords:
- **Google**: Maps `google.com` and international domains (`google.co.uk`, `google.de`, etc.) to `forcesafesearch.google.com` (`216.239.38.120` IPv4, `2001:4860:4806::78` IPv6).
- **Bing**: Maps `bing.com` to `strict.bing.com` (`204.79.197.220` IPv4, `2620:1ec:c11::200` IPv6).
- **DuckDuckGo**: Maps `duckduckgo.com` to `safe.duckduckgo.com` (`52.142.124.215`).
- **Yahoo**: Maps `search.yahoo.com` to `safesearch.yahoo.com`.

---

### 6. Technical Limitations & Boundary Disclosures

SafeGuard is transparent regarding technical boundaries:

1. **Unclassified Domains with User-Generated Content**:
   - If an otherwise unclassified, generic HTTPS domain (e.g. cloud storage or social platform) hosts adult content under a specific sub-path (`https://example.com/adult-path`), SafeGuard **cannot** inspect the encrypted URL path or page body without HTTPS MITM decryption.
   - SafeGuard deliberately avoids HTTPS decryption to preserve cryptographic security and user privacy.

2. **Hardcoded IP Encrypted DNS / Custom Proxies**:
   - If a browser profile has manually configured hardcoded third-party DoH IP addresses or a separate encrypted proxy tunnel, on-device DNS interception cannot modify those encrypted streams without full TLS interception.

---

### 7. Browser Compatibility Matrix

SafeGuard operates entirely at the Android OS network layer (`VpnService`), ensuring universal compatibility without requiring browser-specific extensions or custom flags:
- Google Chrome
- Mozilla Firefox
- Microsoft Edge
- Brave Browser
- Opera Browser
- Samsung Internet
- DuckDuckGo Privacy Browser
- Android System WebView (all embedded browser views)
