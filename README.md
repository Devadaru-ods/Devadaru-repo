# Reactive Orange Pi Hardware Monitor

A lightweight, high-performance reactive telemetry dashboard engineered for ARM-based single-board computers (Orange Pi) running under **Armbian Linux**. Built with modern Java enterprise standards to ensure minimum system resource overhead and maximum storage lifecycle protection.

## 🚀 Key Architectural Features

* **Modern Java 21 LTS Stack:** Leverages **Java Records** for lightweight immutable data representation, `Math.clamp()` for reliable data validation, and optimized JVM memory utilization.
* **Reactive Telemetry Streaming:** Implements **Server-Sent Events (SSE)** via Spring MVC `SseEmitter`, pushing live hardware metrics (CPU temp, memory state, load) directly to the client every 2 seconds without expensive HTTP polling overhead.
* **Hi-End GC-Free Parsing Pipeline:** System metrics from the Linux kernel (`/proc/stat`, `/proc/meminfo`) are processed via an ultra-fast, low-level `StringTokenizer` and lazy stream pipelines. This eliminates runtime string allocations, providing zero-allocation execution to keep the CPU footprint near zero and prevent Garbage Collection overhead.
* **Zero-Trust Network Perimeter:** The internal Java container port is strictly isolated from the host network by binding exclusively to the Docker bridge gateway (`172.18.0.1:8080:8080`). All external traffic is safely funneled through the front-line Caddy reverse proxy, eliminating direct exposure of the application tier.
* **Edge Storage Optimization (Anti-Wear Design):** Engineered strictly for single-board computers utilizing SD/eMMC storage. The application layer bypasses traditional file-logging disk operations by running entirely in memory, cutting background I/O ops to a near-zero physical footprint.
* **Fail-Safe Client Architecture:** The frontend UI is developed in pure **Vanilla JavaScript (HTML5)** with an automated connection lifecycle manager. In case of host reboots or network drops, the script cleans up dead sockets and attempts silent auto-reconnection every 3 seconds without requiring hard page reloads (F5).
* **Enterprise Infrastructure Layer:** Fully containerized with **Docker & Docker Compose**, operating seamlessly behind a **Caddy Reverse Proxy** for network isolation and automated routing.

## 🛠 Tech Stack

* **Backend:** Java 21 (Eclipse Temurin), Spring Boot 3.x, Maven, Spring Scheduling
* **Frontend:** Vanilla JS, HTML5, CSS3 (Flexbox Layout, Hardware-Accelerated Transitions)
* **Infrastructure:** Docker, Docker Compose, Caddy Reverse Proxy, Armbian Linux (Debian 13 base)
