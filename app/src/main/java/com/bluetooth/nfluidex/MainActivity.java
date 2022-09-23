package com.bluetooth.nfluidex;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements ScanResultsConsumer {

    private boolean ble_scanning = false;
    private Handler handler = new Handler();
    private ListAdapter ble_device_list_adapter;
    private Scanner ble_scanner;
    private static final long SCAN_TIMEOUT = 5000;
    private static final int REQUEST_LOCATION = 0;
    private static String[] PERMISSIONS_LOCATION = {Manifest.permission.ACCESS_FINE_LOCATION};
    private boolean permissions_granted = false;
    private int device_count = 0;
    private Toast toast;
    private final String TAG = "MainActivity";

    static class ViewHolder {
        public TextView text;
        public TextView bdaddr;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setButtonText();
        ble_device_list_adapter = new ListAdapter();
        ListView listView = this.findViewById(R.id.deviceList);
        listView.setAdapter(ble_device_list_adapter);
        ble_scanner = new Scanner(this.getApplicationContext());

        //if you click on an item in the list, stop scanning and let device be the one you clicked
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (ble_scanning) {
                setScanState(false);
                ble_scanner.stopScanning();
            }
            BluetoothDevice device = ble_device_list_adapter.getDevice(position);
            if (toast != null) {
                toast.cancel();
            }
            Intent intent = new Intent(MainActivity.this, Real_Time_Activity.class);
            //changed from ABEStatActivity to Real_Time_Activity
            intent.putExtra(Real_Time_Activity.EXTRA_NAME, device.getName());
            intent.putExtra(Real_Time_Activity.EXTRA_ID, device.getAddress());
            startActivity(intent);
        });
    }

    //set or reset button text
    private void setButtonText() {
        String text="";
        text = "Find devices";
        final String button_text = text;
        runOnUiThread(() -> ((TextView)  MainActivity.this.findViewById(R.id.scanButton)).setText(button_text));
    }
    //change text on button if scanning is currently being performed
    private void setScanState(boolean value) {
        ble_scanning = value;
        ((Button) this.findViewById(R.id.scanButton)).setText(value ? "Scanning":"Find devices");
    }

    @Override
    public void candidateDevice(final BluetoothDevice device, byte[] scan_record, int rssi) {
        runOnUiThread(() -> {
            ble_device_list_adapter.addDevice(device);
            ble_device_list_adapter.notifyDataSetChanged();
            device_count++;
        });
    }

    @Override
    public void scanningStarted() {
        setScanState(true);
    }

    @Override
    public void scanningStopped() {
        if (toast != null){
            toast.cancel();
            setScanState(false);
        }
    }
    public void onScan(View view) {
        if(!ble_scanner.isScanning()) {
            device_count = 0;

            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions_granted = false;
                requestLocationPermission();
            }
            else{
                Log.i(TAG, "Location permission has already been granted.Starting scanning");
                permissions_granted = true;
            }
            startScanning();
        }
        else {
            ble_scanner.stopScanning();
        }
    }
    private void requestLocationPermission() {
        Log.i(TAG,"Location permission has not yet been granted. Requesting permission");
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            Log.i(TAG, "Displaying location permission rationale");
            final AlertDialog.Builder builder  = new AlertDialog.Builder(this);
            builder.setTitle("Permission required");
            builder.setMessage("Please grant location access");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(dialog -> {
                Log.d(TAG, "Requesting permissions after explanation");
                ActivityCompat.requestPermissions(MainActivity.this, new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            });
            builder.show();
        }
        else {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION){
            Log.i(TAG,"Received response");
            //check if only required permission has been granted
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                //location permission has been granted
                Log.i(TAG,"Location permission has now been granted. Scanning...");
                permissions_granted = true;
                if (ble_scanner.isScanning()) {
                    startScanning();
                }
            }
            else {
                Log.i(TAG, "Location permission was NOT granted");
            }
        }
        else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void simpleToast(String message, int duration){
        toast = Toast.makeText(this, message, duration);
        toast.setGravity(Gravity.CENTER,0,0);
        toast.show();
    }
    //checks that permissions have been granted, clears UI device list, tells Scanner to start scanning
    public void startScanning(){
        if (permissions_granted){
            runOnUiThread(() -> {
                ble_device_list_adapter.clear();
                ble_device_list_adapter.notifyDataSetChanged();
            });
            simpleToast("Scanning",2000);
            ble_scanner.startScanning(this,SCAN_TIMEOUT);
        }
        else {
            Log.i(TAG,"Permission to perform BT scanning was not yet granted");
        }
    }


    private class ListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> ble_devices;
        public ListAdapter() {
            super();
            ble_devices = new ArrayList<>();
        }
        public void addDevice(BluetoothDevice device) {
            if (!ble_devices.contains(device)) {
                ble_devices.add(device);
            }
        }
        public boolean contains(BluetoothDevice device) {
            return ble_devices.contains(device);
        }
        public BluetoothDevice getDevice(int position) {
            return ble_devices.get(position);
        }
        public void clear() {
            ble_devices.clear();
        }
        @Override
        public int getCount() {
            return ble_devices.size();
        }

        @Override
        public Object getItem(int position) {
            return ble_devices.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
        @Override
        public View getView(int position, View view, ViewGroup parent) {
            ViewHolder viewHolder;
            if (view == null) {
                view = MainActivity.this.getLayoutInflater().inflate(R.layout.list_row, null);
                viewHolder = new MainActivity.ViewHolder();
                viewHolder.text = view.findViewById(R.id.textView);
                viewHolder.bdaddr = view.findViewById(R.id.bdaddr);
                view.setTag(viewHolder);
            }
            else {
                viewHolder = (ViewHolder) view.getTag();
            }
            BluetoothDevice device = ble_devices.get(position);
            String deviceName = device.getName();
            if (deviceName != null && deviceName.length()>0) {
                viewHolder.text.setText(deviceName);
            }
            else{
                viewHolder.text.setText("Unknown device");
            }
            viewHolder.bdaddr.setText(device.getAddress());
            return view;
        }

    }
}
