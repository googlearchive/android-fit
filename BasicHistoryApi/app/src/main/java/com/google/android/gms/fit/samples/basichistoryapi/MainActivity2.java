/*
 * Copyright (C) 2016 Google, Inc.
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
package com.google.android.gms.fit.samples.basichistoryapi;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;

import com.google.android.gms.fit.samples.common.logger.Log;
import com.google.android.gms.fit.samples.common.logger.LogView;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.DataUpdateRequest;
import com.google.android.gms.fitness.result.DataReadResult;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * This sample demonstrates how to use the History API of the Google Fit platform
 * to update data in the fitness history.
 */
public class MainActivity2 extends MainActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new UpdateAndVerifyDataTask().execute();
    }

    /**
     *  Create a {@link DataSet}, then make a {@link DataUpdateRequest} to update
     *  step data. Then, create and execute a {@link DataReadRequest} to verify
     *  that the insertion succeeded. By using an {@link AsyncTask}, the app can
     *  schedule synchronous calls, so that it can query for data after confirming
     *  that our insert was successful.
     */
    public class UpdateAndVerifyDataTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            // Create a new dataset and update request.
            DataSet dataSet = updateFitnessData();
            long startTime = 0;
            long endTime = 0;

            // Get the start and end times from the dataset.
            for (DataPoint dataPoint : dataSet.getDataPoints()) {
                startTime = dataPoint.getStartTime(TimeUnit.MILLISECONDS);
                endTime = dataPoint.getEndTime(TimeUnit.MILLISECONDS);
            }

            // [START update_data_request]
            Log.i(TAG, "Updating the dataset in the History API.");


            DataUpdateRequest request = new DataUpdateRequest.Builder()
                    .setDataSet(dataSet)
                    .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                    .build();

            com.google.android.gms.common.api.Status updateStatus =
                    Fitness.HistoryApi.updateData(mClient, request)
                            .await(1, TimeUnit.MINUTES);

            // Before querying the data, check to see if the update succeeded.
            if (!updateStatus .isSuccess()) {
                Log.i(TAG, "There was a problem updating the dataset.");
                return null;
            }

            // At this point the data has been updated and can be read.
            Log.i(TAG, "Data update was successful.");
            // [END update_data_request]

            // Create the query.
            DataReadRequest readRequest = queryFitnessData();

            DataReadResult dataReadResult =
                    Fitness.HistoryApi.readData(mClient, readRequest).await(1, TimeUnit.MINUTES);

            printData(dataReadResult);

            return null;
        }
    }

    /**
     * Create and return a {@link DataSet} of step count data to update.
     */
    private DataSet updateFitnessData() {
        Log.i(TAG, "Creating a new data update request.");

        // [START build_update_data_request]
        // Set a start and end time for the data that fits within the time range
        // of the original insertion.
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        cal.add(Calendar.MINUTE, 0);
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.MINUTE, -50);
        long startTime = cal.getTimeInMillis();

        // Create a data source
        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName(this)
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setStreamName(TAG + " - step count")
                .setType(DataSource.TYPE_RAW)
                .build();

        // Create a data set
        int stepCountDelta = 1000;
        DataSet dataSet = DataSet.create(dataSource);
        // For each data point, specify a start time, end time, and the data value -- in this case,
        // the number of new steps.
        DataPoint dataPoint = dataSet.createDataPoint()
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS);
        dataPoint.getValue(Field.FIELD_STEPS).setInt(stepCountDelta);
        dataSet.add(dataPoint);
        // [END build_update_data_request]

        return dataSet;
    }
}
