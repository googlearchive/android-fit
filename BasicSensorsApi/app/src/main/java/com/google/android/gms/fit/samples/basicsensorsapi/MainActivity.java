package com.google.android.gms.fit.samples.basicsensorsapi;

import android.app.Activity;
import android.content.IntentSender;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fit.samples.common.logger.Log;
import com.google.android.gms.fit.samples.common.logger.LogView;
import com.google.android.gms.fit.samples.common.logger.LogWrapper;
import com.google.android.gms.fit.samples.common.logger.MessageOnlyLogFilter;
import com.google.android.gms.fitness.DataPoint;
import com.google.android.gms.fitness.DataSource;
import com.google.android.gms.fitness.DataSourceListener;
import com.google.android.gms.fitness.DataSourcesRequest;
import com.google.android.gms.fitness.DataSourcesResult;
import com.google.android.gms.fitness.DataType;
import com.google.android.gms.fitness.DataTypes;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessScopes;
import com.google.android.gms.fitness.SensorRequest;
import com.google.android.gms.fitness.Value;

import java.util.concurrent.TimeUnit;


public class MainActivity extends Activity {
    public static final String TAG = "BasicSensorsApi";
    private static final int REQUEST_OAUTH = 1;
    private GoogleApiClient mClient = null;

    // Need to hold a reference to this listener, as it's passed into the "unregister"
    // method in order to stop all sensors from sending data to this listener.
    private DataSourceListener mListener;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // This method sets up our custom logger, which will print all log messages to the device
        // screen, as well as to adb logcat.
        initializeLogging();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Connect to the Fitness API
        connectFitness();
    }

    private void connectFitness() {
        Log.i(TAG, "Connecting...");
        // Create the Google API Client
        mClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.API)
                .addScope(FitnessScopes.SCOPE_ACTIVITY_READ)
                .addScope(FitnessScopes.SCOPE_LOCATION_READ)
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {

                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Connected!!!");
                                // Now you can make calls to the Fitness APIs.  What to do?
                                //Find some data sources!
                                findFitnessDataSources();
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                // If your connection to the sensor gets lost at some point,
                                // you'll be able to determine the reason and react to it here.
                                if (i == ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.i(TAG, "Connection lost.  Cause: Network Lost.");
                                } else if (i == ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.i(TAG, "Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                )
                .addOnConnectionFailedListener(
                        new GoogleApiClient.OnConnectionFailedListener() {
                            // Called whenever the API client fails to connect.
                            @Override
                            public void onConnectionFailed(ConnectionResult result) {
                                Log.i(TAG, "Connection failed. Cause: " + result.toString());
                                if (!result.hasResolution()) {
                                    // Show the localized error dialog
                                    GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(),
                                            MainActivity.this, 0).show();
                                    return;
                                }
                                // The failure has a resolution. Resolve it.
                                // Called typically when the app is not yet authorized, and an
                                // authorization dialog is displayed to the user.
                                try {
                                    Log.i(TAG, "Attempting to resolve failed connection");
                                    result.startResolutionForResult(MainActivity.this,
                                            REQUEST_OAUTH);
                                } catch (IntentSender.SendIntentException e) {
                                    Log.e(TAG, "Exception while starting resolution activity", e);
                                }
                            }
                        }
                )
                .build();
        mClient.connect();
    }

    /**
     * Finds available data sources.  If the application cares about a data type but doesn't care
     * about the source of the data, this can be skipped entirely, instead calling
     * register(client, request), where the request contains the desired data type.
     */
    private void findFitnessDataSources() {
        PendingResult<DataSourcesResult> dataSources =
                Fitness.SensorsApi.findDataSources(mClient, new DataSourcesRequest.Builder()
                        // At least one datatype must be specified.
                        .setDataTypes(
                                DataTypes.ACTIVITY_SAMPLE,
                                DataTypes.LOCATION)
                        // Can specify whether data type is raw or derived.
                        //.setDataSourceTypes(DataSource.TYPE_DERIVED)
                        .build());

        dataSources.setResultCallback(new ResultCallback<DataSourcesResult>() {
            @Override
            public void onResult(DataSourcesResult dataSourcesResult) {
                Log.i(TAG, "Result: " + dataSourcesResult.getStatus().toString());
                for (DataSource dataSource : dataSourcesResult.getDataSources()) {
                    Log.i(TAG, "Data source found: " + dataSource.toString());
                    Log.i(TAG, "Data Source type: " + dataSource.getDataType().getName());

                    //Let's register a listener to receive Activity data!
                    if (dataSource.getDataType().equals(DataTypes.ACTIVITY_SAMPLE)
                            && mListener == null) {
                        Log.i(TAG, "Data source for ACTIVITY_SAMPLE found!  Registering.");
                        registerFitnessDataListener(dataSource, DataTypes.ACTIVITY_SAMPLE);
                    }
                }
            }
        });
    }

    private void registerFitnessDataListener(DataSource dataSource, DataType dataType) {
        mListener = new DataSourceListener() {
            @Override
            public void onEvent(DataPoint dataPoint) {
                for (DataType.Field field : dataPoint.getDataType().getFields()) {
                    Value val = dataPoint.getValue(field);
                    Log.i(TAG, "Detected DataPoint field: " + field.getName());
                    Log.i(TAG, "Detected DataPoint value: " + val);
                }
            }
        };

        PendingResult<Status> regResult = Fitness.SensorsApi.register(
                mClient,
                new SensorRequest.Builder()
                        .setDataSource(dataSource) // Can be omitted.
                        .setDataType(dataType) // Can't be omitted.
                        .setSamplingRate(10, TimeUnit.SECONDS)
                        .build(),
                mListener);

        regResult.setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                if (status.isSuccess()) {
                    Log.i(TAG, "Listener registered!");
                } else {
                    Log.i(TAG, "Listener not registered.");
                }
            }
        });
    }

    private void unregisterFitnessDataListener() {
        if(mListener == null) {
            // This code only activates one listener at a time.  If there's no listener, there's
            // nothing to unregister.
            return;
        }

        PendingResult<Status> pendingResult = Fitness.SensorsApi.unregister(
                mClient,
                mListener);

        // Waiting isn't actually necessary as the unregister call will complete regardless,
        // even if called from within onStop, but it can still be added in order to inspect the
        // results.
        pendingResult.setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                if (status.isSuccess()) {
                    Log.i(TAG, "Listener was removed!");
                } else {
                    Log.i(TAG, "Listener was not removed.");
                }
            }
        });
    }

    @Override
    protected void onPause() {
        mClient.disconnect();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_unregister_listener) {
            unregisterFitnessDataListener();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Uses a custom log class that outputs both to in-app targets and logcat.
    public void initializeLogging() {
        // Wraps Android's native log framework.
        LogWrapper logWrapper = new LogWrapper();
        // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
        Log.setLogNode(logWrapper);
        // Filter strips out everything except the message text.
        MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
        logWrapper.setNext(msgFilter);
        // On screen logging via a customized TextView.
        LogView logView = (LogView) findViewById(R.id.sample_logview);
        logView.setTextAppearance(this, R.style.Log);
        logView.setBackgroundColor(Color.WHITE);
        msgFilter.setNext(logView);
        Log.i(TAG, "Ready");
    }
}
