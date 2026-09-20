package com.example.demo;

import com.sun.management.OperatingSystemMXBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

@SpringBootApplication
@EnableScheduling
@RestController
public class DemoApplication {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final String indexHtml;
    private final MetricRepository metricRepository;

    // MXBean системы для получения загрузки CPU без I/O нагрузок
    private final OperatingSystemMXBean osBean =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    // Внедряем MetricRepository через конструктор
    public DemoApplication(MetricRepository metricRepository) throws IOException {
        this.metricRepository = metricRepository;
        var resource = new ClassPathResource("templates/index.html");
        this.indexHtml = resource.getContentAsString(StandardCharsets.UTF_8);
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    // Record для хранения данных оперативной памяти
    public record MemoryMetrics(int usedMb, int totalMb, double percentUsed) {}

    // Основной Record с системными метриками
    public record ServerMetrics(
            String timestamp,
            String cpuTemp,
            double cpuUsage,
            MemoryMetrics memory
    ) {}

    // Чтение температуры процессора с локалью US (для точки в десятичных дробях)
    private String getCpuTemperature() {
        try {
            String tempRaw = Files.readString(Path.of("/sys/class/thermal/thermal_zone0/temp")).trim();
            return String.format(Locale.US, "%.1f °C", Double.parseDouble(tempRaw) / 1000.0);
        } catch (Exception e) {
            return String.format(Locale.US, "%.1f °C", ThreadLocalRandom.current().nextDouble(34.0, 36.5));
        }
    }

    // Метод расчета памяти по точной формуле htop
    private MemoryMetrics getMemoryMetrics() {
        try (var reader = Files.newBufferedReader(Path.of("/proc/meminfo"))) {
            long totalKb = 0, freeKb = 0, buffersKb = 0, cachedKb = 0, reclaimableKb = 0, shmemKb = 0;
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal:")) totalKb = parseKbValue(line);
                else if (line.startsWith("MemFree:")) freeKb = parseKbValue(line);
                else if (line.startsWith("Buffers:")) buffersKb = parseKbValue(line);
                else if (line.startsWith("Cached:")) cachedKb = parseKbValue(line);
                else if (line.startsWith("SReclaimable:")) reclaimableKb = parseKbValue(line);
                else if (line.startsWith("Shmem:")) shmemKb = parseKbValue(line);
            }

            if (totalKb == 0) return new MemoryMetrics(544, 4096, 13.2);

            // Точная формула расчета "Used" из исходного кода htop
            long usedKb = totalKb - freeKb - buffersKb - (cachedKb + reclaimableKb - shmemKb);

            int totalMb = (int) (totalKb / 1024);
            int usedMb = (int) (usedKb / 1024);
            double percentUsed = ((double) usedMb / totalMb) * 100.0;

            return new MemoryMetrics(usedMb, totalMb, percentUsed);
        } catch (Exception e) {
            return new MemoryMetrics(544, 4096, 13.2);
        }
    }

    // Быстрое извлечение числа из строки /proc/meminfo
    private long parseKbValue(String line) {
        String[] parts = line.trim().split("\\s+");
        return parts.length >= 2 ? Long.parseLong(parts[1]) : 0;
    }

    // Загрузка CPU через OperatingSystemMXBean
    private double calculateCpuUsage() {
        try {
            double systemCpuLoad = osBean.getCpuLoad();
            if (systemCpuLoad < 0) {
                return ThreadLocalRandom.current().nextDouble(0.5, 2.5);
            }
            return Math.clamp(systemCpuLoad * 100.0, 0.0, 100.0);
        } catch (Exception e) {
            return ThreadLocalRandom.current().nextDouble(0.5, 2.5);
        }
    }

    // Планировщик сборки, рассылки метрик и сохранения в SQLite (в оперативной памяти)
    @Scheduled(fixedRate = 2000)
    public void collectServerMetrics() {
        var now = LocalDateTime.now();
        var formattedTime = now.format(DATE_FORMATTER);

        String tempStr = getCpuTemperature();
        MemoryMetrics mem = getMemoryMetrics();
        double cpuUsage = Math.round(calculateCpuUsage() * 10.0) / 10.0;
        double ramUsageForDb = Math.round(mem.percentUsed() * 10.0) / 10.0;

        // --- СОХРАНЕНИЕ В SQLITE IN-MEMORY ---
        try {
            double tempVal = Double.parseDouble(tempStr.replace(" °C", "").replace(",", "."));
            metricRepository.save(new ServerMetric(now, tempVal, cpuUsage, ramUsageForDb));
        } catch (Exception ignored) {}
        // -------------------------------------

        if (emitters.isEmpty()) return;

        //var metrics = new ServerMetrics(formattedTime, tempStr, cpuUsage, mem);


        var metrics = new ServerMetrics(formattedTime, tempStr, cpuUsage, mem);

        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .data(metrics, MediaType.APPLICATION_JSON);

        emitters.removeIf(emitter -> {
            try {
                emitter.send(event);
                return false;
            } catch (IOException e) {
                return true;
            }
        });
    }

    // Новый эндпоинт для проверки накопленной истории из SQLite (RAM)
    @GetMapping("/api/metrics")
    @ResponseBody
    public List<ServerMetric> getMetricsHistory() {
        return metricRepository.findAll();
    }

    // Новый эндпоинт для проверки количества записей в SQLite (RAM)
    @GetMapping("/api/count")
    @ResponseBody
    public long getCount() {
        return metricRepository.count();
    }

    // Эндпоинт для подключения SSE-клиентов
    @GetMapping(value = "/api/sse-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMetrics() {
        var emitter = new SseEmitter(1800000L); // Таймаут 30 минут

        Runnable removeAction = () -> emitters.remove(emitter);
        emitter.onCompletion(removeAction);
        emitter.onTimeout(removeAction);
        emitter.onError(e -> removeAction.run());

        emitters.add(emitter);
        return emitter;
    }

    // Раздача скэшированного HTML
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home() {
        return indexHtml;
    }
}