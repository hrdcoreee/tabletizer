package com.hrdcoreee.tabletizer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.StrictMode;
import android.util.Log;

import androidx.core.app.NotificationCompat;


import com.hrdcoreee.tabletizer.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TabletServerService extends Service {
    private static final String TAG = "TabletServerService";
    public static final int PORT = 3240;
    public static final int BEACON_PORT = 3242;
    private static final String CHANNEL_ID = "TabletizerService";
    private static final int NOTIFICATION_ID = 101;

    public interface ConnectionListener {
        void onConnectionStateChanged(boolean connected, String clientInfo);
    }

    public interface StatsListener {
        void onStatsUpdated(int tps, long totalPackets);
    }

    public interface AreaSyncListener {
        void onAreaConfigReceived(float widthMm, float heightMm, float posXMm, float posYMm);
    }

    private static ConnectionListener connectionListener;
    private static StatsListener statsListener;
    private static AreaSyncListener areaSyncListener;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static final AtomicBoolean isConnected = new AtomicBoolean(false);
    public static final AtomicLong totalPacketsSent = new AtomicLong(0);
    public static volatile String connectedClientInfo = null;

    private static volatile OutputStream activeOutputStream = null;
    private static volatile Socket activeSocket = null;

    // Zero-allocation circular ring buffer for asynchronous high-speed network transmission
    private static final int RING_SIZE = 256;
    private static final byte[][] ringBuffer = new byte[RING_SIZE][12];
    private static volatile int writeIndex = 0;
    private static volatile int readIndex = 0;
    private static final Object queueLock = new Object();

    // Cached configuration sync packet (Report ID 2)
    private static final byte[] lastConfigPacket = new byte[12];
    private static volatile boolean hasConfigPacket = false;

    static {
        for (int i = 0; i < RING_SIZE; i++) {
            ringBuffer[i] = new byte[12];
        }
    }

    private ServerSocket serverSocket;
    private Thread serverThread;
    private Thread senderThread;
    private Thread beaconThread;
    private PowerManager.WakeLock cpuWakeLock;
    private WifiManager.WifiLock wifiLock;
    private WifiManager.MulticastLock multicastLock;

    private long lastPacketsCount = 0;
    private final Runnable statsTicker = new Runnable() {
        @Override
        public void run() {
            long current = totalPacketsSent.get();
            long tps = current - lastPacketsCount;
            lastPacketsCount = current;
            if (statsListener != null) {
                statsListener.onStatsUpdated((int) tps, current);
            }
            mainHandler.postDelayed(this, 1000);
        }
    };

    public static void setConnectionListener(ConnectionListener l) {
        connectionListener = l;
        if (l != null) {
            mainHandler.post(() -> l.onConnectionStateChanged(isConnected.get(), connectedClientInfo));
        }
    }

    public static void setStatsListener(StatsListener l) {
        statsListener = l;
    }

    public static void setAreaSyncListener(AreaSyncListener l) {
        areaSyncListener = l;
    }

    public static void notifyAreaConfigReceived(float widthMm, float heightMm, float posXMm, float posYMm) {
        if (areaSyncListener != null) {
            mainHandler.post(() -> {
                if (areaSyncListener != null) {
                    areaSyncListener.onAreaConfigReceived(widthMm, heightMm, posXMm, posYMm);
                }
            });
        }
    }

    public static String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (intf.isLoopback() || !intf.isUp()) continue;
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static void sendTouch(boolean inRange, int x, int y, int pressure, boolean btn1, boolean btn2) {
        if (!isConnected.get() || activeOutputStream == null) {
            return;
        }

        try {
            synchronized (queueLock) {
                int currentWrite = writeIndex;
                int nextWrite = (currentWrite + 1) % RING_SIZE;

                // If buffer is full, advance read pointer to discard oldest event
                if (nextWrite == readIndex) {
                    readIndex = (readIndex + 1) % RING_SIZE;
                }

                byte[] buffer = ringBuffer[currentWrite];
                buffer[0] = 1; // Report ID for OpenTabletDriver FmaReportParser
                buffer[1] = (byte) ((inRange ? 0x20 : 0) | (btn1 ? 0x02 : 0) | (btn2 ? 0x04 : 0));
                buffer[2] = (byte) (x & 0xFF);
                buffer[3] = (byte) ((x >> 8) & 0xFF);
                buffer[4] = (byte) (y & 0xFF);
                buffer[5] = (byte) ((y >> 8) & 0xFF);
                buffer[6] = (byte) (pressure & 0xFF);
                buffer[7] = (byte) ((pressure >> 8) & 0xFF);
                buffer[8] = 0; // tiltX
                buffer[9] = 0;
                buffer[10] = 0; // tiltY
                buffer[11] = 0;

                writeIndex = nextWrite;
                queueLock.notify();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error queuing touch packet", t);
        }
    }

    public static void sendConfig(int ratioOrdinal, int scalePercent, int positionOrdinal,
                                  float widthMm, float heightMm, float posXMm, float posYMm) {
        try {
            synchronized (queueLock) {
                lastConfigPacket[0] = 2; // Report ID 2 = Config Sync Packet
                lastConfigPacket[1] = (byte) (ratioOrdinal & 0xFF);
                lastConfigPacket[2] = (byte) (scalePercent & 0xFF);
                lastConfigPacket[3] = (byte) (positionOrdinal & 0xFF);

                short w = (short) Math.round(widthMm * 10f);
                lastConfigPacket[4] = (byte) (w & 0xFF);
                lastConfigPacket[5] = (byte) ((w >> 8) & 0xFF);

                short h = (short) Math.round(heightMm * 10f);
                lastConfigPacket[6] = (byte) (h & 0xFF);
                lastConfigPacket[7] = (byte) ((h >> 8) & 0xFF);

                short x = (short) Math.round(posXMm * 10f);
                lastConfigPacket[8] = (byte) (x & 0xFF);
                lastConfigPacket[9] = (byte) ((x >> 8) & 0xFF);

                short y = (short) Math.round(posYMm * 10f);
                lastConfigPacket[10] = (byte) (y & 0xFF);
                lastConfigPacket[11] = (byte) ((y >> 8) & 0xFF);

                hasConfigPacket = true;

                if (isConnected.get() && activeOutputStream != null) {
                    int currentWrite = writeIndex;
                    int nextWrite = (currentWrite + 1) % RING_SIZE;
                    if (nextWrite == readIndex) {
                        readIndex = (readIndex + 1) % RING_SIZE;
                    }
                    System.arraycopy(lastConfigPacket, 0, ringBuffer[currentWrite], 0, 12);
                    writeIndex = nextWrite;
                    queueLock.notify();
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error queuing config sync packet", t);
        }
    }

    private static void notifyConnectionState(boolean connected, String clientInfo) {
        if (connectionListener != null) {
            mainHandler.post(() -> connectionListener.onConnectionStateChanged(connected, clientInfo));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());
        } catch (Throwable ignored) {
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tabletizer:WakeLock");
            cpuWakeLock.acquire(12 * 60 * 60 * 1000L); // 12 hours max
        }

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "tabletizer:LowLatencyWifi");
            } else {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "tabletizer:HighPerfWifi");
            }
            wifiLock.acquire();

            try {
                multicastLock = wm.createMulticastLock("tabletizer:BeaconMulticast");
                multicastLock.setReferenceCounted(true);
                multicastLock.acquire();
            } catch (Throwable ignored) {
            }
        }

        createNotificationChannel();
        updateNotification(false, null);

        mainHandler.post(statsTicker);
        startSenderThread();
        startServerThread();
        startBeaconThread();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "tabletizer Server",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Tabletizer low-latency touch server");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void updateNotification(boolean connected, String clientInfo) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);

        String statusText;
        if (connected) {
            statusText = "\uD83D\uDFE2 Connected (" + (clientInfo != null ? clientInfo : "PC") + ")";
        } else {
            String ip = getLocalIpAddress();
            statusText = "\uD83D\uDFE1 Listening on :3240" + (ip != null ? " \u2022 Wi-Fi: " + ip : "");
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle("tabletizer Active")
                .setContentText(statusText)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        startForeground(NOTIFICATION_ID, builder.build());
    }

    private void startSenderThread() {
        senderThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                byte[] packetToSend = null;

                synchronized (queueLock) {
                    while (writeIndex == readIndex && isConnected.get()) {
                        try {
                            queueLock.wait(100);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }

                    if (writeIndex != readIndex) {
                        int currentRead = readIndex;
                        packetToSend = ringBuffer[currentRead];
                        readIndex = (currentRead + 1) % RING_SIZE;
                    }
                }

                if (packetToSend != null) {
                    OutputStream out = activeOutputStream;
                    if (out != null && isConnected.get()) {
                        try {
                            out.write(packetToSend);
                            totalPacketsSent.incrementAndGet();
                        } catch (Exception e) {
                            Log.w(TAG, "Socket write failed, client disconnected: " + e.getMessage());
                            disconnectActiveClient();
                        }
                    }
                }
            }
        }, "TabletSenderThread");
        senderThread.setPriority(Thread.MAX_PRIORITY);
        senderThread.start();
    }

    private void disconnectActiveClient() {
        synchronized (queueLock) {
            activeOutputStream = null;
            if (activeSocket != null) {
                try {
                    activeSocket.close();
                } catch (IOException ignored) {
                }
                activeSocket = null;
            }
            isConnected.set(false);
            connectedClientInfo = null;
            readIndex = writeIndex; // Clear pending packets
        }
        mainHandler.post(() -> updateNotification(false, null));
        notifyConnectionState(false, null);
    }

    private void startServerThread() {
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(PORT));

                while (!Thread.currentThread().isInterrupted()) {
                    Socket client = serverSocket.accept();
                    client.setTcpNoDelay(true);
                    client.setKeepAlive(true);
                    client.setSendBufferSize(8192);

                    SocketAddress remote = client.getRemoteSocketAddress();
                    String clientStr = remote != null ? remote.toString() : "Unknown";
                    if (clientStr.startsWith("/")) {
                        clientStr = clientStr.substring(1);
                    }
                    if (clientStr.startsWith("127.0.0.1")) {
                        connectedClientInfo = "USB (ADB)";
                    } else {
                        connectedClientInfo = clientStr;
                    }

                    synchronized (queueLock) {
                        activeSocket = client;
                        activeOutputStream = client.getOutputStream();
                        readIndex = 0;
                        writeIndex = 0;
                        isConnected.set(true);

                        // If we have a cached area configuration, send it immediately as the first packet
                        if (hasConfigPacket) {
                            System.arraycopy(lastConfigPacket, 0, ringBuffer[0], 0, 12);
                            writeIndex = 1;
                        }
                        queueLock.notify();
                    }

                    final String info = connectedClientInfo;
                    mainHandler.post(() -> updateNotification(true, info));
                    notifyConnectionState(true, info);

                    // Read incoming commands/packets from OTD (PC -> Phone)
                    InputStream in = client.getInputStream();
                    byte[] inBuf = new byte[12];
                    int offset = 0;
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            int read = in.read(inBuf, offset, inBuf.length - offset);
                            if (read == -1) break;
                            offset += read;
                            if (offset == inBuf.length) {
                                offset = 0;
                                if (inBuf[0] == 2) {
                                    // Config Sync from OTD
                                    int wVal = (inBuf[4] & 0xFF) | ((inBuf[5] & 0xFF) << 8);
                                    int hVal = (inBuf[6] & 0xFF) | ((inBuf[7] & 0xFF) << 8);
                                    int xVal = (inBuf[8] & 0xFF) | ((inBuf[9] & 0xFF) << 8);
                                    int yVal = (inBuf[10] & 0xFF) | ((inBuf[11] & 0xFF) << 8);

                                    float widthMm = wVal / 10.0f;
                                    float heightMm = hVal / 10.0f;
                                    float posXMm = xVal / 10.0f;
                                    float posYMm = yVal / 10.0f;

                                    notifyAreaConfigReceived(widthMm, heightMm, posXMm, posYMm);
                                }
                            }
                        }
                    } catch (IOException ignored) {
                    }

                    disconnectActiveClient();
                }
            } catch (IOException e) {
                // Server socket closed
            }
        }, "TabletServerThread");
        serverThread.start();
    }

    private void startBeaconThread() {
        beaconThread = new Thread(() -> {
            final byte[] beaconData = ("TABLETIZER_BEACON:port=" + PORT).getBytes(StandardCharsets.UTF_8);

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    DatagramSocket socket = new DatagramSocket();
                    socket.setBroadcast(true);

                    // 1. Broadcast to global 255.255.255.255
                    try {
                        DatagramPacket packet = new DatagramPacket(beaconData, beaconData.length,
                                InetAddress.getByName("255.255.255.255"), BEACON_PORT);
                        socket.send(packet);
                    } catch (Exception ignored) {
                    }

                    // 2. Broadcast to specific interface subnet broadcast addresses
                    try {
                        List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
                        for (NetworkInterface intf : interfaces) {
                            if (intf.isLoopback() || !intf.isUp()) continue;
                            for (InterfaceAddress ifAddr : intf.getInterfaceAddresses()) {
                                InetAddress broadcast = ifAddr.getBroadcast();
                                if (broadcast != null) {
                                    try {
                                        DatagramPacket pkt = new DatagramPacket(beaconData, beaconData.length,
                                                broadcast, BEACON_PORT);
                                        socket.send(pkt);
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }

                    socket.close();
                } catch (Exception ignored) {
                }

                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "TabletBeaconThread");
        beaconThread.setPriority(Thread.MIN_PRIORITY);
        beaconThread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(statsTicker);

        if (beaconThread != null) {
            beaconThread.interrupt();
        }
        if (senderThread != null) {
            senderThread.interrupt();
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }

        disconnectActiveClient();

        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        if (cpuWakeLock != null && cpuWakeLock.isHeld()) {
            cpuWakeLock.release();
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

