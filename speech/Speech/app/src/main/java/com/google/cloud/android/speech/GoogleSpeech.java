package com.google.cloud.android.speech;

import static android.content.Context.BIND_AUTO_CREATE;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.google.cloud.speech.v2.StreamingRecognizeResponse;

import io.grpc.stub.StreamObserver;

public class GoogleSpeech {

    private final SimpleVoiceRecorder voiceRecorder;
    private volatile SpeechService speechService;

    public interface Connected {
        void onConnected();
    }

    GoogleSpeech(Context context, Connected callback) {
        SimpleVoiceRecorder.Callback voiceCallback = new SimpleVoiceRecorder.Callback() {
            @Override
            public void onVoice(byte[] data, int size) {
                if (speechService != null) {
                    speechService.recognize(data, size);
                }
            }
        };
        ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder binder) {
                speechService = SpeechService.from(binder);
                speechService.addListener(callback::onConnected);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                speechService = null;
            }
        };

        context.bindService(new Intent(context, SpeechService.class), serviceConnection, BIND_AUTO_CREATE);
        voiceRecorder = new SimpleVoiceRecorder(voiceCallback);
    }

    public void startRecognizing(StreamObserver<StreamingRecognizeResponse> observer) {
        if (speechService == null) {
            throw new SpeechService.NotConnectedException();
        }
        voiceRecorder.start();
        speechService.startRecognizing(voiceRecorder.getSampleRate(), observer);
    }

    public void stopRecognizing() {
        voiceRecorder.stop();
        speechService.finishRecognizing();
    }
}
