/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gms.fit.samples.basicrecordingapi;

import android.app.Activity;
import android.content.Intent;
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
import com.google.android.gms.fitness.DataType;
import com.google.android.gms.fitness.DataTypes;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessScopes;
import com.google.android.gms.fitness.ListSubscriptionsResult;
import com.google.android.gms.fitness.Subscription;


public class MainActivity extends Activity {
    public static final String TAG = "BasicRecordingApi";
    private static final int REQUEST_OAUTH = 1;

    // Tracks whether an authorization activity is stacking over the current activity, i.e. when
    // a known auth error is being resolved, such as showing the account chooser or presenting a
    // consent dialog. This avoids common duplications as might happen on screen rotations, etc.
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean authInProgress = false;

    private GoogleApiClient mClient = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // This method sets up our custom logger, which will print all log messages to the device
        // screen, as well as to adb logcat.
        initializeLogging();

        if (savedInstanceState != null) {
            authInProgress = savedInstanceState.getBoolean(AUTH_PENDING);
        }

        buildFitnessClient();
    }

    private void buildFitnessClient() {
        // Create the Google API Client
        mClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.API)
                .addScope(FitnessScopes.SCOPE_ACTIVITY_READ)
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {

                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Connected!!!");
                                // Now you can make calls to the Fitness APIs.  What to do?
                                // Subscribe to some data sources!
                                subscribeIfNotAlreadySubscribed();
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
                                if (!authInProgress) {
                                    try {
                                        Log.i(TAG, "Attempting to resolve failed connection");
                                        authInProgress = true;
                                        result.startResolutionForResult(MainActivity.this,
                                                REQUEST_OAUTH);
                                    } catch (IntentSender.SendIntentException e) {
                                        Log.e(TAG,
                                                "Exception while starting resolution activity", e);
                                    }
                                }
                            }
                        }
                )
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Connect to the Fitness API
        Log.i(TAG, "Connecting...");
        mClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mClient.isConnected()) {
            mClient.disconnect();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OAUTH) {
            authInProgress = false;
            if (resultCode == RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!mClient.isConnecting() && !mClient.isConnected()) {
                    mClient.connect();
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(AUTH_PENDING, authInProgress);
    }

    // Subscriptions can exist across application instances (so data is recorded even after the
    // application closes down).  Before creating a new subscription, verify it doesn't already
    // exist from a previous invocation of this app.  If the subscription already exists,
    // just bail out of the method.  Because this a pending result that depends on the result
    // of another pending result, the easiest thing to do is move it all off the UI thread
    // so the results can be synchronous.
    public void subscribeIfNotAlreadySubscribed() {
        new Thread() {
            public void run() {
                // Get a list of current subscriptions and iterate over it.
                // Since this code isn't running on the UI thread, it's safe to await() for the
                // result instead of creating callbacks.
                ListSubscriptionsResult subResults = getSubscriptionsList().await();
                boolean activitySubActive = false;
                for (Subscription sc : subResults.getSubscriptions()) {
                    if (sc.getDataType().equals(DataTypes.ACTIVITY_SAMPLE)) {
                        activitySubActive = true;
                        break;
                    }
                }

                if(activitySubActive) {
                    Log.i(TAG, "Existing subscription for activity detection detected.");
                    return;
                }

                // At this point in the code, the desired subscription doesn't exist.

                // Invoke the Recording API.  As soon as the subscription is active,
                // fitness data will start recording.
                Status status =
                        Fitness.RecordingApi.subscribe(mClient, DataTypes.ACTIVITY_SAMPLE).await();
                if (status.isSuccess()) {
                    Log.i(TAG, "Successfully subscribed!");
                } else {
                    Log.i(TAG, "There was a problem subscribing.");
                }
            }
        }.start();
    }

    // Since there are multiple things you can do with a list of subscriptions (dump to log, mine
    // for data types, unsubscribe from everything) it's easiest to abstract out the part that
    // wants the list, and leave it to the calling method to decide what to do with the result.
    private PendingResult<ListSubscriptionsResult> getSubscriptionsList() {
        // Invoke a Subscriptions list request with the Recording API
        return Fitness.RecordingApi.listSubscriptions(mClient, DataTypes.ACTIVITY_SAMPLE);
    }

    public void dumpSubscriptionsList() {
        // Create the callback to retrieve the list of subscriptions asynchronously.
        getSubscriptionsList().setResultCallback(new ResultCallback<ListSubscriptionsResult>() {
            @Override
            public void onResult(ListSubscriptionsResult listSubscriptionsResult) {
                for (Subscription sc : listSubscriptionsResult.getSubscriptions()) {
                    DataType dt = sc.getDataType();
                    Log.i(TAG, "Active subscription for data type: " + dt.getName());
                }
            }
        });
    }

    // Cancel all fitness API subscriptions.
    public void cancelAllSubscriptions() {
        getSubscriptionsList().setResultCallback(new ResultCallback<ListSubscriptionsResult>() {
            @Override
            public void onResult(ListSubscriptionsResult listSubscriptionsResult) {
                for (Subscription sc : listSubscriptionsResult.getSubscriptions()) {
                    cancelSubscription(sc);
                }
            }
        });
    }

    public void cancelSubscription(Subscription sc) {
        final String dataTypeStr = sc.getDataType().toString();
        Log.i(TAG, "Unsubscribing from data type: " + dataTypeStr);

        // Invoke the Recording API to unsubscribe from the data type and
        // retrieve the result of the request synchronously
        Fitness.RecordingApi.unsubscribe(mClient, DataTypes.ACTIVITY_SAMPLE)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Successfully unsubscribed for data type: " + dataTypeStr);
                        } else {
                            // Subscription not removed
                            Log.i(TAG, "Failed to unsubscribe for data type: " + dataTypeStr);
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
        if (id == R.id.action_cancel_subs) {
            cancelAllSubscriptions();
            return true;
        } else if (id == R.id.action_dump_subs) {
            dumpSubscriptionsList();
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
