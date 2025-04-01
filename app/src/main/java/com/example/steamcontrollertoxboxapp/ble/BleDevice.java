package com.example.steamcontrollertoxboxapp.ble;

public class BleDevice {
    private final String address;
    private final String name;

    public BleDevice(String address, String name) {
        this.address = address;
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name != null ? name + " (" + address + ")" : address;
    }
}
