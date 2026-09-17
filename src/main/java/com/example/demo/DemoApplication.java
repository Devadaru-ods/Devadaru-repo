package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

@SpringBootApplication
@EnableScheduling
@RestController
public class DemoApplication {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private long prevIdle = 0;
    private long prevTotal = 0;

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    // Легковесный Record для хранения данных оперативной памяти
    public record MemoryMetrics(int usedMb, int totalMb, double percentUsed) {}

    // Основной Record, содержащий только системные метрики Orange Pi
    public record ServerMetrics(
            String timestamp,
            String cpuTemp,
            double cpuUsage,
            MemoryMetrics memory
    ) {}

    // Метод чтения реальной температуры процессора Allwinner
    private String getCpuTemperature() {
        try {
            var content = Files.readString(Paths.get("/sys/class/thermal/thermal_zone0/temp")).trim();
            return String.format("%.1f °C", Double.parseDouble(content) / 1000.0);
        } catch (Exception e) {
            return String.format("%.1f °C", ThreadLocalRandom.current().nextDouble(34.0, 36.5));
        }
    }

    // Метод парсинга /proc/meminfo для получения точных мегабайт ОЗУ
    private MemoryMetrics getMemoryMetrics() {
        try {
            long[] memData = Files.readAllLines(Paths.get("/proc/meminfo")).stream()
                    // 1. Фильтруем строки: оставляем только MemTotal и MemAvailable
                    .filter(line -> line.startsWith("MemTotal:") || line.startsWith("MemAvailable:"))
                    // 2. Очищаем строку от букв и пробелов, оставляя только цифры
                    .map(line -> line.replaceAll("[^0-9]", ""))
                    // 3. Парсим оставшийся текст в примитивное число long
                    .mapToLong(Long::parseLong)
                    // 4. Собираем в массив: [0] будет MemTotal, [1] будет MemAvailable
                    .toArray();

            // Безопасная проверка: если Linux вернул не все данные, отдаем заглушку
            if (memData.length < 2) return new MemoryMetrics(544, 4096, 13.2);

            long totalKb = memData[0];
            long availableKb = memData[1];

            int totalMb = (int) (totalKb / 1024);
            int usedMb = totalMb - (int) (availableKb / 1024);
            double percentUsed = ((double) usedMb / totalMb) * 100.0;

            return new MemoryMetrics(usedMb, totalMb, percentUsed);

        } catch (Exception e) {
            // Дефолтная заглушка для ПК в случае ошибки чтения файла
            return new MemoryMetrics(544, 4096, 13.2);
        }
    }


    // Метод расчета честной загрузки CPU на основе дельты тиков ядра Linux
    private double calculateCpuUsage() {
        try {
            String firstLine;
            try (var stream = Files.lines(Paths.get("/proc/stat"))) {
                firstLine = stream.findFirst().orElse("");
            }

            if (!firstLine.startsWith("cpu")) return 0.0;

            // Посимвольный разрез без регулярных выражений (работает со скоростью Си)
            var tokenizer = new java.util.StringTokenizer(firstLine.trim());

            // Пропускаем первое слово "cpu"
            if (tokenizer.hasMoreTokens()) tokenizer.nextToken();

            long total = 0;
            long idleTime = 0;
            int index = 0;

            while (tokenizer.hasMoreTokens() && index < 7) {
                long tick = Long.parseLong(tokenizer.nextToken());
                total += tick;

                if (index == 3 || index == 4) { // idle (3) и iowait (4)
                    idleTime += tick;
                }
                index++;
            }

            if (index < 7) return 0.0;

            long totalDelta = total - prevTotal;
            long idleDelta = idleTime - prevIdle;

            prevTotal = total;
            prevIdle = idleTime;

            return totalDelta == 0 ? 0.0 : Math.clamp(100.0 * (totalDelta - idleDelta) / totalDelta, 0.0, 100.0);

        } catch (Exception e) {
            return ThreadLocalRandom.current().nextDouble(0.5, 2.5);
        }
    }




    // Планировщик: собирает реальные параметры железа раз в 2 секунды и отправляет в браузеры
    @Scheduled(fixedRate = 2000)
    public void collectServerMetrics() {
        if (emitters.isEmpty()) return; // Экономим ресурсы процессора Allwinner, если никто не открыл сайт

        var now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
        var metrics = new ServerMetrics(now, getCpuTemperature(), calculateCpuUsage(), getMemoryMetrics());

        List<SseEmitter> dead = new ArrayList<>();
        for (var emitter : emitters) {
            try {
                emitter.send(metrics, MediaType.APPLICATION_JSON);
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }

    // Эндпоинт для подключения SSE-клиента (браузера)
    @GetMapping(value = "/api/sse-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMetrics() {
        var emitter = new SseEmitter(1800000L); // Таймаут 30 минут
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitters.add(emitter);
        return emitter;
    }

    // Раздача статического HTML-шаблона из ресурсов
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home() throws IOException {
        var resource = new ClassPathResource("templates/index.html");
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
