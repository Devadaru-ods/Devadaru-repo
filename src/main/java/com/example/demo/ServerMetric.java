package com.example.demo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "server_metrics")
@SuppressWarnings("unused")
public class ServerMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;
    private double cpuTemperature;
    private double cpuLoad;
    private double ramUsage;

    // Пустой конструктор (обязателен для JPA)
    public ServerMetric() {
    }

    // Конструктор для удобного создания объекта
    public ServerMetric(LocalDateTime timestamp, double cpuTemperature, double cpuLoad, double ramUsage) {
        this.timestamp = timestamp;
        this.cpuTemperature = cpuTemperature;
        this.cpuLoad = cpuLoad;
        this.ramUsage = ramUsage;
    }

    // Геттеры и сеттеры
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public double getCpuTemperature() {
        return cpuTemperature;
    }

    public void setCpuTemperature(double cpuTemperature) {
        this.cpuTemperature = cpuTemperature;
    }

    public double getCpuLoad() {
        return cpuLoad;
    }

    public void setCpuLoad(double cpuLoad) {
        this.cpuLoad = cpuLoad;
    }

    public double getRamUsage() {
        return ramUsage;
    }

    public void setRamUsage(double ramUsage) {
        this.ramUsage = ramUsage;
    }
}