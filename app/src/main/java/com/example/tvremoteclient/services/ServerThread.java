package com.example.tvremoteclient.services;

import android.util.Log;

import com.example.tvremoteclient.MainActivity;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerThread implements Runnable {

    private MainActivity mainActivity;

    @Override
    public void run() {
        try {
            ServerSocket serverSocket = new ServerSocket(12345);  // Choose an unused port
            while (!Thread.currentThread().isInterrupted()) {
                Socket client = serverSocket.accept();

                // Optional: Fetch client's IP address
                String clientIP = client.getInetAddress().getHostAddress();
                Log.d("Server", "Connected to client at " + clientIP);

                handleClient(client);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setActivity(MainActivity mainActivity){
        this.mainActivity = mainActivity;
    }


    private void handleClient(Socket client) {
    }
}

