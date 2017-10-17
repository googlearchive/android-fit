/*
 * Copyright (C) 2014 Google, Inc.
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
package com.google.android.gms.fit.samples.basicsensorsapi;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.fit.samples.common.logger.Log;
import com.google.android.gms.fit.samples.common.logger.LogView;
import com.google.android.gms.fit.samples.common.logger.LogWrapper;
import com.google.android.gms.fit.samples.common.logger.MessageOnlyLogFilter;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This sample demonstrates how to use the Sensors API of the Google Fit platform to find available
 * data sources and to register/unregister listeners to those sources. It also demonstrates how to
 * authenticate a user with Google Play Services.
 */
public class MainActivity extends AppCompatActivity {

  public static final String TAG = "BasicSensorsApi";

  private static final int REQUEST_OAUTH_REQUEST_CODE = 1;
  private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;

  // [START mListener_variable_reference]
  // Need to hold a reference to this listener, as it's passed into the "unregister"
  // method in order to stop all sensors from sending data to this listener.
  private OnDataPointListener mListener;
  // [END mListener_variable_reference]

  // [START auth_oncreate_setup]
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Put application specific code here.

    setContentView(R.layout.activity_main);
    // This method sets up our custom logger, which will print all log messages to the device
    // screen, as well as to adb logcat.
    initializeLogging();

    // When permissions are revoked the app is restarted so onCreate is sufficient to check for
    // permissions core to the Activity's functionality.
    if (hasRuntimePermissions()) {
      findFitnessDataSourcesWrapper();
    } else {
      requestRuntimePermissions();
    }
  }

  /**
   * A wrapper for {@link #findFitnessDataSources}. If the user account has OAuth permission,
   * continue to {@link #findFitnessDataSources}, else request OAuth permission for the account.
   */
  private void findFitnessDataSourcesWrapper() {
    if (hasOAuthPermission()) {
      findFitnessDataSources();
    } else {
      requestOAuthPermission();
    }
  }

  /** Gets the {@link FitnessOptions} in order to check or request OAuth permission for the user. */
  private FitnessOptions getFitnessSignInOptions() {
    return FitnessOptions.builder().addDataType(DataType.TYPE_LOCATION_SAMPLE).build();
  }

  /** Checks if user's account has OAuth permission to Fitness API. */
  private boolean hasOAuthPermission() {
    FitnessOptions fitnessOptions = getFitnessSignInOptions();
    return GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this), fitnessOptions);
  }

  /** Launches the Google SignIn activity to request OAuth permission for the user. */
  private void requestOAuthPermission() {
    FitnessOptions fitnessOptions = getFitnessSignInOptions();
    GoogleSignIn.requestPermissions(
        this,
        REQUEST_OAUTH_REQUEST_CODE,
        GoogleSignIn.getLastSignedInAccount(this),
        fitnessOptions);
  }

  @Override
  protected void onResume() {
    super.onResume();

    // This ensures that if the user denies the permissions then uses Settings to re-enable
    // them, the app will start working.
    findFitnessDataSourcesWrapper();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (resultCode == Activity.RESULT_OK) {
      if (requestCode == REQUEST_OAUTH_REQUEST_CODE) {
        findFitnessDataSources();
      }
    }
  }
  // [END auth_oncreate_setup]

  /** Finds available data sources and attempts to register on a specific {@link DataType}. */
  private void findFitnessDataSources() {
    // [START find_data_sources]
    // Note: Fitness.SensorsApi.findDataSources() requires the ACCESS_FINE_LOCATION permission.
    Fitness.getSensorsClient(this, GoogleSignIn.getLastSignedInAccount(this))
        .findDataSources(
            new DataSourcesRequest.Builder()
                .setDataTypes(DataType.TYPE_LOCATION_SAMPLE)
                .setDataSourceTypes(DataSource.TYPE_RAW)
                .build())
        .addOnSuccessListener(
            new OnSuccessListener<List<DataSource>>() {
              @Override
              public void onSuccess(List<DataSource> dataSources) {
                for (DataSource dataSource : dataSources) {
                  Log.i(TAG, "Data source found: " + dataSource.toString());
                  Log.i(TAG, "Data Source type: " + dataSource.getDataType().getName());

                  // Let's register a listener to receive Activity data!
                  if (dataSource.getDataType().equals(DataType.TYPE_LOCATION_SAMPLE)
                      && mListener == null) {
                    Log.i(TAG, "Data source for LOCATION_SAMPLE found!  Registering.");
                    registerFitnessDataListener(dataSource, DataType.TYPE_LOCATION_SAMPLE);
                  }
                }
              }
            })
        .addOnFailureListener(
            new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                Log.e(TAG, "failed", e);
              }
            });
    // [END find_data_sources]
  }

  /**
   * Registers a listener with the Sensors API for the provided {@link DataSource} and {@link
   * DataType} combo.
   */
  private void registerFitnessDataListener(DataSource dataSource, DataType dataType) {
    // [START register_data_listener]
    mListener =
        new OnDataPointListener() {
          @Override
          public void onDataPoint(DataPoint dataPoint) {
            for (Field field : dataPoint.getDataType().getFields()) {
              Value val = dataPoint.getValue(field);
              Log.i(TAG, "Detected DataPoint field: " + field.getName());
              Log.i(TAG, "Detected DataPoint value: " + val);
            }
          }
        };

    Fitness.getSensorsClient(this, GoogleSignIn.getLastSignedInAccount(this))
        .add(
            new SensorRequest.Builder()
                .setDataSource(dataSource) // Optional but recommended for custom data sets.
                .setDataType(dataType) // Can't be omitted.
                .setSamplingRate(10, TimeUnit.SECONDS)
                .build(),
            mListener)
        .addOnCompleteListener(
            new OnCompleteListener<Void>() {
              @Override
              public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                  Log.i(TAG, "Listener registered!");
                } else {
                  Log.e(TAG, "Listener not registered.", task.getException());
                }
              }
            });
    // [END register_data_listener]
  }

  /** Unregisters the listener with the Sensors API. */
  private void unregisterFitnessDataListener() {
    if (mListener == null) {
      // This code only activates one listener at a time.  If there's no listener, there's
      // nothing to unregister.
      return;
    }

    // [START unregister_data_listener]
    // Waiting isn't actually necessary as the unregister call will complete regardless,
    // even if called from within onStop, but a callback can still be added in order to
    // inspect the results.
    Fitness.getSensorsClient(this, GoogleSignIn.getLastSignedInAccount(this))
        .remove(mListener)
        .addOnCompleteListener(
            new OnCompleteListener<Boolean>() {
              @Override
              public void onComplete(@NonNull Task<Boolean> task) {
                if (task.isSuccessful() && task.getResult()) {
                  Log.i(TAG, "Listener was removed!");
                } else {
                  Log.i(TAG, "Listener was not removed.");
                }
              }
            });
    // [END unregister_data_listener]
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

  /** Initializes a custom log class that outputs both to in-app targets and logcat. */
  private void initializeLogging() {
    // Wraps Android's native log framework.
    LogWrapper logWrapper = new LogWrapper();
    // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
    Log.setLogNode(logWrapper);
    // Filter strips out everything except the message text.
    MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
    logWrapper.setNext(msgFilter);
    // On screen logging via a customized TextView.
    LogView logView = (LogView) findViewById(R.id.sample_logview);

    // Fixing this lint errors adds logic without benefit.
    // noinspection AndroidLintDeprecation
    logView.setTextAppearance(R.style.Log);

    logView.setBackgroundColor(Color.WHITE);
    msgFilter.setNext(logView);
    Log.i(TAG, "Ready");
  }

  /** Returns the current state of the permissions needed. */
  private boolean hasRuntimePermissions() {
    int permissionState =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
    return permissionState == PackageManager.PERMISSION_GRANTED;
  }

  private void requestRuntimePermissions() {
    boolean shouldProvideRationale =
        ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.ACCESS_FINE_LOCATION);

    // Provide an additional rationale to the user. This would happen if the user denied the
    // request previously, but didn't check the "Don't ask again" checkbox.
    if (shouldProvideRationale) {
      Log.i(TAG, "Displaying permission rationale to provide additional context.");
      Snackbar.make(
          findViewById(R.id.main_activity_view),
          R.string.permission_rationale,
          Snackbar.LENGTH_INDEFINITE)
          .setAction(
              R.string.ok,
              new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                  // Request permission
                  ActivityCompat.requestPermissions(
                      MainActivity.this,
                      new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                      REQUEST_PERMISSIONS_REQUEST_CODE);
                }
              })
          .show();
    } else {
      Log.i(TAG, "Requesting permission");
      // Request permission. It's possible this can be auto answered if device policy
      // sets the permission in a given state or the user denied the permission
      // previously and checked "Never ask again".
      ActivityCompat.requestPermissions(
          MainActivity.this,
          new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
          REQUEST_PERMISSIONS_REQUEST_CODE);
    }
  }

  /** Callback received when a permissions request has been completed. */
  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Log.i(TAG, "onRequestPermissionResult");
    if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
      if (grantResults.length <= 0) {
        // If user interaction was interrupted, the permission request is cancelled and you
        // receive empty arrays.
        Log.i(TAG, "User interaction was cancelled.");
      } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        // Permission was granted.
        findFitnessDataSourcesWrapper();
      } else {
        // Permission denied.

        // In this Activity we've chosen to notify the user that they
        // have rejected a core permission for the app since it makes the Activity useless.
        // We're communicating this message in a Snackbar since this is a sample app, but
        // core permissions would typically be best requested during a welcome-screen flow.

        // Additionally, it is important to remember that a permission might have been
        // rejected without asking the user for permission (device policy or "Never ask
        // again" prompts). Therefore, a user interface affordance is typically implemented
        // when permissions are denied. Otherwise, your app could appear unresponsive to
        // touches or interactions which have required permissions.
        Snackbar.make(
            findViewById(R.id.main_activity_view),
            R.string.permission_denied_explanation,
            Snackbar.LENGTH_INDEFINITE)
            .setAction(
                R.string.settings,
                new View.OnClickListener() {
                  @Override
                  public void onClick(View view) {
                    // Build intent that displays the App settings screen.
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null);
                    intent.setData(uri);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                  }
                })
            .show();
      }
    }
  }
}
