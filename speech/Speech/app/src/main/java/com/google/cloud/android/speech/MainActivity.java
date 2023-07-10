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

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.cloud.speech.v2.SpeechRecognitionAlternative;
import com.google.cloud.speech.v2.StreamingRecognitionResult;
import com.google.cloud.speech.v2.StreamingRecognizeResponse;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import io.grpc.stub.StreamObserver;


public class MainActivity extends AppCompatActivity implements MessageDialogFragment.Listener {
    @Override
    public void onMessageDialogDismissed() {

    }

    private GoogleSpeech speech;
    private TextToSpeech tts;

    @Override
    protected void onStart() {
        super.onStart();

        speech = new GoogleSpeech(getApplicationContext(), new GoogleSpeech.Connected() {
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
