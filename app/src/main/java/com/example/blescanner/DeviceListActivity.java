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
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Map;

public class DeviceListActivity extends AppCompatActivity implements AdapterView.OnItemClickListener
{
    class DeviceListAdapter extends BaseAdapter
    {
        private ArrayList<DeviceInfo> mDeviceList;
        private LayoutInflater             mInflator;

        public DeviceListAdapter( Activity activity )
        {
            super();
            mDeviceList = new ArrayList<DeviceInfo>();
            mInflator = activity.getLayoutInflater();
        }

        // リストへの追加
        public void addDevice( DeviceInfo device )
        {
/*            int idx = mDeviceList.indexOf(device);
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
            return mDeviceList.size();
*/
            if( !mDeviceList.contains(device))
            {
                mDeviceList.add(device);
                notifyDataSetChanged();
            }
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

        public void setItem( int position, DeviceInfo item )
        {
            mDeviceList.set( position, item );
        }

        @Override
        public long getItemId( int position )
        {
            return position;
        }

        public class ViewHolder
        {
            TextView deviceName;
            TextView deviceAddress;
        }

        @Override
        public View getView( int position, View convertView, ViewGroup parent )
        {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if( null == convertView )
            {
                convertView = mInflator.inflate( R.layout.listitem_device_list, parent, false );
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView)convertView.findViewById( R.id.textview_deviceaddress );
                viewHolder.deviceName = (TextView)convertView.findViewById( R.id.textview_devicename );
                convertView.setTag( viewHolder );
            }
            else
            {
                viewHolder = (ViewHolder)convertView.getTag();
            }

            DeviceInfo device     = mDeviceList.get( position );
            String StrRecord = String.format("%s",device.getAddress());
            if(device.getName() != "" )
            {
                StrRecord = StrRecord.concat(" ").concat(device.getName());
            }
            if( null != StrRecord && 0 < StrRecord.length() )
            {
                viewHolder.deviceName.setText( StrRecord );
            }
            else
            {
                viewHolder.deviceName.setText( R.string.unknown_device );
            }
            viewHolder.deviceAddress.setText( String.format("ID:%04x  RSSI:%d", device.getDeviceId(), device.getRssi()) );

            return convertView;
        }
    }

    // 定数
    private static final long   SCAN_PERIOD             = 20_000;    // スキャン時間。単位はミリ秒。
    public static final  String EXTRAS_DEVICE_NAME      = "DEVICE_NAME";
    public static final  String EXTRAS_DEVICE_ADDRESS   = "DEVICE_ADDRESS";

    // メンバー変数
    private BluetoothAdapter  mBluetoothAdapter;        // BluetoothAdapter : Bluetooth処理で必要
    private DeviceListAdapter mDeviceListAdapter;    // リストビューの内容
    private Handler           mHandler;                            // UIスレッド操作ハンドラ : 「一定時間後にスキャンをやめる処理」で必要
    private boolean mScanning = false;                // スキャン中かどうかのフラグ
    private ActivityResultLauncher<Intent> startForBTenable; // Bluetooth ON 要求
    private SharedPreferences sharedPreferences;

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
                public void run()
                {
                    String address = result.getDevice().getAddress();
                    //info.scanRecord = result.getScanRecord();
                    int deviceId = result.getScanRecord().getManufacturerSpecificData().keyAt(0);
                    byte[] record = result.getScanRecord().getManufacturerSpecificData().valueAt(0);
                    int rssi = result.getRssi();
                    DeviceInfo info = new DeviceInfo(address,deviceId,record, rssi);

                    String name = sharedPreferences.getString(info.getAddress(), "");
                    info.setName(name);

                    mDeviceListAdapter.addDevice(info);
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
        setContentView( R.layout.activity_device_list );
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("設定");
        //sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences = getSharedPreferences("Settings",Context.MODE_PRIVATE);

        // 戻り値の初期化
        setResult( Activity.RESULT_CANCELED );

        // リストビューの設定
        mDeviceListAdapter = new DeviceListAdapter( this ); // ビューアダプターの初期化
        ListView listView = (ListView)findViewById( R.id.devicelist );    // リストビューの取得
        listView.setAdapter( mDeviceListAdapter );    // リストビューにビューアダプターをセット
        listView.setOnItemClickListener( this ); // クリックリスナーオブジェクトのセット

        // UIスレッド操作ハンドラの作成（「一定時間後にスキャンをやめる処理」で使用する）
        mHandler = new Handler(Looper.getMainLooper());

        // Bluetoothアダプタの取得
        BluetoothManager bluetoothManager = (BluetoothManager)getSystemService( Context.BLUETOOTH_SERVICE );
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if( null == mBluetoothAdapter )
        {    // デバイス（＝スマホ）がBluetoothをサポートしていない
            Toast.makeText( this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT ).show();
            finish();    // アプリ終了宣言
            return;
        }

        startForBTenable = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if( Activity.RESULT_CANCELED == result.getResultCode() )
                    {    // 有効にされなかった
                        Toast.makeText( this, R.string.bluetooth_is_not_working, Toast.LENGTH_SHORT ).show();
                        finish();    // アプリ終了宣言
                        return;
                    }

                }
        );
    }

    // 初回表示時、および、ポーズからの復帰時
    @Override
    protected void onResume()
    {
        super.onResume();

        // デバイスのBluetooth機能の有効化要求
        requestBluetoothFeature();

        // スキャン開始
        startScan();
    }

    // 別のアクティビティ（か別のアプリ）に移行したことで、バックグラウンドに追いやられた時
    @Override
    protected void onPause()
    {
        super.onPause();

        // スキャンの停止
        stopScan();
    }

    // デバイスのBluetooth機能の有効化要求
    private void requestBluetoothFeature()
    {
        if( mBluetoothAdapter.isEnabled() )
        {
            return;
        }
        // デバイスのBluetooth機能が有効になっていないときは、有効化要求（ダイアログ表示）
        Intent enableBtIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
        startForBTenable.launch(enableBtIntent);
    }

    // 機能の有効化ダイアログの操作結果

    // スキャンの開始
    private void startScan()
    {
        // リストビューの内容を空にする。
        mDeviceListAdapter.clear();

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
        mHandler.removeCallbacksAndMessages( null );

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

    // リストビューのアイテムクリック時の処理
    @Override
    public void onItemClick( AdapterView<?> parent, View view, int position, long id )
    {
        // クリックされたアイテムの取得
        DeviceInfo device = (DeviceInfo)mDeviceListAdapter.getItem( position );
        if( null == device )
        {
            return;
        }
        // 戻り値の設定
        //Intent intent = new Intent();
        //intent.putExtra( EXTRAS_DEVICE_NAME, device.getName() );
        //intent.putExtra( EXTRAS_DEVICE_ADDRESS, device.getAddress() );
        //setResult( Activity.RESULT_OK, intent );
        if( device.getName().equals("") )
        {
            EditText editText = new EditText(DeviceListActivity.this);
            editText.setHint("Device Name");
            new AlertDialog.Builder(DeviceListActivity.this)
                    .setTitle("表示追加設定")
                    .setMessage("表示名を設定してください\n".concat(device.getAddress()))
                    .setView(editText)
                    .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    })
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            device.setName(editText.getText().toString());
                            mDeviceListAdapter.setItem(position,device);
                            mDeviceListAdapter.notifyDataSetChanged();

                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString(device.getAddress(), device.getName());
                            editor.commit();
                        }
                    })
                    .show();
        }
        else
        {
            new AlertDialog.Builder(DeviceListActivity.this)
                    .setTitle("表示削除設定")
                    .setMessage("削除します - ".concat(device.getName()))
                    .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    })
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            device.setName("");
                            mDeviceListAdapter.setItem(position,device);
                            mDeviceListAdapter.notifyDataSetChanged();
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.remove(device.getAddress());
                            editor.commit();
                        }
                    })
                    .show();
        }
    }

    // オプションメニュー作成時の処理
    @Override
    public boolean onCreateOptionsMenu( Menu menu )
    {
        getMenuInflater().inflate( R.menu.activity_device_list, menu );
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

    // オプションメニューのアイテム選択時の処理
    @Override
    public boolean onOptionsItemSelected( MenuItem item )
    {
        switch( item.getItemId() )
        {
            case R.id.menuitem_scan:
                startScan();    // スキャンの開始
                break;
            case R.id.menuitem_stop:
                stopScan();    // スキャンの停止
                break;
            case android.R.id.home:
                finish();
                break;
            case R.id.menuitem_clear:
                Map<String,?> map = (Map<String,?>)sharedPreferences.getAll();
                if( map == null ) break;

                new AlertDialog.Builder(DeviceListActivity.this)
                        .setTitle("表示削除")
                        .setMessage("全ての表示名を削除します")
                        .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                for ( Map.Entry<String,?> entry : map.entrySet() )
                                {
                                    editor.remove(entry.getKey());
                                }
                                editor.commit();

                                for( int pos=0; pos < mDeviceListAdapter.mDeviceList.size(); pos++ )
                                {
                                    DeviceInfo info = mDeviceListAdapter.mDeviceList.get(pos);
                                    info.setName("");
                                    mDeviceListAdapter.mDeviceList.set(pos,info);
                                }
                                mDeviceListAdapter.notifyDataSetChanged();
                            }
                        })
                        .show();

                break;
        }
        return true;
    }
}
