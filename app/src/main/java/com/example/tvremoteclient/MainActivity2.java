package com.example.tvremoteclient;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// MainActivity.java
public class MainActivity2 extends AppCompatActivity implements SensorEventListener {

    private ServerSocket serverSocket;
    Thread serverThread = null;
    public static final int SERVERPORT = 8000;
    private Handler handler = new Handler();
    private String TAG="Main2";
    ExecutorService executorService = Executors.newFixedThreadPool(2);
    private BufferedReader input;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private long lastUpdateTime = 0;
    private boolean isCalibrated = false;
    private float baselinePitch;
    private Sensor rotationVectorSensor;
    private float baselineYaw = Float.NaN; // Initialize with NaN to check if it's set
    private boolean isBaselineSet = false;
    private String pointerLocation = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        @SuppressLint({"MissingInflatedId", "LocalSuppress"})
        TextView ipAddress = findViewById(R.id.ipAddress);
        ipAddress.setText(getIpAddress());

        this.serverThread = new Thread(new ServerThread());
        this.serverThread.start();

        // Initialize the sensor manager and sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);


    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-register the sensors when the app resumes
        //sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        //sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            // Calculate pointer position on screen
            int screenWidth = 1920; // Example screen width
            int screenHeight = 1080; // Example screen height
            float[] rotationMatrix = new float[9];
            float[] orientationAngles = new float[3];

            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);

            float currentYaw = (float) Math.toDegrees(orientationAngles[0]); // Convert radians to degrees
            float pitchDegrees = (float) Math.toDegrees(orientationAngles[1]); // Convert radians to degrees

            if (!isBaselineSet) {
                baselineYaw = currentYaw; // Set the initial baseline yaw
                isBaselineSet = true;
            }

            float adjustedYaw = currentYaw - baselineYaw;

            // Debug output

            // Normalize adjustedYaw to be within -180 to 180 for correct mapping
            adjustedYaw = (adjustedYaw + 360) % 360;
            if (adjustedYaw > 180) {
                adjustedYaw -= 360;
            }

            // Map yaw degrees to screen coordinates, adjusting for the desired screen coverage
            int x = (int) ((adjustedYaw / 60.0) * (screenWidth / 2) + (screenWidth / 2));
            int y = (int) ((pitchDegrees / 50.0) * (screenHeight / 2) + (screenHeight / 2));

            x = Math.max(0, Math.min(screenWidth, x));
            y = Math.max(0, Math.min(screenHeight, y));

            // Debug output
            pointerLocation = x+":"+y;
        }
    }

    private void sendPointerPosition(int x, int y) {
        Log.d(TAG, "sendPointerPosition: ("+x+":"+y+")");
    }

    private float integrateGyroData(float rateOfRotation) {
        if (lastUpdateTime == 0) {
            lastUpdateTime = System.nanoTime();
            return 0;
        }

        long currentTime = System.nanoTime();
        float deltaTime = (currentTime - lastUpdateTime) / 1_000_000_000.0f; // Convert nanoseconds to seconds
        lastUpdateTime = currentTime;

        // Angular change in degrees
        return rateOfRotation * deltaTime * (float) (180.0 / Math.PI); // Convert radians to degrees
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private class ServerThread implements Runnable {
        @Override
        public void run() {
            Socket socket = null;
            try {
                serverSocket = new ServerSocket(SERVERPORT);
                Log.d(TAG, "run: server started");
                socket = serverSocket.accept();
                Socket finalSocket = socket;
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                Log.d(TAG, "run: "+input.readLine());
                while (!Thread.currentThread().isInterrupted()) {
                    executorService.execute(new Runnable() {
                        @Override
                        public void run() {
                            communicate(finalSocket);
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private void communicate(Socket clientSocket){
        while (!Thread.currentThread().isInterrupted()) {
            handler.post(() -> {
                PrintWriter out;
                try {
                    out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream())), true);
                    executorService.execute(new Runnable() {
                        @Override
                        public void run() {
                            out.println(pointerLocation+"\n");
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

    }

    private String getIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("ServerActivity", ex.toString());
        }
        return null;
    }
}
