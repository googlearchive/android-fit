Step Counter
============

A simple example of how to record steps and read them back.

Introduction
------------

Pre-requisites
--------------

- Android API Level > 9
- Android Build Tools v23
- Android Support Repository
- Register a Google Project with an Android client per getting started instructions
  http://developers.google.com/fit/android/get-started

Getting Started
---------------
This sample uses the Gradle build system. To build this project, use the
"gradlew build" command or use "Import Project" in Android Studio.

NOTE: You must register an Android client underneath a Google Project in order for the Google Fit
API to become available for your app. The process ensures your app has proper consent screen
information for users to accept, among other things required to access Google APIs.
See the instructions for more details: http://developers.google.com/fit/android/get-started

Support
-------

The most common problem using these samples is a SIGN_IN_FAILED exception. Users can experience
this after selecting a Google Account to connect to the FIT API. If you see the following in
logcat output then make sure to register your Android app underneath a Google Project as outlined
in the instructions for using this sample at: http://developers.google.com/fit/android/get-started

`10-26 14:40:37.082 1858-2370/? E/MDM: [138] b.run: Couldn't connect to Google API client: ConnectionResult{statusCode=API_UNAVAILABLE, resolution=null, message=null}`

Use the following channels for support:

- Google+ Community: https://plus.google.com/communities/103314459667402704958
- Stack Overflow: http://stackoverflow.com/questions/tagged/android

If you've found an error in this sample, please file an issue:
https://github.com/googlesamples/android-fitness/issues

Patches are encouraged, and may be submitted according to the instructions in CONTRIB.md.

License
-------

Copyright 2016 Google, Inc.

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.
