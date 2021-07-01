package com.example.blescanner;

public class DeviceInfo
{
    DeviceInfo(String address,int deviceId,byte[] record)
    {
        this.address = address; this.deviceId = deviceId; this.record = record;
    }
    final private String address;
    final private int deviceId;
    final private byte[] record;
    private String name = "";

    public String getAddress(){ return address; }
    public int getDeviceId(){ return deviceId; }
    public byte[] getRecord(){ return record; }
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

