package com.google.android.gms.fit.samples.basichistorysessions;

import android.app.Activity;
import android.content.IntentSender;
import android.graphics.Color;
import android.os.AsyncTask;
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
import com.google.android.gms.fitness.DataDeleteRequest;
import com.google.android.gms.fitness.DataPoint;
import com.google.android.gms.fitness.DataSet;
import com.google.android.gms.fitness.DataSource;
import com.google.android.gms.fitness.DataType;
import com.google.android.gms.fitness.DataTypes;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessScopes;
import com.google.android.gms.fitness.Session;
import com.google.android.gms.fitness.SessionInsertRequest;
import com.google.android.gms.fitness.SessionReadRequest;
import com.google.android.gms.fitness.SessionReadResult;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class MainActivity extends Activity {
    public static final String TAG = "BasicSessions";
    public static final String SAMPLE_SESSION_NAME = "Afternoon run";
    private static final int REQUEST_OAUTH = 1;
    private GoogleApiClient mClient = null;

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
                .addScope(FitnessScopes.SCOPE_ACTIVITY_READ_WRITE)
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {
                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Connected!!!");
                                // Now you can make calls to the Fitness APIs.  What to do?
                                // Play with some sessions!!
                                new InsertAndVerifySessionTask().execute();
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

    // By using an AsyncTask to make our calls, we can schedule synchronous calls, so that we can
    // query for sessions after confirming that our insert was successful. Using asynchronous calls
    // and callbacks would not guarantee that the insertion had concluded before the read request
    // was made. An example of an asynchronous call using a callback can be found in the example
    // on deleting sessions below.
    private class InsertAndVerifySessionTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            //First, create a new session and an insertion request.
            SessionInsertRequest insertRequest = insertFitnessSession();
            // Then, invoke the History API to insert the session.
            PendingResult<com.google.android.gms.common.api.Status> pendingInsertResult =
                    Fitness.HistoryApi.insertSession(mClient, insertRequest);
            // After insertion, await the result, which is possible here because of the AsyncTask.
            com.google.android.gms.common.api.Status insertStatus = pendingInsertResult.await();
            // Before querying the session, check to see if the insertion succeeded.
            if (!insertStatus.isSuccess()) {
                Log.i(TAG, "There was a problem inserting the session.");
                return null;
            }
            // At this point, the session has been inserted and can be read.
            Log.i(TAG, "Session insert was successful!");

            // Begin by creating the query.
            SessionReadRequest readRequest = readFitnessSession();
            // Invoke the History API to fetch the session with the query.
            PendingResult<SessionReadResult> pendingReadResult =
                    Fitness.HistoryApi.readSession(mClient, readRequest);
            // Await the result of the read request.
            SessionReadResult sessionReadResult = pendingReadResult.await();
            // Get a list of the sessions that match the criteria to check the result.
            Log.i(TAG, "Session read was successful. Number of returned sessions is: "
                    + sessionReadResult.getSessions().size());
            for (Session session : sessionReadResult.getSessions()) {
                // Process the session
                dumpSession(session);

                // Process the data sets for this session
                List<DataSet> dataSets = sessionReadResult.getDataSet(session);
                for (DataSet dataSet : dataSets) {
                    dumpDataSet(dataSet);
                }
            }
            return null;
        }
    }

    public SessionInsertRequest insertFitnessSession() {
        Log.i(TAG, "Creating a new session for an afternoon run");
        // Setting a start and end time for our run.
        long HOUR_IN_MS = 1000 * 60 * 60;
        Date now = new Date();
        // Set a range of the run, using a start time of 1 hour before this moment.
        long endTime = now.getTime();
        long startTime = endTime - (HOUR_IN_MS);

        // Create a data source
        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName(this.getPackageName())
                .setDataType(DataTypes.STEP_COUNT_CUMULATIVE)
                .setName(SAMPLE_SESSION_NAME + "- step count")
                .setType(DataSource.TYPE_RAW)
                .build();

        int stepCountValue = 10000;
        // Create a data set to include in the session.
        DataSet dataSet = DataSet.create(dataSource);
        // for each data point (startTime, endTime, stepCountValue):
        dataSet.add(
                dataSet.createDataPoint()
                        .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                        .setIntValues(stepCountValue)
        );

        // Create a session with metadata about the activity.
        Session session = new Session.Builder()
                .setName(SAMPLE_SESSION_NAME)
                .setDescription("Long run around Shoreline Park")
                .setIdentifier("UniqueIdentifierHere")
                .setStartTimeMillis(startTime)
                .setEndTimeMillis(endTime)
                .build();

        Log.i(TAG, "Inserting the session in the History API");
        // Build a session insert request
        return new SessionInsertRequest.Builder()
                .setSession(session)
                .addDataSet(dataSet)
                .build();
    }

    public SessionReadRequest readFitnessSession() {
        Log.i(TAG, "Reading History API results for session: " + SAMPLE_SESSION_NAME);
        // Setting a start and end time for our run.
        long WEEK_IN_MS = 1000 * 60 * 60 * 24 * 7;
        Date now = new Date();
        // Set a range of the week, using a start time of 1 week before this moment.
        long endTime = now.getTime();
        long startTime = endTime - (WEEK_IN_MS);

        // Build a session read request
        return new SessionReadRequest.Builder()
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                .addDefaultDataSource(DataTypes.STEP_COUNT_CUMULATIVE)
                .setSessionName(SAMPLE_SESSION_NAME)
                .build();
    }

    public void dumpDataSet(DataSet dataSet) {
        Log.i(TAG, "Data returned for Data type: " + dataSet.getDataType().getName());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
        for (DataPoint dp : dataSet.getDataPoints()) {
            // Get start time of data point, convert from nanos to millis for date parsing,
            // since nanoseconds aren't helpful for measuring step count..
            long dpStart = dp.getStartTimeNanos() / 1000000;
            long dpEnd = dp.getEndTimeNanos() / 1000000;
            dateFormat.format(dpStart);
            Log.i(TAG, "Data point:");
            Log.i(TAG, "\tType: " + dp.getDataType().getName());
            Log.i(TAG, "\tStart: " + dateFormat.format(dpStart));
            Log.i(TAG, "\tEnd: " + dateFormat.format(dpEnd));
            for(DataType.Field field : dp.getDataType().getFields()) {
                Log.i(TAG, "\tField: " + field.getName() +
                        " Value: " + dp.getValue(field));
            }
        }
    }

    public void dumpSession(Session session) {
        Log.i(TAG, "Data returned for Session: " + session.getName());
        Log.i(TAG, "\tDescription: " + session.getDescription());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
        Log.i(TAG, "\tStart: " + dateFormat.format(session.getStartTimeMillis()));
        Log.i(TAG, "\tEnd: " + dateFormat.format(session.getEndTimeMillis()));
    }

    public void deleteSession() {
        Log.i(TAG, "Deleting today's sessions");

        // 1. Create a delete request object
        // (provide a data type and a time interval)
        long DAY_IN_MS = 1000 * 60 * 60 * 24;
        Date now = new Date();
        // Set a range of the day, using a start time of 1 day before this moment.
        long endTime = now.getTime();
        long startTime = endTime - (DAY_IN_MS);
        DataDeleteRequest request = new DataDeleteRequest.Builder()
            .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
            .addDataType(DataTypes.STEP_COUNT_CUMULATIVE)
            .deleteAllSessions() // Or specify a particular session here
            .build();

        // 2. Invoke the History API with:
        // - The Google API client object
        // - The delete request
        PendingResult<Status> pendingResult =
            Fitness.HistoryApi.delete(mClient, request);

        // 3. Check the result
        pendingResult.setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                if (status.isSuccess()) {
                    Log.i(TAG, "Successfully deleted today's sessions");
                } else {
                    Log.i(TAG, "Failed to delete today's sessions");
                }
            }
        });
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
        if (id == R.id.action_delete_session) {
            deleteSession();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Using a custom log class that outputs both to in-app targets and logcat.
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
