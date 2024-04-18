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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity{
    private static final String TAG = "Main";
    private ExecutorService executorService = Executors.newFixedThreadPool(2);
    ActivityMainBinding binding;
    private PrintWriter out;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

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
