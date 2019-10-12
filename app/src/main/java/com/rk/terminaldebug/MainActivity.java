package com.rk.terminaldebug;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "Ble" + MainActivity.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 1;

    private Button mScanDeviceBtn;
    private RecyclerView mBleDeviceListView;
    private BleDeviceItemAdapter mBleDeviceItemAdapter;

    private ArrayList<BluetoothDevice> mBleList = new ArrayList<>();

    private static final BluetoothAdapter BLUETOOTH_ADAPTER = BluetoothAdapter.getDefaultAdapter();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
                if (BLUETOOTH_ADAPTER == null) {
                    Toast.makeText(MainActivity.this, "Not Support BLE！", Toast.LENGTH_LONG);
                    return;
                }

                if (!BLUETOOTH_ADAPTER.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    return;
                }
                mBleList.clear();
                BLUETOOTH_ADAPTER.startDiscovery();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Ble Already Opened", Toast.LENGTH_LONG);
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
        public void onBindViewHolder(BleDeviceVH holder, int position) {
            Log.i(TAG, "onBindViewHolder, position: " + position);
            holder.BleName.setText(mBleList.get(position).getName());
            holder.BleSigStrength.setText("99   ");
            holder.BleMac.setText(mBleList.get(position).getAddress());
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
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
