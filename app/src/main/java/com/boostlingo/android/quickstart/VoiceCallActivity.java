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
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.SwitchCompat;

import com.boostlingo.android.BLCall;
import com.boostlingo.android.BLCallStateListener;
import com.boostlingo.android.BLLogLevel;
import com.boostlingo.android.BLVoiceCall;
import com.boostlingo.android.Boostlingo;
import com.boostlingo.android.CallRequest;

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

public class VoiceCallActivity extends AppCompatActivity implements BLCallStateListener {

    private enum State {
        NO_CALL,
        CALLING,
        IN_PROGRESS
    }

    private static final int MIC_PERMISSION_REQUEST_CODE = 1;

    private ConstraintLayout clRoot;
    private AppCompatTextView tvStatus;
    private SwitchCompat swMute;
    private SwitchCompat swSpeaker;
    private AppCompatButton btnHangUp;

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private Boostlingo boostlingo;
    private AudioManager audioManager;
    private int savedAudioMode = AudioManager.MODE_INVALID;
    private BLVoiceCall currentCall;
    private int lastCallId;
    private CallRequest callRequest;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // Check if microphone permissions is granted.
        if (requestCode == MIC_PERMISSION_REQUEST_CODE && permissions.length > 0) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(clRoot,
                        R.string.permissions_needed,
                        SNACKBAR_DURATION).show();
            } else {
                makeCall();
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
        setContentView(R.layout.activity_voice_call);

        if (getIntent().hasExtra(CALL_REQUEST_EXTRA)) {
            callRequest = getIntent().getParcelableExtra(CALL_REQUEST_EXTRA);
            String region = getIntent().getStringExtra(BOOSTLINGO_REGION_EXTRA);
            String token = getIntent().getStringExtra(BOOSTLINGO_AUTH_TOKEN_EXTRA);
            boostlingo = new Boostlingo(this, token, region, BLLogLevel.DEBUG);
        }

        clRoot = findViewById(R.id.cl_root);

        tvStatus = findViewById(R.id.tv_status);

        swMute = findViewById(R.id.sw_mute);
        swMute.setOnCheckedChangeListener((v, b) -> currentCall.setMuted(b));

        swSpeaker = findViewById(R.id.sw_speaker);
        swSpeaker.setOnCheckedChangeListener((v, b) -> audioManager.setSpeakerphoneOn(b));

        btnHangUp = findViewById(R.id.btn_hangup);
        btnHangUp.setOnClickListener(v -> {
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
         * Needed for setting/abandoning audio focus during a call
         */
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);

        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        updateUI(State.NO_CALL);

        // Ensure the microphone permission is enabled.
        if (!checkPermissionForMicrophone()) {
            requestPermissionForMicrophone();
        } else {
            makeCall();
        }
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

    private void makeCall() {
        boostlingo.makeVoiceCall(callRequest, this)
                .subscribe(new SingleObserver<BLVoiceCall>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onSuccess(BLVoiceCall call) {
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

    private boolean checkPermissionForMicrophone() {
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return resultMic == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionForMicrophone() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            Snackbar.make(clRoot,
                    "Microphone permissions needed. Please allow in your application settings.",
                    SNACKBAR_DURATION).show();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MIC_PERMISSION_REQUEST_CODE);
        }
    }

    private void setAudioFocus(boolean setFocus) {
        if (audioManager != null) {
            if (setFocus) {
                savedAudioMode = audioManager.getMode();
                // Request audio focus before making any device switch.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
                    AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                                @Override
                                public void onAudioFocusChange(int i) {
                                }
                            })
                            .build();
                    audioManager.requestAudioFocus(focusRequest);
                } else {
                    int focusRequestResult = audioManager.requestAudioFocus(focusChange -> {
                            }, AudioManager.STREAM_VOICE_CALL,
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                }
                /*
                 * Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
                 * required to be in this mode when playout and/or recording starts for
                 * best possible VoIP performance. Some devices have difficulties with speaker mode
                 * if this is not set.
                 */
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            } else {
                audioManager.setMode(savedAudioMode);
                audioManager.abandonAudioFocus(null);
            }
        }
    }

    private void updateUI(State state) {
        switch (state) {
            case NO_CALL:
                tvStatus.setText("No active call");
                swMute.setEnabled(false);
                swSpeaker.setEnabled(false);
                btnHangUp.setEnabled(false);
                break;
            case CALLING:
                tvStatus.setText("Calling");
                swMute.setEnabled(false);
                swSpeaker.setEnabled(false);
                btnHangUp.setEnabled(true);
                break;
            case IN_PROGRESS:
                tvStatus.setText("Call with: " + ((currentCall != null && currentCall.getInterlocutorInfo() != null) ? currentCall.getInterlocutorInfo().requiredName : ""));
                swMute.setEnabled(true);
                swSpeaker.setEnabled(true);
                btnHangUp.setEnabled(true);
                break;
        }
    }

    @Override
    public void callConnected(@NonNull BLCall call) {
        runOnUiThread(() -> {
            currentCall = (BLVoiceCall) call;
            lastCallId = call.getCallId();
            swMute.setChecked(call.isMuted());
            updateUI(State.IN_PROGRESS);
            setAudioFocus(true);
            swSpeaker.setChecked(true);
        });
    }

    @Override
    public void callFailedToConnect(@Nullable Throwable e) {
        runOnUiThread(() -> {
            currentCall = null;
            updateUI(State.NO_CALL);
            setAudioFocus(false);
            Snackbar.make(clRoot,
                    e != null ? "Call did fail to connect with error: " + e.getLocalizedMessage() : "Call did fail to connect",
                    SNACKBAR_DURATION).show();
        });
    }

    @Override
    public void callDisconnected(@Nullable Throwable e) {
        runOnUiThread(() -> {
            currentCall = null;
            updateUI(State.NO_CALL);
            setAudioFocus(false);
            Snackbar.make(clRoot,
                    e != null ? "Call did disconnect with error: " + e.getLocalizedMessage() : "Call did disconnect",
                    SNACKBAR_DURATION).show();
        });
    }
}
