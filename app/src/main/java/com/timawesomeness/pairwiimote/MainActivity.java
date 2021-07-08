package com.timawesomeness.pairwiimote;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.timawesomeness.pairwiimote.databinding.ActivityMainBinding;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    private final ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private ControllerType controllerType = ControllerType.WIIMOTE;
    private String localMAC = "";
    private ActivityMainBinding binding;
    private BluetoothDeviceAdapter deviceAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        // if we don't have bluetooth, quit
        if (adapter == null) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.no_bluetooth)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, (d, i) ->
                            finishAffinity())
                    .show();
        } else {
            tryLocationPermission();

            if (!adapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivity(enableBtIntent);
            }

            deviceAdapter = new BluetoothDeviceAdapter(devices);

            binding.list.setHasFixedSize(true);
            binding.list.setLayoutManager(new LinearLayoutManager(this));
            binding.list.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
            binding.list.setAdapter(deviceAdapter);

            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(receiver, filter);
            IntentFilter filter2 = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
            registerReceiver(receiver, filter2);
            adapter.startDiscovery();
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                devices.add(device);
                deviceAdapter.notifyNewItem();
                if (!adapter.isDiscovering()) {
                    binding.progressBar.setVisibility(View.GONE);
                }
            } else if (action.equals(BluetoothDevice.ACTION_PAIRING_REQUEST)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                try {
                    byte[] pin = new byte[6];
                    for (int i = 0; i < 6; i++) {
                        switch (controllerType) {
                            case WIIMOTE:
                                // Wiimote pins are the remote's bluetooth MAC address reversed
                                // https://wiibrew.org/wiki/Wiimote#Bluetooth_Pairing
                                pin[i] = Integer.decode("0x" + device.getAddress().split(":")[5 - i]).byteValue();
                                break;
                            case WIIUPRO:
                                // Wii U Pro Controller pins seem to be the host's MAC address
                                pin[i] = Integer.decode("0x" + localMAC.split(":")[i]).byteValue();
                        }
                    }
                    device.setPin(pin);
                    device.getClass().getMethod("setPairingConfirmation", boolean.class).invoke(device, true);
                } catch (Exception ignored) {
                }
            }
        }
    };

    /**
     * Check if we have location permissions (required for bluetooth scanning)
     */
    private void tryLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { // if we're pre-M, permissions are granted at install-time
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new RequestPermission(), isGranted -> {
                if (isGranted) {
                    refresh();
                } else {
                    tryLocationPermission();
                }
            });

    @Override
    protected void onDestroy() {
        super.onDestroy();
        adapter.cancelDiscovery();
        unregisterReceiver(receiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.app_bar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.refresh:
                refresh();
                break;
            case R.id.wiimote:
                controllerType = ControllerType.WIIMOTE;
                binding.instructions.setText(R.string.wiimote_instructions);
                break;
            case R.id.wiiupro:
                final EditText input = new EditText(this);
                input.setHint(R.string.local_mac_hint);
                input.setFilters(new InputFilter[] { (charSequence, start, end, spanned, sstart, send) -> {
                    for (int i = start; i < end; i++) {
                        if (!String.valueOf(charSequence.charAt(i)).matches("[0-9a-fA-F:]")) {
                            input.setError(getString(R.string.local_mac_error));
                            return "";
                        }
                    }
                    return null;
                } });
                input.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                new AlertDialog.Builder(this)
                        .setTitle(R.string.local_mac_title)
                        .setMessage(R.string.local_mac_instructions)
                        .setView(input)
                        .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                            if (input.getText().toString().matches("[0-9a-fA-F]:{5}[0-9a-fA-F]")) {
                                localMAC = input.getText().toString();
                                controllerType = ControllerType.WIIUPRO;
                                binding.instructions.setText(R.string.wiiupro_instructions);
                            } else {
                                input.setError(getString(R.string.local_mac_error));
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, (dialogInterface, i) ->
                                dialogInterface.cancel())
                        .show();
                break;
        }
        return true;
    }

    /**
     * Refresh bluetooth devices
     */
    public void refresh() {
        adapter.cancelDiscovery();
        devices.clear();
        deviceAdapter.notifyDataReset();
        binding.progressBar.setVisibility(View.VISIBLE);
        adapter.startDiscovery();
    }

    private enum ControllerType {
        WIIUPRO, WIIMOTE
    }
}