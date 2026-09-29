package com.example.odev1;

public class AppModel {
    private String name;
    private String title;
    private int pid;
    private String icon;
    private String memory; // إضافة
    private String cpu;    // إضافة

    public AppModel() {}

    // Constructor معدل
    public AppModel(String name, String title, int pid, String icon, String memory, String cpu) {
        this.name = name;
        this.title = title;
        this.pid = pid;
        this.icon = icon;
        this.memory = memory;
        this.cpu = cpu;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getPid() { return pid; }
    public void setPid(int pid) { this.pid = pid; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getMemory() { return memory; }
    public void setMemory(String memory) { this.memory = memory; }

    public String getCpu() { return cpu; }
    public void setCpu(String cpu) { this.cpu = cpu; }
}