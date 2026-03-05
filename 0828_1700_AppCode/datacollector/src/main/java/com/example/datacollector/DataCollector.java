package com.example.datacollector;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DataCollector implements SensorEventListener {

    private static DataCollector instance;
    private Context context;
    private WifiManager wifiManager;
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor magnetometer;
    private List<ScanResult> scanResults;
    private Handler handler;
    private Runnable scanRunnable;
    private File csvFile;
    private float lightIntensity;
    private float[] acceleration = new float[3];
    private float[] gyroscopeData = new float[3];
    private float[] magneticField = new float[3];
    private float[] rotationMatrix = new float[9];
    private float[] orientation = new float[3];
    private int coordinate = 1;
    private String floorId;
    private Callback callback; // 可选回调接口，用于返回结果或错误

    private DataCollector(Context context, String floorId) {
        this.context = context.getApplicationContext();
        this.floorId = floorId;

        wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        sensorManager = (SensorManager) this.context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }

        handler = new Handler(Looper.getMainLooper());

        // 初始化CSV文件
        initCsvFile();
    }

    public static DataCollector getInstance(Context context, String floorId) {
        if (instance == null) {
            instance = new DataCollector(context, floorId);
        }
        return instance;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    private void initCsvFile() {
        File appDir = context.getFilesDir();
        String fileName = "scan_results.csv";
        csvFile = new File(appDir, fileName);

        // 如果檔案已存在，清空內容
        if (csvFile.exists()) {
            try (FileWriter writer = new FileWriter(csvFile, false)) {
                writer.write(""); // 清空文件内容
            } catch (IOException e) {
                if (callback != null) callback.onError("无法清空现有的 CSV 文件");
            }
        } else {
            if (callback != null) callback.onError("无法访问下载目录");
        }
    }

    // API: 相当于 btnStartScan
    public void startScanning() {
        if (!checkPermissions()) {
            if (callback != null) callback.onError("缺少权限，请在app中请求ACCESS_FINE_LOCATION");
            return;
        }
        registerSensors();
        scanWifi();

        handler.post(scanRunnable);
        if (callback != null) callback.onSuccess("开始扫描");
    }

    // API: 相当于 btnPauseScan
    public void pauseScanning() {
        if (handler != null && scanRunnable != null) {
            handler.removeCallbacks(scanRunnable);
            if (callback != null) callback.onSuccess("扫描已暂停");
        }
    }

    // API: 相当于 btnResumeScan
    public void resumeScanning() {
        if (handler != null && scanRunnable != null) {
            handler.post(scanRunnable);
            if (callback != null) callback.onSuccess("扫描已恢复");
        }
    }

    // API: 相当于 btnPreviousCoordinate
    public void previousCoordinate() {
        if (coordinate > 1) {
            coordinate--;
            if (callback != null) callback.onSuccess("当前坐标: " + coordinate);
        } else {
            if (callback != null) callback.onError("无法再减少坐标");
        }
    }

    // API: 相当于 btnNextCoordinate
    public void nextCoordinate() {
        coordinate++;
        if (callback != null) callback.onSuccess("当前坐标: " + coordinate);
    }

    // API: 相当于 btnExportCSV
    public void exportToCSV() {
        stopScanning();
        unregisterSensors();
        if (csvFile != null && csvFile.exists()) {
            if (callback != null) callback.onSuccess("CSV 文件已导出至: " + csvFile.getAbsolutePath());
        } else {
            if (callback != null) callback.onError("CSV 文件导出失败");
        }
    }

    public String getCsvPath() {
        if (csvFile != null && csvFile.exists()) {
            return csvFile.getAbsolutePath();
        } else {
            return null; // 或者可以回傳空字串 ""
        }
    }
    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }



    private void scanWifi() {
        try {
            // 權限檢查，假設需要 ACCESS_FINE_LOCATION 權限
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (callback != null) callback.onError("缺少位置權限");
                return;
            }

            if (!wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(true);
            }

            boolean success = wifiManager.startScan();
            if (success) {
                scanResults = wifiManager.getScanResults();
                updateCsv();
            } else {
                if (callback != null) callback.onError("WiFi 扫描失败");
            }
        } catch (SecurityException e) {
            if (callback != null) callback.onError("權限被拒絕，無法掃描 WiFi");
        }
    }


    private void registerSensors() {
        if (sensorManager != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void unregisterSensors() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private void updateCsv() {
        if (scanResults == null || scanResults.isEmpty()) return;

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        StringBuilder csvData = new StringBuilder();

        // 如果檔案不存在，或是要寫入新的時間戳 → 重新初始化檔案
        boolean needReset = true;

        if (csvFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
                String firstLine = reader.readLine(); // 讀第一行標題
                String secondLine = reader.readLine(); // 讀第一筆資料
                if (secondLine != null && secondLine.startsWith(timestamp.substring(0, 19))) {
                    // 同一天、同時間批次的情況下才不重置（如果你希望更嚴格可以直接比對完整 timestamp）
                    needReset = false;
                }
            } catch (IOException e) {
                if (callback != null) callback.onError("讀取 CSV 檔案失敗");
            }
        }

        // 🔄 重新初始化檔案（清空並寫入標題）
        if (needReset) {
            try (FileWriter writer = new FileWriter(csvFile, false)) {
                writer.write("Timestamp,SSID,BSSID,RSSI,Light Intensity,Acceleration (X,Y,Z),Gyroscope (X,Y,Z),Heading,Floor ID,Coordinate\n");
            } catch (IOException e) {
                if (callback != null) callback.onError("無法初始化 CSV 檔案");
                return;
            }
        }

        // 📋 寫入新的資料
        for (ScanResult result : scanResults) {
            float heading = 0;
            if (SensorManager.getRotationMatrix(rotationMatrix, null, acceleration, magneticField)) {
                SensorManager.getOrientation(rotationMatrix, orientation);
                heading = (float) Math.toDegrees(orientation[0]);
                if (heading < 0) heading += 360;
            }

            csvData.append(timestamp).append(",")
                    .append(result.SSID).append(",")
                    .append(result.BSSID).append(",")
                    .append(result.level).append(",")
                    .append(lightIntensity).append(",")
                    .append(acceleration[0]).append(",").append(acceleration[1]).append(",").append(acceleration[2]).append(",")
                    .append(gyroscopeData[0]).append(",").append(gyroscopeData[1]).append(",").append(gyroscopeData[2]).append(",")
                    .append(heading).append(",")
                    .append(floorId).append(",")
                    .append(coordinate).append("\n");
        }

        // ✍️ 寫入資料（追加）
        try (FileWriter writer = new FileWriter(csvFile, true)) {
            writer.append(csvData);
        } catch (IOException e) {
            if (callback != null) callback.onError("無法寫入 CSV 檔案");
        }
    }


    private void stopScanning() {
        if (handler != null && scanRunnable != null) {
            handler.removeCallbacks(scanRunnable);
            if (callback != null) callback.onSuccess("扫描已停止");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            lightIntensity = event.values[0];
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, acceleration, 0, event.values.length);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            System.arraycopy(event.values, 0, gyroscopeData, 0, event.values.length);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magneticField, 0, event.values.length);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 无需实现
    }

    // 回调接口，用于返回结果（可选，如果不需要可移除）
    public interface Callback {
        void onSuccess(String message);
        void onError(String error);
    }
}