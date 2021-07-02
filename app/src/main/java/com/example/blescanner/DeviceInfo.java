package com.example.blescanner;

public class DeviceInfo
{
    DeviceInfo(String address,int deviceId,byte[] record, int rssi)
    {
        this.address = address; this.deviceId = deviceId; this.record = record; this.rssi = rssi;
    }
    final private String address;
    final private int deviceId;
    final private byte[] record;
    final int rssi;
    private String name = "";

    public String getAddress(){ return address; }
    public int getDeviceId(){ return deviceId; }
    public byte[] getRecord(){ return record; }
    public int getRssi(){ return rssi; }
    public String getName(){ return name; }
    public void setName( String name ){ this.name = name; }

    // アドレスが同じならば等しいとする
    public boolean equals(Object o)
    {
        if( o == this ) return true;
        if( o.getClass() != this.getClass() ) return false;

        DeviceInfo d = (DeviceInfo)o;
        return d.address.equals(this.address);
    }
}

