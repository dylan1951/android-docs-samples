package com.google.cloud.android.speech;

import static android.content.Context.BIND_AUTO_CREATE;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.auth.oauth2.AccessToken;
import com.google.cloud.speech.v2.CreateRecognizerRequest;
import com.google.cloud.speech.v2.ExplicitDecodingConfig;
import com.google.cloud.speech.v2.ListRecognizersRequest;
import com.google.cloud.speech.v2.ListRecognizersResponse;
import com.google.cloud.speech.v2.RecognitionConfig;
import com.google.cloud.speech.v2.RecognitionFeatures;
import com.google.cloud.speech.v2.Recognizer;
import com.google.cloud.speech.v2.SpeechAdaptation;
import com.google.cloud.speech.v2.StreamingRecognitionConfig;
import com.google.cloud.speech.v2.StreamingRecognitionFeatures;
import com.google.cloud.speech.v2.StreamingRecognizeRequest;
import com.google.cloud.speech.v2.StreamingRecognizeResponse;
import com.google.longrunning.Operation;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.grpc.stub.StreamObserver;

public class CloudSpeech {
    private final VoiceRecorder voiceRecorder;
    private volatile SpeechService speechService;
    private static final String parent = "projects/driverinsight-384904/locations/global";
    private StreamObserver<StreamingRecognizeRequest> mRequestObserver;

    public interface Connected {
        void onReady();
    }

    public interface Authorize {
        AccessToken refreshAccessToken() throws IOException;
    }

    CloudSpeech(Context context, Authorize authorize, Connected callback) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
            throw new SecurityException("RECORD_AUDIO permission not granted");
        }

        SpeechService.setAuthorize(authorize);

        VoiceRecorder.Callback voiceCallback = new VoiceRecorder.Callback() {
            @Override
            public void onVoice(byte[] data, int size) {
            if (speechService != null) {
                recognize(data, size);
            }
            }
        };

        ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder binder) {
                speechService = SpeechService.from(binder);
                speechService.registerListener(callback::onReady);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                speechService = null;
            }
        };

        context.bindService(new Intent(context, SpeechService.class), serviceConnection, BIND_AUTO_CREATE);
        voiceRecorder = new VoiceRecorder(voiceCallback);
    }

    public void startRecognizing(int sampleRate, SpeechAdaptation speechAdaptation, RecognitionFeatures recognitionFeatures, StreamingRecognitionFeatures streamingRecognitionFeatures, StreamObserver<StreamingRecognizeResponse> responseObserver) throws SpeechService.NotConnectedException {

        Log.d("Banana", "startRecognizing()");

        if (speechService.mApi == null) {
            throw new SpeechService.NotConnectedException();
        }

        List<Recognizer> recognizers = getRecognizers().join();

        Log.d("banana", "there are " + recognizers.size() + " recognizers");

        if (recognizers.size() < 1) {
            return;
        }

        // Configure the API
        mRequestObserver = speechService.mApi.streamingRecognize(responseObserver);
        mRequestObserver.onNext(StreamingRecognizeRequest.newBuilder()
                .setStreamingConfig(StreamingRecognitionConfig.newBuilder()
                        .setStreamingFeatures(streamingRecognitionFeatures)
                        .setConfig(RecognitionConfig.newBuilder()
                                .setAdaptation(speechAdaptation)
                                .setFeatures(recognitionFeatures)
                                .setExplicitDecodingConfig(ExplicitDecodingConfig.newBuilder()
                                        .setEncoding(ExplicitDecodingConfig.AudioEncoding.LINEAR16)
                                        .setSampleRateHertz(sampleRate)
                                        .setAudioChannelCount(1)
                                        .build())
                                .build())
                        .build())
                .setRecognizer(recognizers.get(0).getName())
                .build());

        Log.d("banana", "sent config request");
    }

    public void startRecognizing(StreamObserver<StreamingRecognizeResponse> observer) {
        if (speechService == null) {
            throw new SpeechService.NotConnectedException();
        }

        voiceRecorder.start();

        SpeechAdaptation speechAdaptation = SpeechAdaptation.newBuilder()
                .build();

        RecognitionFeatures recognitionFeatures = RecognitionFeatures.newBuilder()
                .build();

        StreamingRecognitionFeatures streamingRecognitionFeatures = StreamingRecognitionFeatures.newBuilder()
                .build();

        startRecognizing(voiceRecorder.getSampleRate(), speechAdaptation, recognitionFeatures, streamingRecognitionFeatures, observer);
    }

    public void recognize(byte[] data, int size) {
        if (mRequestObserver == null) {
            return;
        }

        // Call the streaming recognition API
        mRequestObserver.onNext(StreamingRecognizeRequest.newBuilder()
                .setAudio(ByteString.copyFrom(data, 0, size))
                .build());
    }

    public void stopRecognizing() {
        voiceRecorder.stop();
        if (mRequestObserver == null) {
            return;
        }
        mRequestObserver.onCompleted();
        mRequestObserver = null;
    }

    public CompletableFuture<List<Recognizer>> getRecognizers() {
        CompletableFuture<List<Recognizer>> future = new CompletableFuture<>();

        ListRecognizersRequest request = ListRecognizersRequest.newBuilder().setParent(parent).build();

        speechService.mApi.listRecognizers(request, new StreamObserver<ListRecognizersResponse>() {
            @Override
            public void onNext(ListRecognizersResponse value) {
                future.complete(value.getRecognizersList());
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {}
        });

        return future;
    }

    public CompletableFuture<Operation> createRecognizer(Recognizer recognizer, final String id) {
        CompletableFuture<Operation> future = new CompletableFuture<>();

        CreateRecognizerRequest request = CreateRecognizerRequest.newBuilder()
                .setParent(parent)
                .setRecognizerId(id)
                .setRecognizer(recognizer)
                .build();

        speechService.mApi.createRecognizer(request, new StreamObserver<Operation>() {
            @Override
            public void onNext(Operation value) {
                future.complete(value);
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {}
        });

        return future;
    }
}
