package com.example.tvremoteclient;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import com.example.tvremoteclient.base.BaseActivity;
import com.example.tvremoteclient.databinding.ActivityMainBinding;
import com.example.tvremoteclient.services.AndroidWebServer;
import com.example.tvremoteclient.viewmodel.MainActivityViewModel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// MainActivity.java
public class MainActivity2 extends BaseActivity implements SensorEventListener {

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
    ActivityMainBinding binding;
    MainActivityViewModel model;
    private boolean isCalibrated = false;
    private float baselinePitch;
    private Sensor rotationVectorSensor;
    private float baselineYaw = Float.NaN; // Initialize with NaN to check if it's set
    private boolean isBaselineSet = false;
    private String pointerLocation = "";
    private DatagramSocket socket;
    private int serverPort = 12345;
    private boolean isTransfer = false;
    private AndroidWebServer webServer;
    private String tvIP;
    private ExecutorService service = Executors.newFixedThreadPool(2);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        model = new ViewModelProvider(this).get(MainActivityViewModel.class);
        binding.setModel(model);
        binding.setLifecycleOwner(this);

        binding.ipAddress.setText(getIpAddress());
        tvIP = getIntent().getStringExtra("IP");
        checkStoragePermission();


    }

    @Override
    protected void onStoragePermission(boolean isPermissionGranted) {
        if (isPermissionGranted){
            startServer();
        }
    }

    private void startServer() {
        int port = 3000; // Choose an appropriate port
        webServer = new AndroidWebServer(port, getApplicationContext());
        try {
            webServer.start();
            Toast.makeText(this, "Server started on port: " + port, Toast.LENGTH_LONG).show();
            exchangeIpAddressWithTV();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to start server: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void exchangeIpAddressWithTV() {

        service.execute(new Runnable() {
            @Override
            public void run() {
                Socket socket1 = null;
                OutputStream outputStream = null;
                BufferedReader inputStream = null;
                try {
                    socket1 = new Socket();
                    socket1.connect(new InetSocketAddress(tvIP, 3005), 5000); // 5 seconds timeout
                    outputStream = socket1.getOutputStream();
                    inputStream = new BufferedReader(new InputStreamReader(socket1.getInputStream()));


                    String ip = getIpAddress();
                    outputStream.write(ip.getBytes());
                    outputStream.flush();

                    // Listen for response
                    String response;
                    while ((response = inputStream.readLine()) != null) {
                        model.isConencted.setValue(true);
                        if (response.equals("OK")){
                            initializeSensors();
                        }
                        else{
                            //Toast.makeText(MainActivity2.this, "Response from the TV is wrong : "+response, Toast.LENGTH_SHORT).show();
                        }
                    }
                }catch (Exception e){
                    e.printStackTrace();
                    //Toast.makeText(MainActivity2.this, "Could not exchange the ip address with the tv"+e, Toast.LENGTH_SHORT).show();
                }
                finally {
                    try {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                        if (outputStream != null) {
                            outputStream.close();
                        }
                        if (socket1 != null) {
                            socket1.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

    }

    private void initializeSensors() {
        // Initialize the sensor manager and sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        binding.send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isTransfer = true;
            }
        });


    }

    private void sendMessage() {
        try {
            String message = pointerLocation;
            String serverIP = binding.etIpAddress.getText().toString();
            // Convert the message into bytes
            byte[] buffer = message.getBytes();

            // Get the internet address of the server
            InetAddress address = InetAddress.getByName(serverIP);

            // Create a packet to send the message to the server
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, serverPort);

            // Create a socket
            socket = new DatagramSocket();

            // Send the packet
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        socket.send(packet);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            Log.d(TAG, "sendMessage: Message sent to the server "+message);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager!=null)
            sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-register the sensors when the app resumes
        //sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        //sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        if (sensorManager!=null)
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onDestroy() {
        isTransfer = false;
        try {
            serverSocket.close();
            if (webServer != null) {
                webServer.stop();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    private float[] lastYawPitch = new float[2]; // lastYawPitch[0] for yaw, lastYawPitch[1] for pitch
    private static final float ALPHA = 0.1f; // Smoothing constant
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
            float currentPitch = (float) Math.toDegrees(orientationAngles[1]); // Convert radians to degrees

            if (!isBaselineSet) {
                baselineYaw = currentYaw;
                baselinePitch = currentPitch;
                lastYawPitch[0] = currentYaw;
                lastYawPitch[1] = currentPitch;
                isBaselineSet = true;
            }

            // Apply low-pass filter
            currentYaw = lastYawPitch[0] += ALPHA * (currentYaw - lastYawPitch[0]);
            currentPitch = lastYawPitch[1] += ALPHA * (currentPitch - lastYawPitch[1]);

            // Adjust based on baseline
            float adjustedYaw = currentYaw - baselineYaw;
            float adjustedPitch = currentPitch - baselinePitch;

            // Normalize the yaw to be within -180 to 180
            adjustedYaw = (adjustedYaw + 360) % 360;
            if (adjustedYaw > 180) {
                adjustedYaw -= 360;
            }

            // Map yaw and pitch to screen coordinates
            int x = (int) ((adjustedYaw / 30.0) * (screenWidth / 2) + (screenWidth / 2));
            int y = (int) ((adjustedPitch / 25.0) * (screenHeight / 2) + (screenHeight / 2));

            x = Math.max(0, Math.min(screenWidth, x));
            y = Math.max(0, Math.min(screenHeight, y));

            // Update pointer location in a thread-safe manner
            pointerLocation = x + ":" + y;
            if (isTransfer){
                sendMessage();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

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
