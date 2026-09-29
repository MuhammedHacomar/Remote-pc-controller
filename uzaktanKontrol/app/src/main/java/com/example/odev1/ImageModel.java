package com.example.odev1;

public class ImageModel {
    private String url;
    private String filename;
    private long timestamp;
    private String key;
    private String type; // إضافة field جديد

    public ImageModel() {}

    // تعديل constructor لإضافة type
    public ImageModel(String url, String filename, long timestamp, String key, String type) {
        this.url = url;
        this.filename = filename;
        this.timestamp = timestamp;
        this.key = key;
        this.type = type;
    }

    public String getUrl() { return url; }
    public String getFilename() { return filename; }
    public long getTimestamp() { return timestamp; }
    public String getKey() { return key; }
    public String getType() { return type; } // getter جديد

    // setter جديد
    public void setType(String type) { this.type = type; }
}