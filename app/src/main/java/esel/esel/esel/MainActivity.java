package esel.esel.esel;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.util.List;

import esel.esel.esel.datareader.Datareader;
import esel.esel.esel.datareader.EsNowDatareader;
import esel.esel.esel.datareader.SGV;
import esel.esel.esel.receivers.ReadReceiver;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;
import esel.esel.esel.util.ToastUtils;

public class MainActivity extends MenuActivity {

    private Button buttonReadValue;
    private Button buttonSync;
    private Button buttonExport;
    private TextView textViewValue;

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupView(R.layout.activity_main);
        askForBatteryOptimizationPermission();
        askForNotificationAccess();
        buttonReadValue = (Button) findViewById(R.id.button_readvalue);
        buttonSync = (Button) findViewById(R.id.button_manualsync);
        buttonExport = (Button) findViewById(R.id.button_exportdata);
        textViewValue = (TextView) findViewById(R.id.textview_main);

        /*FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });*/

        SharedPreferences.OnSharedPreferenceChangeListener settingsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals("use_patched_es")){
                    if (SP.getBoolean("use_patched_es", false)){
                        Esel.getsInstance().startKeepAliveService();
                        EselLog.LogV(TAG, "START patched_es keepAlive recievers");
                        Esel.getsInstance().startReadReceiver();
                        EselLog.LogV(TAG, "START patched_es Read recievers");
                    } else {
                        Esel.getsInstance().stopReadReceiver();
                        EselLog.LogV(TAG, "STOP patched_es Read recievers");
                        Esel.getsInstance().stopKeepAliveService();
                        EselLog.LogV(TAG, "STOP patched_es keepAlive recievers");
                    }
                } else if (key.equals("esdms_us") || key.equals("use_esdms") ) {
                    SP.putString("esnow_token", "");
                }
            }
        };
        SP.sharedPreferences.registerOnSharedPreferenceChangeListener(settingsListener);

        buttonReadValue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {

                    long currentTime = System.currentTimeMillis();

                    long syncTime = 30 * 60 * 1000L;

                    boolean use_esdms = SP.getBoolean("use_esdms",false);
                    if(use_esdms){
                        class DataHandler implements EsNowDatareader.ProcessResultI{
                            @Override
                            public void ProcessResult(List<SGV> data) {
                                if (data != null && data.size() > 0) {
                                    textViewValue.setText("");
                                    for (int i = 0; i < data.size(); i++) {
                                        SGV sgv = data.get(i);
                                        textViewValue.append(sgv.toString() +" " + sgv.direction + "\n");
                                        //LocalBroadcaster.broadcast(sgv);
                                        EselLog.LogI(TAG,String.valueOf(sgv.value) + " " + sgv.direction);
                                    }
                                } else {
                                    EselLog.LogE(TAG,"No access to eversensedms",true);
                                }
                            }
                        }

                        EsNowDatareader reader = new EsNowDatareader();
                        reader.queryCurrentValue(new DataHandler());



                    }else {
                        List<SGV> valueArray = Datareader.readDataFromContentProvider(getBaseContext(), 6, currentTime - syncTime);

                        if (valueArray != null && valueArray.size() > 0) {
                            textViewValue.setText("");
                            for (int i = 0; i < valueArray.size(); i++) {
                                SGV sgv = valueArray.get(i);
                                textViewValue.append(sgv.toString() +" " + sgv.direction+ "\n");
                                //LocalBroadcaster.broadcast(sgv);
                                EselLog.LogI(TAG,String.valueOf(sgv.value) + " " + sgv.direction);
                            }
                        } else {
                            EselLog.LogE(TAG,"DB not readable!",true);
                        }
                    }


                } catch (android.database.CursorIndexOutOfBoundsException eb) {
                    eb.printStackTrace();
                    EselLog.LogW(TAG,"DB is empty!\nIt can take up to 15min with running Eversense App until values are available!",true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        buttonSync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int sync = 8;
                try {

                    sync = SP.getInt("max-sync-hours", sync);


                } catch (Exception e) {
                    e.printStackTrace();
                }


                ReadReceiver receiver = new ReadReceiver();
                receiver.FullSync(getBaseContext(), sync);
                textViewValue.setText("Read values from DB\n(last " + sync + " hours)");

            }
        });

        buttonExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int sync = 8;

                try {

                    sync = SP.getInt("max-sync-hours", sync);

                } catch (Exception e) {
                    e.printStackTrace();
                }


                    String filename = "esel_output_" + System.currentTimeMillis() + ".json";
                    String path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + Environment.DIRECTORY_DOWNLOADS;
                    File file = new File(path, filename);

                    ReadReceiver receiver = new ReadReceiver();

                     receiver.FullExport(getBaseContext(),file, sync);

                    textViewValue.setText("Created file " + file.getAbsoluteFile() + " containing values from DB\n(last " + sync + " hours)");


                }
        });

    }

    private void askForNotificationAccess() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            final String packageName = getPackageName();
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getBaseContext());

            boolean nlenabled = NotificationManagerCompat.getEnabledListenerPackages(getBaseContext()).contains(packageName);

            if (!nlenabled) {
                final Runnable askNotificationAccessRunnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            final String msg = "Device does not appear to support notification access!";
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ToastUtils.makeToast("Device does not appear to support notification access!");
                                }
                            });
                        }
                    }
                };

                try {
                    final Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    intent.setData(Uri.parse("package:" + packageName));
                    //startActivity(intent);


                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Please Allow Permission")
                            .setMessage("For data access in Companion Mode, ESEL needs access to the System Notifications.\n" +
                                    "If the settings are not available due to restricted settings, see 'https://support.google.com/android/answer/12623953'.")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    SystemClock.sleep(100);
                                    MainActivity.this.runOnUiThread(askNotificationAccessRunnable);
                                    dialog.dismiss();
                                }
                            }).show();
                } catch (Exception e) {
                    ToastUtils.makeToast("Please whitelist ESEL in the phone settings.");
                }
            }
        }

}

    private void askForBatteryOptimizationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final String packageName = getPackageName();
            final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                final Runnable askOptimizationRunnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final Intent intent = new Intent();
                            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + packageName));
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            final String msg = "Device does not appear to support battery optimization whitelisting!";
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ToastUtils.makeToast("Device does not appear to support battery optimization whitelisting!");
                                }
                            });
                        }
                    }
                };

                try {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Please Allow Permission")
                            .setMessage("ESEL needs battery optimalization whitelisting for proper performance")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    SystemClock.sleep(100);
                                    MainActivity.this.runOnUiThread(askOptimizationRunnable);
                                    dialog.dismiss();
                                }
                            }).show();
                } catch (Exception e) {
                    ToastUtils.makeToast("Please whitelist ESEL in the phone settings.");
                }
            }
        }
    }
}
