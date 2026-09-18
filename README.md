# Reactive Orange Pi Hardware Monitor

A lightweight, high-performance reactive telemetry dashboard engineered for ARM-based single-board computers (Orange Pi) running under **Armbian Linux**. Built with modern Java enterprise standards to ensure minimum system resource overhead and maximum storage lifecycle protection.

## 🚀 Key Architectural Features

* **Modern Java 21 LTS Stack:** Leverages **Java Records** for immutable data transfer objects, `Math.clamp()` for numerical safety, and explicit `Locale.US` formatting to guarantee cross-platform string consistency.
* **Single-Pass Reactive Telemetry Streaming:** Implements **Server-Sent Events (SSE)** via Spring MVC `SseEmitter`. The backend performs a single JSON serialization pass (`SseEventBuilder`) for all active clients before broadcasting, keeping CPU serialization overhead strictly $O(1)$ relative to payload generation.
* **Low-Allocation Metrics Engine:**
    * **CPU Load:** Powered by JNI native OS calls via `OperatingSystemMXBean`, completely eliminating file I/O operations and string parsing overhead for `/proc/stat`.
    * **RAM Usage (`htop`-aligned):** Precise Linux memory calculation parsing `/proc/meminfo` via a lightweight `BufferedReader` (`Total - Free - Buffers - Cached - SReclaimable + Shmem`). Correctly isolates application memory consumption from kernel page cache and I/O buffers.
    * **Temperature Monitoring:** Direct reading of raw SoC thermal zones (`/sys/class/thermal/thermal_zone0/temp`).
* **In-Memory Template Caching:** Pre-loads and caches `index.html` in JVM heap memory during application startup, preventing disk/SD-card read operations on root HTTP GET requests.
* **Zero-Trust Network Perimeter:** The internal Java container port is strictly isolated from the host network by binding exclusively to the Docker bridge gateway (`172.18.0.1:8080:8080`). All external traffic is safely funneled through the front-line Caddy reverse proxy, eliminating direct exposure of the application tier.
* **Edge Storage Optimization (Anti-Wear Design):** Engineered strictly for single-board computers utilizing SD/eMMC storage. The application layer bypasses traditional file-logging disk operations by running entirely in memory, cutting background I/O ops to a near-zero physical footprint.
* **Fail-Safe & DOM-Optimized Frontend:** Developed in pure **Vanilla JavaScript (HTML5)** with pre-cached DOM element references and safe JSON parsing. Includes an automated connection lifecycle supervisor that handles host reboots or network drops with silent auto-reconnection every 3 seconds.
* **Enterprise Infrastructure Layer:** Fully containerized with **Docker & Docker Compose**, operating seamlessly behind a **Caddy Reverse Proxy** for network isolation and automated routing.

## 🛠 Tech Stack

* **Backend:** Java 21 (Eclipse Temurin), Spring Boot 3.x, Maven, Spring Scheduling
* **Frontend:** Vanilla JS (DOM-cached), HTML5, CSS3 (Flexbox Layout, Hardware-Accelerated Transitions)
* **Infrastructure:** Docker, Docker Compose, Caddy Reverse Proxy, Armbian Linux (Debian 13 base)