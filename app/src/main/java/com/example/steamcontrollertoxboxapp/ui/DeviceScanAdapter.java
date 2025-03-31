package com.example.steamcontrollertoxboxapp.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.steamcontrollertoxboxapp.R; // Your R file
import java.util.List;

public class DeviceScanAdapter extends RecyclerView.Adapter<DeviceScanAdapter.DeviceViewHolder> {

    private final List<String> deviceAddresses;
    private final OnDeviceClickListener listener;

    public interface OnDeviceClickListener {
        void onDeviceClick(String address);
    }

    public DeviceScanAdapter(List<String> deviceAddresses, OnDeviceClickListener listener) {
        this.deviceAddresses = deviceAddresses;
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
        String address = deviceAddresses.get(position);
        holder.bind(address, listener);
    }

    @Override
    public int getItemCount() {
        return deviceAddresses.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView tvDeviceAddress; // Add TextView for name if available

        DeviceViewHolder(View itemView) {
            super(itemView);
            tvDeviceAddress = itemView.findViewById(R.id.tv_device_address);
        }

        void bind(final String address, final OnDeviceClickListener listener) {
            // TODO: Show device name if available from ScanResult, otherwise show address
            tvDeviceAddress.setText(address);
            itemView.setOnClickListener(v -> listener.onDeviceClick(address));
        }
    }
}