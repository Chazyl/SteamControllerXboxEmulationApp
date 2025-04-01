package com.example.steamcontrollertoxboxapp.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.steamcontrollertoxboxapp.R;
import com.example.steamcontrollertoxboxapp.ble.BleDevice;
import java.util.List;

public class DeviceScanAdapter extends RecyclerView.Adapter<DeviceScanAdapter.DeviceViewHolder> {

    private final List<BleDevice> devices;
    private final OnDeviceClickListener listener;

    public interface OnDeviceClickListener {
        void onDeviceClick(String address);
    }

    public DeviceScanAdapter(List<BleDevice> devices, OnDeviceClickListener listener) {
        this.devices = devices;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        BleDevice device = devices.get(position);
        holder.bind(device, listener);
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView tvDeviceAddress;

        DeviceViewHolder(View itemView) {
            super(itemView);
            tvDeviceAddress = itemView.findViewById(R.id.tv_device_address);
        }

        void bind(final BleDevice device, final OnDeviceClickListener listener) {
            String displayText = device.getName() != null ? 
                device.getName() + "\n" + device.getAddress() : 
                device.getAddress();
            tvDeviceAddress.setText(displayText);
            itemView.setOnClickListener(v -> listener.onDeviceClick(device.getAddress()));
        }
    }
}
