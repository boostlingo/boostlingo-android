package com.boostlingo.android.quickstart;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.boostlingo.android.BLCall;
import com.boostlingo.android.BLCallStateListener;
import com.boostlingo.android.BLChatListener;
import com.boostlingo.android.BLLogLevel;
import com.boostlingo.android.BLVideoCall;
import com.boostlingo.android.BLVideoListener;
import com.boostlingo.android.Boostlingo;
import com.boostlingo.android.CallRequest;
import com.boostlingo.android.ChatMessage;
import com.twilio.video.VideoView;

import io.reactivex.CompletableObserver;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

import static com.boostlingo.android.quickstart.MainActivity.BOOSTLINGO_AUTH_TOKEN_EXTRA;
import static com.boostlingo.android.quickstart.MainActivity.BOOSTLINGO_REGION_EXTRA;
import static com.boostlingo.android.quickstart.MainActivity.CALL_ID_EXTRA;
import static com.boostlingo.android.quickstart.MainActivity.CALL_ID_RESULT_CODE;
import static com.boostlingo.android.quickstart.MainActivity.CALL_REQUEST_EXTRA;
import static com.boostlingo.android.quickstart.MainActivity.SNACKBAR_DURATION;

public class VideoCallActivity extends AppCompatActivity implements BLCallStateListener, BLVideoListener, BLChatListener {

    private enum State {
        NO_CALL,
        CALLING,
        IN_PROGRESS
    }

    private static final int CAMERA_MIC_PERMISSION_REQUEST_CODE = 1;

    private View clRoot;
    private VideoView primaryVideoView;
    private VideoView thumbnailVideoView;
    private FloatingActionButton switchCameraActionFab;
    private FloatingActionButton localVideoActionFab;
    private FloatingActionButton muteActionFab;
    private FloatingActionButton hangupActionFab;
    private TextView videoStatusTextView;
    private FloatingActionButton chatActionFab;

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private boolean isSpeakerPhoneEnabled = true;
    private Boostlingo boostlingo;
    private CallRequest callRequest;
    private int previousAudioMode;
    private boolean previousMicrophoneMute;
    private BLVideoCall currentCall;
    private AudioManager audioManager;
    private int lastCallId;

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == CAMERA_MIC_PERMISSION_REQUEST_CODE) {
            boolean cameraAndMicPermissionGranted = true;

            for (int grantResult : grantResults) {
                cameraAndMicPermissionGranted &= grantResult == PackageManager.PERMISSION_GRANTED;
            }

            if (cameraAndMicPermissionGranted) {
                connectToRoom();
            } else {
                Toast.makeText(this,
                        R.string.permissions_needed,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        updateUI(State.NO_CALL);
        boostlingo.hangUp().subscribe(new CompletableObserver() {
            @Override
            public void onSubscribe(Disposable d) {
                compositeDisposable.add(d);
            }

            @Override
            public void onComplete() {
                runOnUiThread(() -> updateUI(State.NO_CALL));
            }

            @Override
            public void onError(Throwable e) {
                runOnUiThread(() -> {
                    updateUI(State.NO_CALL);
                    Snackbar.make(clRoot,
                            e != null ? "Error: " + e.getLocalizedMessage() : "Error",
                            SNACKBAR_DURATION).show();
                });
            }
        });
        Intent resultIntent = new Intent();
        resultIntent.putExtra(CALL_ID_EXTRA, lastCallId);
        setResult(CALL_ID_RESULT_CODE, resultIntent);
        super.onBackPressed();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);

        if (getIntent().hasExtra(CALL_REQUEST_EXTRA)) {
            callRequest = getIntent().getParcelableExtra(CALL_REQUEST_EXTRA);
            String region = getIntent().getStringExtra(BOOSTLINGO_REGION_EXTRA);
            String token = getIntent().getStringExtra(BOOSTLINGO_AUTH_TOKEN_EXTRA);
            boostlingo = new Boostlingo(this, token, region, BLLogLevel.DEBUG);
        }

        clRoot = findViewById(R.id.cl_root);
        primaryVideoView = findViewById(R.id.primary_video_view);
        thumbnailVideoView = findViewById(R.id.thumbnail_video_view);
        videoStatusTextView = findViewById(R.id.video_status_textview);
        
        switchCameraActionFab = findViewById(R.id.switch_camera_action_fab);
        switchCameraActionFab.setOnClickListener(v -> {
            if (currentCall != null) {
                currentCall.switchCameraSource();
            }
        });

        localVideoActionFab = findViewById(R.id.local_video_action_fab);
        localVideoActionFab.setOnClickListener(v -> {
            if (currentCall != null) {
                currentCall.setVideoEnabled(!currentCall.isVideoEnabled());
            }
            // TODO: Show you privacy screen here
            // Get the requestor profile URL
            // boostlingo.getProfile()...imageInfo.url(null);
        });

        muteActionFab = findViewById(R.id.mute_action_fab);
        muteActionFab.setOnClickListener(v -> {
            if (currentCall != null) {
                currentCall.setMuted(!currentCall.isMuted());
            }
        });

        chatActionFab = findViewById(R.id.chat_action_fab);
        chatActionFab.setOnClickListener(v -> {
            boostlingo.sendChatMessage("Test").subscribe(new SingleObserver<ChatMessage>() {
                @Override
                public void onSubscribe(Disposable d) {
                    compositeDisposable.add(d);
                }

                @Override
                public void onSuccess(ChatMessage message) {
                    runOnUiThread(() -> {
                        Snackbar.make(clRoot, "Success: Message sent",
                                SNACKBAR_DURATION).show();
                    });
                }

                @Override
                public void onError(Throwable e) {
                    runOnUiThread(() -> {
                        Snackbar.make(clRoot,
                                e != null ? "Error: " + e.getLocalizedMessage() : "Error",
                                SNACKBAR_DURATION).show();
                    });
                }
            });
        });

        hangupActionFab = findViewById(R.id.hangup_action_fab);
        hangupActionFab.setOnClickListener(v -> {
            updateUI(State.NO_CALL);
            boostlingo.hangUp().subscribe(new CompletableObserver() {
                @Override
                public void onSubscribe(Disposable d) {
                    compositeDisposable.add(d);
                }

                @Override
                public void onComplete() {
                    runOnUiThread(() -> updateUI(State.NO_CALL));
                }

                @Override
                public void onError(Throwable e) {
                    runOnUiThread(() -> {
                        updateUI(State.NO_CALL);
                        Snackbar.make(clRoot,
                                e != null ? "Error: " + e.getLocalizedMessage() : "Error",
                                SNACKBAR_DURATION).show();
                    });
                }
            });
        });

        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        /*
         * Needed for setting/abandoning audio focus during call
         */
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(isSpeakerPhoneEnabled);

        updateUI(State.NO_CALL);

        /*
         * Check camera and microphone permissions. Needed in Android M.
         */
        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraAndMicrophone();
        } else {
            connectToRoom();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
//        if (checkPermissionForCameraAndMicrophone()) {
//            if (currentCall != null) {
//                /*
//                 * If the local video track was released when the app was put in the background, recreate.
//                 */
//                currentCall.resume();
//            }
//        }

        /*
         * Route audio through cached value.
         */
        audioManager.setSpeakerphoneOn(isSpeakerPhoneEnabled);
    }

    @Override
    protected void onPause() {
//        if (currentCall != null) {
//            /*
//             * Release the local video track before going in the background. This ensures that the
//             * camera can be used by other applications while this app is in the background.
//             */
//            currentCall.pause();
//        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (compositeDisposable != null) {
            compositeDisposable.dispose();
        }
        if (boostlingo != null) {
            boostlingo.dispose();
        }
        super.onDestroy();
    }

    private void updateUI(State state) {
        switch (state) {
            case NO_CALL:
                videoStatusTextView.setText("No active call");
                break;
            case CALLING:
                videoStatusTextView.setText("Calling");
                break;
            case IN_PROGRESS:
                videoStatusTextView.setText("Call with: " + ((currentCall != null && currentCall.getInterlocutorInfo() != null) ? currentCall.getInterlocutorInfo().requiredName : ""));
                break;
        }
    }

    private boolean checkPermissionForCameraAndMicrophone() {
        int resultCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return resultCamera == PackageManager.PERMISSION_GRANTED &&
                resultMic == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionForCameraAndMicrophone() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this,
                    R.string.permissions_needed,
                    Toast.LENGTH_LONG).show();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    CAMERA_MIC_PERMISSION_REQUEST_CODE);
        }
    }

    private void connectToRoom() {
        configureAudio(true);
        boostlingo.makeVideoCall(callRequest, this, this, this, primaryVideoView, thumbnailVideoView)
                .subscribe(new SingleObserver<BLVideoCall>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onSuccess(BLVideoCall call) {
                        runOnUiThread(() -> {
                            updateUI(State.CALLING);
                            currentCall = call;
                        });
                    }

                    @Override
                    public void onError(Throwable e) {
                        runOnUiThread(() -> {
                            updateUI(State.NO_CALL);
                            Snackbar.make(clRoot,
                                    e != null ? "Error: " + e.getLocalizedMessage() : "Error",
                                    SNACKBAR_DURATION).show();
                        });
                    }
                });
    }

    private void configureAudio(boolean enable) {
        if (enable) {
            previousAudioMode = audioManager.getMode();
            // Request audio focus before making any device switch
            requestAudioFocus();
            /*
             * Use MODE_IN_COMMUNICATION as the default audio mode. It is required
             * to be in this mode when playout and/or recording starts for the best
             * possible VoIP performance. Some devices have difficulties with
             * speaker mode if this is not set.
             */
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            /*
             * Always disable microphone mute during a WebRTC call.
             */
            previousMicrophoneMute = audioManager.isMicrophoneMute();
            audioManager.setMicrophoneMute(false);
        } else {
            audioManager.setMode(previousAudioMode);
            audioManager.abandonAudioFocus(null);
            audioManager.setMicrophoneMute(previousMicrophoneMute);
        }
    }

    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFocusRequest focusRequest =
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(
                                    i -> {
                                    })
                            .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    @Override
    public void callConnected(@NonNull BLCall call) {
        runOnUiThread(() -> {
            currentCall = (BLVideoCall) call;
            updateUI(State.IN_PROGRESS);
            lastCallId = call.getCallId();
        });
    }

    @Override
    public void callFailedToConnect(@Nullable Throwable e) {
        runOnUiThread(() -> {
            currentCall = null;
            configureAudio(false);
            updateUI(State.NO_CALL);
            Snackbar.make(clRoot,
                    e != null ? "Call did fail to connect with error: " + e.getLocalizedMessage() : "Call did fail to connect",
                    SNACKBAR_DURATION).show();
        });
    }

    @Override
    public void callDisconnected(@Nullable Throwable e) {
        runOnUiThread(() -> {
            currentCall = null;
            configureAudio(false);
            updateUI(State.NO_CALL);
            Snackbar.make(clRoot,
                    e != null ? "Call did disconnect with error: " + e.getLocalizedMessage() : "Call did disconnect",
                    SNACKBAR_DURATION).show();
        });
    }

    @Override
    public void onAudioTrackPublished() {

    }

    @Override
    public void onAudioTrackUnpublished() {

    }

    @Override
    public void onVideoTrackPublished() {

    }

    @Override
    public void onVideoTrackUnpublished() {

    }

    @Override
    public void onAudioTrackEnabled() {

    }

    @Override
    public void onAudioTrackDisabled() {
        // TODO: Interpreter has disabled the video. Show you privacy screen here
        // Get the interpreter profile image URL
        // String url = currentCall.getInterlocutorInfo().imageInfo.url(null);
    }

    @Override
    public void onVideoTrackEnabled() {

    }

    @Override
    public void onVideoTrackDisabled() {

    }

    // Chat Listener
    @Override
    public void chatConnected() {

    }

    @Override
    public void chatDisconnected() {

    }

    @Override
    public void chatMessageReceived(ChatMessage message) {
        runOnUiThread(() -> {
            Snackbar.make(clRoot, "Chat Message Received: " + message.text,
                    SNACKBAR_DURATION).show();
        });
    }
}
