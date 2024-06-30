package com.example.tvremoteclient;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.example.tvremoteclient.databinding.ActivityMainBinding;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity{
    private static final String TAG = "Main";
    private ExecutorService executorService = Executors.newFixedThreadPool(2);
    ActivityMainBinding binding;
    private PrintWriter out;
    private DatagramSocket socket;
    private int serverPort = 12345;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        binding.send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        sendMessage();
                    }
                });
            }
        });
    }

    private void sendMessage() {
        try {
            String message = binding.etMessageBody.getText().toString();
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
            socket.send(packet);
            Log.d(TAG, "sendMessage: Message sent to the server "+message);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void startServer() {
        try {
            ServerSocket serverSocket = new ServerSocket(8000);
            Socket socket = serverSocket.accept();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void keepPingingTvServer() {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = new Socket(String.valueOf(binding.etIpAddress.getText()),8000);
                    Toast.makeText(MainActivity.this, "Socket conenctred to tv", Toast.LENGTH_SHORT).show();
                    out = new PrintWriter(socket.getOutputStream(), true);
                    while (!socket.isClosed()){
                        out.println("Kisan Madarchot !!");
                        Log.d(TAG, "run: Kisan Madarchot !!");
                    }
                    Log.d(TAG, "socket closed");
                } catch (IOException e) {
                    Log.d(TAG, "run: "+e);
                    throw new RuntimeException(e);
                }
            }
        });

    }




    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown(); // Properly shutdown the executor when the activity is destroyed
        }
    }
}
