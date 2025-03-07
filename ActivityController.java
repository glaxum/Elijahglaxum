package com.example.smartcurtaincontrollerapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;

import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import android.os.Bundle;
import android.os.Handler;

import android.util.Log;

import android.view.MotionEvent;
import android.view.View;
import android.view.MenuItem;

import android.widget.Button;
import android.widget.TextView;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AlertDialog;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONException;
import org.json.JSONObject;
import okhttp3.Call;
import okhttp3.Callback;

public class ControllerActivity extends AppCompatActivity {

    // Drawer
    private ActionBarDrawerToggle toggle;
    private LinearLayout connectSubMenu;
    private boolean isSubMenuVisible = false;
    private DrawerLayout drawerLayout;
    // UI Components
    private Button btnOpen, btnClose, btnSwitchMode;
    private TextView txtTemperature, txtMode, txtConnection, txtStatus;

    // Humidity
    private TextView txtHumidity;

    // Wi-Fi and Bluetooth Variables
    private WifiManager wifiManager;
    private BroadcastReceiver wifiReceiver;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice bluetoothDevice;
    private InputStream bluetoothInputStream;
    private OutputStream bluetoothOutputStream;
    private boolean isBluetoothConnected = false;
    private Handler handler = new Handler();
    private boolean isRepeating = false;
    private OkHttpClient client = new OkHttpClient();

    // Current Mode
    private Mode currentMode = Mode.AUTOMATIC;

    private enum Mode {
        MANUAL,
        AUTOMATIC
    }

    /**
     * Converts an integer IP address to a string representation.
     *
     * @param ipAddress Integer representation of the IP address.
     * @return String representation of the IP address.
     */
    private String intToIp(int ipAddress) {
        return String.format(
                "%d.%d.%d.%d",
                (ipAddress & 0xFF),
                (ipAddress >> 8 & 0xFF),
                (ipAddress >> 16 & 0xFF),
                (ipAddress >> 24 & 0xFF)
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);

        // Set up Toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        } else {
            Log.e("ControllerActivity", "ActionBar is null!");
        }

        // Initialize UI Components
        btnOpen = findViewById(R.id.btnOpen);
        btnClose = findViewById(R.id.btnClose);
        btnSwitchMode = findViewById(R.id.btnSwitchMode);
        txtTemperature = findViewById(R.id.txtTemperature);
        txtMode = findViewById(R.id.txtMode);
        txtConnection = findViewById(R.id.txtConnection);
        txtStatus = findViewById(R.id.txtStatus);

        // Initialize DrawerLayout and NavigationView
        drawerLayout = findViewById(R.id.drawerLayout);
        NavigationView navigationView = findViewById(R.id.navigationView);

        txtHumidity = findViewById(R.id.txtHumidity);

        // Set up ActionBarDrawerToggle
        toggle = new ActionBarDrawerToggle(
                this, drawerLayout, R.string.navigation_open, R.string.navigation_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // Set up NavigationView item click listener
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_connect) {
                // Handle "Connect" action
            } else if (id == R.id.nav_settings) {
                // Handle settings action
            } else if (id == R.id.nav_about) {
                Intent intent = new Intent(ControllerActivity.this, AboutActivity.class);
                startActivity(intent);
                // Handle about action
                // Apply animation
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            } else if (id == R.id.nav_exit) {
                // Handle exit action
                showExitConfirmationDialog();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Initialize Wi-Fi Manager and Bluetooth Adapter
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Register Wi-Fi Receiver
        registerWifiReceiver();

        // Setup button listeners
        setupButtonListeners();

        // Start periodic data fetch
        startPeriodicDataFetch();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void fetchSensorData() {
        String url = "http://192.168.4.1/";  // Adjust based on ESP route

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> txtConnection.setText("Error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonResponse = response.body().string();

                    try {
                        JSONObject jsonObject = new JSONObject(jsonResponse);
                        final String temperature = jsonObject.getString("temperature");
                        final String humidity = jsonObject.getString("humidity");
                        final String lightValue = jsonObject.getString("lightValue");

                        runOnUiThread(() -> {
                            txtTemperature.setText(temperature + "°C");
                            txtHumidity.setText(humidity + "%");
                            // Update additional sensor values if needed
                        });

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void registerWifiReceiver() {
        wifiReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                    NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

                    if (networkInfo != null && networkInfo.isConnected()) {
                        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                        String ssid = wifiInfo.getSSID();
                        String ipAddress = intToIp(wifiInfo.getIpAddress());

                        // Ensure UI update runs on the main thread
                        runOnUiThread(() -> {
                            if (ssid != null && ssid.equals("\"SmartCurtain\"") || ipAddress.startsWith("192.168.4.")) {
                                txtConnection.setText("Connected to: Smart Curtain");
                            } else {
                                txtConnection.setText("Connected to: " + ipAddress);
                            }
                        });
                    } else {
                        runOnUiThread(() -> txtConnection.setText("Not Connected, Trying Bluetooth..."));
                    }
                }
            }
        };

        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
    }

    private void setupButtonListeners() {
        btnOpen.setOnTouchListener((v, event) -> {
            handleButtonPress(event, "open");
            return true;
        });
        btnClose.setOnTouchListener((v, event) -> {
            handleButtonPress(event, "close");
            return true;
        });
        btnSwitchMode.setOnClickListener(view -> switchMode());
    }

    private void handleButtonPress(MotionEvent event, String action) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isRepeating = true;
                txtStatus.setText(action.equals("open") ? "Opening Curtain" : "Closing Curtain");
                repeatAction(action);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isRepeating = false;
                resetStatusAfterDelay();
                break;
        }
    }

    private void repeatAction(final String action) {
        if (isRepeating) {
            sendCommand(action);
            handler.postDelayed(() -> repeatAction(action), 100);
        }
    }

    private void switchMode() {
        if (currentMode == Mode.AUTOMATIC) {
            currentMode = Mode.MANUAL;
            txtMode.setText("Manual");
            txtStatus.setText("Switched to Manual");
        } else {
            currentMode = Mode.AUTOMATIC;
            txtMode.setText("Automatic");
            txtStatus.setText("Switched to Automatic");
        }
        sendCommand("switch");
    }

    public void sendCommand(final String cmd) {
        new Thread(() -> {
            String url = "http://192.168.4.1/" + cmd;
            try {
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();

                if (response.body() != null) { // Check if response body is not null
                    String responseBody = response.body().string().trim();
                    runOnUiThread(() -> {
                        // Only update the status if the command is not "switch"
                        if (!cmd.equals("switch")) {
                            // Check if the response is JSON (sensor data)
                            if (responseBody.startsWith("{") && responseBody.endsWith("}")) {
                                // Do not update txtStatus with sensor data
                                Log.i("ControllerActivity", "Received sensor data: " + responseBody);
                            } else {
                                // Update status with the command response
                                txtStatus.setText(responseBody);
                            }
                        }
                    });
                } else {
                    Log.e("ControllerActivity", "Response body is null");
                    runOnUiThread(() -> txtMode.setText("No response from server"));
                }
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> txtMode.setText("Error: " + e.getMessage()));
            }
        }).start();
    }

    private void resetStatusAfterDelay() {
        handler.postDelayed(() -> txtStatus.setText("status"), 3000);
    }

    private void startPeriodicDataFetch() {
        final Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                fetchSensorData();
                handler.postDelayed(this, 5000); // Fetch every 5 seconds
            }
        };
        handler.post(runnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(wifiReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(wifiReceiver);
    }

    private void showExitConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Exit");
        builder.setMessage("Are you sure you want to exit?");
        builder.setPositiveButton("Yes", (dialog, which) -> {
            finish();
        });
        builder.setNegativeButton("No", (dialog, which) -> {
            dialog.dismiss();
        });
        builder.show();
    }
}