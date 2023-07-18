/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.android.speech;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v2.SpeechRecognitionAlternative;
import com.google.cloud.speech.v2.StreamingRecognitionResult;
import com.google.cloud.speech.v2.StreamingRecognizeResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import io.grpc.stub.StreamObserver;


public class MainActivity extends Activity {
    private CloudSpeech speech;
    private TextToSpeech tts;

    @Override
    protected void onStart() {
        super.onStart();

        speech = new CloudSpeech(this, new CloudSpeech.Authorize() {
            final List<String> SCOPE = Collections.singletonList("https://www.googleapis.com/auth/cloud-platform");
            @Override
            // ***** WARNING *****
            // In this sample, we load the credential from a JSON file stored in a raw resource
            // folder of this client app. You should never do this in your app. Instead, store
            // the file in your server and obtain an access token from there.
            // *******************
            public AccessToken refreshAccessToken() throws IOException {
                final InputStream stream = getResources().openRawResource(R.raw.credential);
                final GoogleCredentials credentials = GoogleCredentials.fromStream(stream).createScoped(SCOPE);
                return credentials.refreshAccessToken();
            }
        }, new CloudSpeech.Connected() {
            @Override
            public void onConnected() {
                new Thread(() -> getNumberPlate()).start();
            }
        });
    }

    private CompletableFuture<Void> speak(String message) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        tts = new TextToSpeech(this, i -> {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String s) {}

                @Override
                public void onDone(String s) {
                    future.complete(null);
                }

                @Override
                public void onError(String s) {
                    future.complete(null);
                }
            });

            if (i == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                Bundle params = new Bundle();
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utterance_id");
                tts.speak(message, TextToSpeech.QUEUE_ADD, params, "utterance_id");
            }
        });

        return future;
    }


    private void getNumberPlate() {

        speak("What is the registration of the vehicle?").join();

        speech.startRecognizing(new StreamObserver<StreamingRecognizeResponse>() {
            @Override
            public void onNext(StreamingRecognizeResponse value) {
                Log.d("banana", "received response");
                for (int i = 0; i < value.getResultsCount(); i++) {
                    StreamingRecognitionResult result = value.getResults(i);
                    if (result.getIsFinal()) {
                        for (int j = 0; j < result.getAlternativesCount(); j++) {
                            SpeechRecognitionAlternative alternative = result.getAlternatives(j);
                            String numberPlate = alternative.getTranscript();
                            Log.d("banana", numberPlate);
                        }
                        speech.stopRecognizing();
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                Log.d("banana", t.toString());
            }

            @Override
            public void onCompleted() {
                System.out.println("banana: COMPLETED");
            }
        });
    }
}
