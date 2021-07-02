package com.example.blescanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Build;
import android.Manifest;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Map;

public class MainActivity extends AppCompatActivity
{
    private class DeviceListAdapter extends BaseAdapter
    {
        private ArrayList<DeviceInfo> mDeviceList;
        private LayoutInflater mInflator;

        public DeviceListAdapter( Activity activity )
        {
            super();
            mDeviceList = new ArrayList<DeviceInfo>();
            mInflator = activity.getLayoutInflater();
        }

        // リストへの追加
        public void addDevice( DeviceInfo device )
        {
            int idx = mDeviceList.indexOf(device);
            if( idx == -1 )
            {    // 加えられていなければ加える
                mDeviceList.add( device );
            }
            else
            {
                // すでにあれば置き換える
                mDeviceList.set(idx, device);
            }
            notifyDataSetChanged();    // ListViewの更新
/*            return mDeviceList.size();

            if( !mDeviceList.contains(device))
            {
                mDeviceList.add(device);
                notifyDataSetChanged();
            }
*/
        }

        // リストのクリア
        public void clear()
        {
            mDeviceList.clear();
            notifyDataSetChanged();    // ListViewの更新
        }

        @Override
        public int getCount()
        {
            return mDeviceList.size();
        }

        @Override
        public Object getItem( int position )
        {
            return mDeviceList.get( position );
        }

        @Override
        public long getItemId( int position )
        {
            return position;
        }

        class ViewHolder
        {
            TextView deviceName;
            TextView deviceAddress;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent )
        {
            MainActivity.DeviceListAdapter.ViewHolder viewHolder;
            // General ListView optimization code.
            if( null == convertView )
            {
                convertView = mInflator.inflate( R.layout.listitem_main, parent, false );
                viewHolder = new MainActivity.DeviceListAdapter.ViewHolder();
                viewHolder.deviceAddress = (TextView)convertView.findViewById( R.id.textview_subinfo );
                viewHolder.deviceName = (TextView)convertView.findViewById( R.id.textview_maininfo );
                convertView.setTag( viewHolder );
            }
            else
            {
                viewHolder = (MainActivity.DeviceListAdapter.ViewHolder)convertView.getTag();
            }

            DeviceInfo device     = mDeviceList.get( position );
            // アドレスの表示
            String strAddress = "";
            // 名前とセンサ情報
            String strRecord = device.getName().concat(" ");
            if( device.getDeviceId() != 0x9999 )
            {
                // デバイスIDが違う場合
                strRecord = strRecord.concat("Unknown Device");
            }
            else
            {
                // データレコード取得
                byte[] record = device.getRecord();
                if( record == null )
                {
                    // 取得できなかった場合
                    strRecord = strRecord.concat("Illegal Data");
                }
                else
                {
                    // バッテリ電圧
                    double battery = record[5];
                    long record6 = record[6]; record6 &= 0xff;
                    battery += (double)(record6/16*10 + record6%16)/100;
                    strAddress = String.format("BATT : %1.2fV  RSSI : %d", battery, device.getRssi());

                    // 温度
                    int pol = record[2];
                    long record3 = record[3]; record3 &= 0xff;
                    double tmp = record3/16*10 + record3%16;
                    long record4 = record[4]; record4 &= 0xff;
                    tmp += (double)(record4/16*10 + record4%16)/100;
                    if( pol != 0 ) tmp *= -1;
                    strRecord = strRecord.concat(String.format("%2.1f℃", tmp));

                    // 湿度と気圧
                    if( record.length == 11 )
                    {
                        long record7 = record[7]; record7 &= 0xff;
                        double humidity = record7/16*10 + record7%16;
                        long record8 = record[8]; record8 &= 0xff;
                        humidity += (double)(record8/16*10 + record8%16)/100;
                        long record9 = record[9]; record9 &= 0xff;
                        long pressure = record9/16*1000 + record9%16*100;
                        long record10 = record[10]; record10 &= 0xff;
                        pressure += record10/16*10 + record10%16;
                        strRecord = strRecord.concat(String.format("  %2.1f%%  %dpa", humidity, pressure));
                    }
                }
            }
            viewHolder.deviceName.setText( strRecord );
            viewHolder.deviceAddress.setText( strAddress );

            return convertView;
        }
    }
    // 定数
    private static final long   SCAN_PERIOD             = 20_000;    // スキャン時間。単位はミリ秒。
    private static final long   SCAN_INTERVAL             = 60_000;    // スキャン時間。単位はミリ秒。
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    private ActivityResultLauncher<Intent> startForBTscan;
    private SharedPreferences sharedPreferences;
    // メンバー変数
    private BluetoothAdapter mBluetoothAdapter;    // BluetoothAdapter : Bluetooth処理で必要
    private ActivityResultLauncher<Intent> startForBTon; // Bluetooth ON 要求
    private Map<String,?> deviceNameMap;

    private MainActivity.DeviceListAdapter mDeviceListAdapter;    // リストビューの内容
    private Handler mHandler;                            // UIスレッド操作ハンドラ : 「一定時間後にスキャンをやめる処理」で必要
    private boolean mScanning = false;                // スキャン中かどうかのフラグ

    // デバイススキャンコールバック
    private ScanCallback mLeScanCallback = new ScanCallback()
    {
        // スキャンに成功（アドバタイジングは一定間隔で常に発行されているため、本関数は一定間隔で呼ばれ続ける）
        @Override
        public void onScanResult( int callbackType, final ScanResult result )
        {
            super.onScanResult( callbackType, result );
            runOnUiThread( new Runnable()
            {
                @Override
                public void run() {
                    String address = result.getDevice().getAddress();
                    //info.scanRecord = result.getScanRecord();
                    int deviceId = result.getScanRecord().getManufacturerSpecificData().keyAt(0);
                    byte[] record = result.getScanRecord().getManufacturerSpecificData().valueAt(0);
                    int rssi = result.getRssi();
                    DeviceInfo info = new DeviceInfo(address, deviceId, record, rssi);

                    if (deviceNameMap == null)
                    {
                        // デバイス名が設定されていないときは表示を行わない
                        stopScan();
                        mScanning = false;
                    }
                    else
                    {
                        // デバイスの情報を表示
                        String name = sharedPreferences.getString(info.getAddress(), "");
                        if (!name.equals("")) {
                            info.setName(name);
                            mDeviceListAdapter.addDevice(info);

                            // デバイス名のリストから該当のアドレスを削除。デバイス名が全てなくなったらスキャン終了
                            deviceNameMap.remove(info.getAddress());
                            if (deviceNameMap.size() == 0)
                            {
                                stopScan();
                                mScanning = false;
                            }
                        }
                    }
                }
            } );
        }

        // スキャンに失敗
        @Override
        public void onScanFailed( int errorCode )
        {
            super.onScanFailed( errorCode );
        }
    };
    @Override
    protected void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );
        //sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences = getSharedPreferences("Settings",Context.MODE_PRIVATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
// Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
            }
        }

        // Android端末がBLEをサポートしてるかの確認
        if( !getPackageManager().hasSystemFeature( PackageManager.FEATURE_BLUETOOTH_LE ) )
        {
            Toast.makeText( this, R.string.ble_is_not_supported, Toast.LENGTH_SHORT ).show();
            finish();    // アプリ終了宣言
            return;
        }

        // UIスレッド操作ハンドラの作成（「一定時間後にスキャンをやめる処理」で使用する）
        mHandler = new Handler(Looper.getMainLooper());

        // Bluetoothアダプタの取得
        BluetoothManager bluetoothManager = (BluetoothManager)getSystemService( Context.BLUETOOTH_SERVICE );
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if( null == mBluetoothAdapter )
        {    // Android端末がBluetoothをサポートしていない
            Toast.makeText( this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT ).show();
            finish();    // アプリ終了宣言
            return;
        }

        startForBTon = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if( Activity.RESULT_CANCELED == result.getResultCode() )
                    {    // 有効にされなかった
                        Toast.makeText( MainActivity.this, R.string.bluetooth_is_not_working, Toast.LENGTH_SHORT ).show();
                        finish();    // アプリ終了宣言
                        return;
                    }

                }
        );

        // リストビューの設定
        mDeviceListAdapter = new MainActivity.DeviceListAdapter( this ); // ビューアダプターの初期化
        ListView listView = (ListView)findViewById( R.id.devicelist );    // リストビューの取得
        listView.setAdapter( mDeviceListAdapter );    // リストビューにビューアダプターをセット

        startForBTscan = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        mDeviceListAdapter.clear();
                    }
                }
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                } else {
                    Toast.makeText(this, R.string.BluetoothScan_need_Permissions, Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    // Android端末のBluetooth機能の有効化要求
    private void requestBluetoothFeature()
    {
        if( mBluetoothAdapter.isEnabled() )
        {
            return;
        }
        // デバイスのBluetooth機能が有効になっていないときは、有効化要求（ダイアログ表示）
        Intent enableBtIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
        startForBTon.launch(enableBtIntent);
    }

    // オプションメニュー作成時の処理
    @Override
    public boolean onCreateOptionsMenu( Menu menu )
    {
        getMenuInflater().inflate( R.menu.activity_main, menu );
        if( !mScanning )
        {
            menu.findItem( R.id.menuitem_stop ).setVisible( false );
            menu.findItem( R.id.menuitem_scan ).setVisible( true );
            menu.findItem( R.id.menuitem_progress ).setActionView( null );
        }
        else
        {
            menu.findItem( R.id.menuitem_stop ).setVisible( true );
            menu.findItem( R.id.menuitem_scan ).setVisible( false );
            menu.findItem( R.id.menuitem_progress ).setActionView( R.layout.ationbar_indeterminate_progress );
        }
        return true;
    }

    // 初回表示時、および、ポーズからの復帰時
    @Override
    protected void onResume()
    {
        super.onResume();

        // デバイスのBluetooth機能の有効化要求
        requestBluetoothFeature();

        // スキャン開始
        //startScan();
        mHandler.post(cyclicStart);
    }

    // 別のアクティビティ（か別のアプリ）に移行したことで、バックグラウンドに追いやられた時
    @Override
    protected void onPause()
    {
        super.onPause();

        // スキャンの停止
        stopScan();
    }

    // スキャンの開始
    private void startScan()
    {
        // リストビューの内容を空にする。
        //mDeviceListAdapter.clear();

        // BluetoothLeScannerの取得
        // ※Runnableオブジェクト内でも使用できるようfinalオブジェクトとする。
        final android.bluetooth.le.BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
        if( null == scanner )
        {
            return;
        }

        // スキャン開始（一定時間後にスキャン停止する）
        mHandler.postDelayed( new Runnable()
        {
            @Override
            public void run()
            {
                mScanning = false;
                scanner.stopScan( mLeScanCallback );

                // メニューの更新
                invalidateOptionsMenu();
            }
        }, SCAN_PERIOD );

        // デバイス名のマップを作成
        deviceNameMap = (Map<String,?>)sharedPreferences.getAll();
        if(deviceNameMap.size() == 0)
        {
            // 名前設定されたデバイスがない場合はアラート表示
            new AlertDialog.Builder(this)
                    .setTitle("Usage")
                    .setMessage("デバイスの表示名が登録されていません。設定画面にてデバイスのIPに名前を登録してください。")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .show();

        }

        ScanSettings scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        mScanning = true;
        scanner.startScan( null, scanSettings, mLeScanCallback );

        // メニューの更新
        invalidateOptionsMenu();
    }

    // スキャンの停止
    private void stopScan()
    {
        // 一定期間後にスキャン停止するためのHandlerのRunnableの削除
        //mHandler.removeCallbacksAndMessages( null );

        // BluetoothLeScannerの取得
        android.bluetooth.le.BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
        if( null == scanner )
        {
            return;
        }
        mScanning = false;
        scanner.stopScan( mLeScanCallback );

        // メニューの更新
        invalidateOptionsMenu();
    }
    // オプションメニューのアイテム選択時の処理
    @Override
    public boolean onOptionsItemSelected( MenuItem item )
    {
        switch( item.getItemId() )
        {
            case R.id.menuitem_scan:
                //startScan();    // スキャンの開始
                mHandler.post(cyclicStart);
                break;
            case R.id.menuitem_stop:
                stopScan();    // スキャンの停止
                break;
            case R.id.menuitem_setting:
                Intent scanDeviceActivityIntent = new Intent(this,DeviceListActivity.class);
                startForBTscan.launch(scanDeviceActivityIntent);
                break;
        }
        return true;
    }

    final Runnable cyclicStart = new Runnable() {
        @Override
        public void run() {
            startScan();
            mHandler.postDelayed(this,SCAN_INTERVAL);
        }
    };
}
