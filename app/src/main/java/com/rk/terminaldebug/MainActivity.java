package com.rk.terminaldebug;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "Ble" + MainActivity.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_ENABLE_LACATION = 2;
    private static final int REQUEST_ACCESS_COARSE_LOCATION = 3;

    private Button mScanDeviceBtn;
    private RecyclerView mBleDeviceListView;
    private BleDeviceItemAdapter mBleDeviceItemAdapter;

    private Object mSyncObj = new Object();
    private class HandleReadCharacteristicThread extends Thread {
        ArrayList<BluetoothGattCharacteristic> mReadCharacteristicList;
        public void setReadCharacteristicListList(ArrayList<BluetoothGattCharacteristic> list) {
            mReadCharacteristicList = list;
        }

        @Override
        public void run() {
            if (mReadCharacteristicList != null && mReadCharacteristicList.size() > 0) {
                for (int i = 0; i < mReadCharacteristicList.size(); i++) {
                    BluetoothGattCharacteristic characteristic = mReadCharacteristicList.get(i);
                    synchronized (mSyncObj) {
                        boolean readDone = false;

                        if (mBluetoothGatt != null) {
                            readDone = mBluetoothGatt.readCharacteristic(characteristic);
                        }
                        Log.i(TAG, "run characteristic uuid: " + characteristic.getUuid() + " ,readDone: " + readDone);
                        try {
                            mSyncObj.wait();
                        } catch (Exception e) {
                            Log.e(TAG, "run, wait error: " + e.getMessage());
                        }
                    }
                }

            }
        }

    }

    private BluetoothGatt mBluetoothGatt;
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                //Attempting to start service discovery
                boolean isStarted = mBluetoothGatt.discoverServices();
                Log.i(TAG, "onConnectionStateChange, is Started to discover services: " + isStarted);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
            }
        }


        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.i(TAG, "onServicesDiscovered, status: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> servicesList = mBluetoothGatt.getServices();
                ArrayList<BluetoothGattCharacteristic> list = new ArrayList<>();
                for (int i = 0; i < servicesList.size(); i++) {
                    Log.i(TAG, "onServicesDiscovered, service[" + i + "]: " + servicesList.get(i).getUuid());
                    final List<BluetoothGattCharacteristic> characteristicsList = servicesList.get(i).getCharacteristics();
                    for (int j = 0; j < characteristicsList.size(); j++) {
                        final BluetoothGattCharacteristic characteristic = characteristicsList.get(j);
                        String uuid = characteristic.getUuid().toString();
                        Log.i(TAG, "onServicesDiscovered, characteristic[" + j + "]: " + uuid);
                        if ("00002a26-0000-1000-8000-00805f9b34fb".equals(uuid)
                                || "00002a27-0000-1000-8000-00805f9b34fb".equals(uuid)
                                || "00002a28-0000-1000-8000-00805f9b34fb".equals(uuid)
                                || "00002a29-0000-1000-8000-00805f9b34fb".equals(uuid)) {
                            list.add(characteristic);


                        }
                    }
                }
                HandleReadCharacteristicThread thread = new HandleReadCharacteristicThread();
                thread.setReadCharacteristicListList(list);
                thread.start();
            }

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                try {
                    Log.i(TAG, "onCharacteristicRead, UUID: " + characteristic.getUuid().toString()
                            + ", read data: " + new String(characteristic.getValue(), "utf-8"));
                } catch (UnsupportedEncodingException e) {
                    Log.e(TAG, "onCharacteristicRead, error: " + e.getMessage());
                }
                synchronized (mSyncObj) {
                    mSyncObj.notify();
                }

            }
        }
    };

    private ArrayList<BluetoothDevice> mBleList = new ArrayList<>();

    private static BluetoothAdapter sBluetoothAdapter;

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi,
                             byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (device != null) {
                        Log.i(TAG, "onLeScan, device: " + device.getName() + ", device mac: " + device.getAddress());
                        mBleList.add(device);
                    }

                    if (mBleDeviceItemAdapter != null) {
                        Log.i(TAG, "onLeScan, data size : " + mBleDeviceItemAdapter.getItemCount());
                        mBleDeviceItemAdapter.notifyDataSetChanged();
                    }

                }
            });
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        sBluetoothAdapter = bluetoothManager.getAdapter();
        initView();
        initEvent();
        IntentFilter bleIntentFilter = new IntentFilter();
        bleIntentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mBleFoundReceiver, bleIntentFilter);
    }


    private void initView() {
        mScanDeviceBtn = (Button) findViewById(R.id.scan_btn);
        mBleDeviceListView = (RecyclerView) findViewById(R.id.ble_device_list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(OrientationHelper.VERTICAL);
        mBleDeviceListView.setLayoutManager(layoutManager);
        mBleDeviceItemAdapter = new BleDeviceItemAdapter();
        mBleDeviceListView.setAdapter(mBleDeviceItemAdapter);
    }

    private void initEvent() {
        mScanDeviceBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if (sBluetoothAdapter == null) {
                    Toast.makeText(MainActivity.this, "Not Support BLE！", Toast.LENGTH_LONG).show();
                    return;
                }

                if (!sBluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    return;
                }

                if (!isLocationOpen(MainActivity.this)) {
                    Log.i(TAG, "onClick, Build.VERSION.SDK_INT: " + Build.VERSION.SDK_INT);
                    if (Build.VERSION.SDK_INT >= 23) {
                        Intent enableLocate = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivityForResult(enableLocate, REQUEST_ENABLE_LACATION);
                        return;
                    }
                } else {
                    dynamicRequestPermission();
                }
                mBleList.clear();
                sBluetoothAdapter.startDiscovery();
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        sBluetoothAdapter.cancelDiscovery();

                    }
                }, 10000);
            }
        });
    }

    /**
     * Judge If location function is opened.
     */
    public static boolean isLocationOpen(final Context context) {
        Log.i(TAG, "isLocationOpen");
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "onActivityResult, resultCode: " + resultCode);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Ble Already Opened", Toast.LENGTH_LONG).show();
            }
        } else if (resultCode == REQUEST_ENABLE_LACATION) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Locate Service Opened", Toast.LENGTH_LONG).show();
                dynamicRequestPermission();
            }

        }
    }

    private void dynamicRequestPermission() {
        Log.i(TAG, "dynamicRequestPermission");
        //Android6.0需要动态申请权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            //请求权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_ACCESS_COARSE_LOCATION);
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {
                //判断是否需要解释
                Toast.makeText(this, "需要蓝牙权限....", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final BroadcastReceiver mBleFoundReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i(TAG, "onReceive, action: " + action);
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (device != null) {
                    Log.i(TAG, "onReceive, device: " + device.getName() + ", device mac: " + device.getAddress());
                    mBleList.add(device);
                }

                if (mBleDeviceItemAdapter != null) {
                    Log.i(TAG, "onReceive, data size : " + mBleDeviceItemAdapter.getItemCount());
                    mBleDeviceItemAdapter.notifyDataSetChanged();
                }

            }
        }
    };


    private class BleDeviceItemAdapter extends RecyclerView.Adapter<BleDeviceItemAdapter.BleDeviceVH> {

        public class BleDeviceVH extends RecyclerView.ViewHolder{
            public TextView BleName;
            public TextView BleSigStrength;
            public TextView BleMac;
            public BleDeviceVH(View v) {
                super(v);
                BleName = (TextView) v.findViewById(R.id.ble_name);
                BleSigStrength = (TextView) v.findViewById(R.id.ble_sig_strength);
                BleMac = (TextView) v.findViewById(R.id.ble_mac);
            }
        }

        @Override
        public int getItemCount() {
            Log.i(TAG, "getItemCount, data size: " + mBleList.size());
            return mBleList.size();
        }

        @Override
        public BleDeviceVH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.ble_device_item_layout, parent, false);
            return new BleDeviceVH(v);
        }

        @Override
        public void onBindViewHolder(BleDeviceVH holder, final int position) {
            Log.i(TAG, "onBindViewHolder, position: " + position);
            holder.BleName.setText(mBleList.get(position).getName());
            holder.BleSigStrength.setText("99   ");
            holder.BleMac.setText(mBleList.get(position).getAddress());
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.i(TAG, "onBindView, setOnClickListener, onClick, position: " + position);
                    BluetoothDevice device = mBleList.get(position);
                    Log.i(TAG, "onClick, device: " + device.getName() + ", address: " + device.getAddress());
                    mBluetoothGatt = device.connectGatt(MainActivity.this, true, mGattCallback);
                    //TODO: add click event, to connect ble device.
                }
            });
        }

    }

    @Override
    protected void onStart() {
        Log.i(TAG, "onStart");
        super.onStart();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
        if (mBleFoundReceiver != null) {
            unregisterReceiver(mBleFoundReceiver);
        }
    }
}
