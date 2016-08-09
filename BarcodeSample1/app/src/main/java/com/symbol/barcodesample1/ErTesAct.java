package com.symbol.barcodesample1;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.symbol.emdk.EMDKManager;
import com.symbol.emdk.EMDKResults;
import com.symbol.emdk.barcode.BarcodeManager;
import com.symbol.emdk.barcode.ScanDataCollection;
import com.symbol.emdk.barcode.Scanner;
import com.symbol.emdk.barcode.ScannerConfig;
import com.symbol.emdk.barcode.ScannerException;
import com.symbol.emdk.barcode.ScannerInfo;
import com.symbol.emdk.barcode.ScannerResults;
import com.symbol.emdk.barcode.StatusData;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by EricH on 8/8/2016.
 */
public class ErTesAct extends Activity implements EMDKManager.EMDKListener, Scanner.DataListener,
        Scanner.StatusListener, BarcodeManager.ScannerConnectionListener, View.OnClickListener{

    @BindView(R.id.ok)protected Button ok;

    private EMDKManager emdkManager = null;
    private BarcodeManager barcodeManager = null;
    private Scanner scanner = null;


    private List<ScannerInfo> deviceList = null;
    private ScannerInfo myScannerInfo = null;
    private String t = "1234";
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.one_point);
        ButterKnife.bind(this);
        deviceList = new ArrayList<>();

        EMDKResults results = EMDKManager.getEMDKManager(getApplicationContext(), this);
        if (results.statusCode != EMDKResults.STATUS_CODE.SUCCESS) {
            //
            Log.e(t,results.statusCode.name());
        }
        Log.d(t,results.statusCode.name());
        handler = new Handler();
        ok.setOnClickListener(this);
    }

    private void initScanner() {

        if (scanner == null) {

            if ((deviceList != null) && (deviceList.size() != 0) ) {
                scanner = barcodeManager.getDevice(myScannerInfo);
            } else {
                Log.e(t,"initScanner Status: " + "Failed to get the specified scanner device! Please close and restart the application.");
                return;
            }

            if (scanner != null) {
                scanner.addDataListener(this);
                scanner.addStatusListener(this);
                try {
                    scanner.enable();
                } catch (ScannerException e) {
                    Log.e(t,"ScannerException Status: " + e.getMessage());
                }
            }else{
                Log.e(t,"initScanner Status: " + "Failed to initialize the scanner device.");
            }
        }
    }
    private void deInitScanner() {//cancel previous scanner

        if (scanner != null) {
            try {
                scanner.cancelRead();
                scanner.disable();
            } catch (ScannerException e) {
                Log.e(t,"deInitScanner Status: " + e.getMessage());
            }
            scanner.removeDataListener(this);
            scanner.removeStatusListener(this);
            try{
                scanner.release();
            } catch (ScannerException e) {
                Log.e(t,"deInitScanner Status: " + e.getMessage());
            }
            scanner = null;
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        // The application is in background
        stopScan();
        // De-initialize scanner
        deInitScanner();

        // Remove connection listener
        if (barcodeManager != null) {
            barcodeManager.removeConnectionListener(this);
            barcodeManager = null;
            deviceList = null;
        }

        // Release the barcode manager resources
        if (emdkManager != null) {
            emdkManager.release(EMDKManager.FEATURE_TYPE.BARCODE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The application is in foreground
        Log.d(t,"onResume");
        // Acquire the barcode manager resources
        constructBarCOdeScannerAndScan();
    }

    private void constructBarCOdeScannerAndScan() {
        if (emdkManager != null) {
            barcodeManager = (BarcodeManager) emdkManager.getInstance(EMDKManager.FEATURE_TYPE.BARCODE);
            // Add connection listener
            if (barcodeManager != null) {
                barcodeManager.addConnectionListener(this);
                Log.d(t,"barcodeManage add connection listener");
            }
            // Enumerate scanner devices
            enumerateScannerDevices();
            // Initialize scanner
            initScanner();
            setTrigger();
            setDecoders();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    startScan();
                }
            });
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    ErTesAct.this.finish();
                }
            },10*1000);
        }
    }

    private void enumerateScannerDevices() {//getsupport device list

        if (barcodeManager != null) {
            deviceList = barcodeManager.getSupportedDevicesInfo();
            if ((deviceList != null) && (deviceList.size() != 0)) {
                for(ScannerInfo scnInfo: deviceList) {
                    if(scnInfo.getConnectionType() == ScannerInfo.ConnectionType.INTERNAL && scnInfo.getDeviceType() == ScannerInfo.DeviceType.IMAGER )
                        myScannerInfo = scnInfo;
                }
            }
            else {
                Log.d(t,"Status: " + "Failed to get the list of supported scanner devices! Please close and restart the application.");
            }
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // De-initialize scanner
        deInitScanner();

        // Remove connection listener
        closeBarCodeAndEmdkManage();

    }@Override
    public void onOpened(EMDKManager emdkManager) {
        Log.d(t,"Status: " + "EMDK open success!");
        this.emdkManager = emdkManager;
        constructBarCOdeScannerAndScan();
    }

    @Override
    public void onClosed() {
        closeBarCodeAndEmdkManage();
        Log.d(t,"Status: " + "EMDK closed unexpectedly! Please close and restart the application.");
    }

    private void closeBarCodeAndEmdkManage() {
        if (emdkManager != null) {
            // Remove connection listener
            if (barcodeManager != null){
                barcodeManager.removeConnectionListener(this);
                barcodeManager = null;
            }
            // Release all the resources
            emdkManager.release();
            emdkManager = null;
        }
    }

    @Override
    public void onData(ScanDataCollection scanDataCollection) {
        if ((scanDataCollection != null) && (scanDataCollection.getResult() == ScannerResults.SUCCESS)) {
            ArrayList<ScanDataCollection.ScanData> scanData = scanDataCollection.getScanData();
            for(ScanDataCollection.ScanData data : scanData) {
                String dataString =  data.getData();
                giveResult(dataString);
            }
        }
    }

    private void giveResult(String dataString) {
        Intent i = new Intent();
        Log.d(t,dataString);
        i.putExtra("123",dataString);
        setResult(RESULT_OK,i);
        finish();
    }

    @Override
    public void onConnectionChange(ScannerInfo scannerInfo, BarcodeManager.ConnectionState connectionState) {
        Log.d(t,"onConnectionChange ");
        switch(connectionState) {
            case CONNECTED:
                deInitScanner();
                initScanner();
                setTrigger();
                setDecoders();
                break;
            case DISCONNECTED:
                deInitScanner();
                break;
        }
    }
    private void setTrigger() {
        Log.d(t,"setTrigger");
        if (scanner == null) {
            initScanner();
        }
        if (scanner != null) {
            // Selected "SOFT"
            scanner.triggerType = Scanner.TriggerType.SOFT_ONCE;
            Log.d(t,"setTrigger: trigger type = "+scanner.triggerType.toString());
        }
    }

    private void setDecoders() {
        Log.d(t,"setDecoders");
        if (scanner == null) {
            initScanner();
        }
        if ((scanner != null) && (scanner.isEnabled())) {
            try {
                ScannerConfig config = scanner.getConfig();
                // Set EAN8
                config.decoderParams.ean8.enabled = true;
                // Set EAN13
                config.decoderParams.ean13.enabled = true;
                // Set Code39
                config.decoderParams.code39.enabled = true;
                config.decoderParams.code128.enabled = true;
                config.decoderParams.code11.enabled = true;
                scanner.setConfig(config);
                Log.d(t,"scan trigger type = "+scanner.triggerType.toString());
            } catch (ScannerException e) {
                Log.d(t,"Status: " + e.getMessage());
            }
        }
    }
    private void startScan() {
        if(scanner == null) {
            initScanner();
        }
        if (scanner != null) {
            setTrigger();
            try {
                // Submit a new read.
                scanner.read();
                Log.d(t,"startScan");
            } catch (ScannerException e) {
                Log.e(t,"Status: " + e.getMessage());
            }
        }
    }

    private void stopScan() {

        if (scanner != null) {
            try {
                // Cancel the pending read.
                scanner.cancelRead();
                Log.d(t,"stopScan");
            } catch (ScannerException e) {
                Log.e(t,"Status: " + e.getMessage());
            }
        }
    }
    @Override
    public void onStatus(StatusData statusData) {
        StatusData.ScannerStates state = statusData.getState();
        Log.d(t,"onStatus new "+state.name());
    }

    @Override
    public void onClick(View v) {
        startScan();
    }
}
