package com.boostlingo.android.quickstart;

import android.Manifest;
import android.content.Context;
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
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.SwitchCompat;
import android.widget.ArrayAdapter;

import com.boostlingo.android.BLCall;
import com.boostlingo.android.BLCallStateListener;
import com.boostlingo.android.BLLogLevel;
import com.boostlingo.android.Boostlingo;
import com.boostlingo.android.CallDetails;
import com.boostlingo.android.CallDictionaries;
import com.boostlingo.android.CallRequest;
import com.boostlingo.android.Gender;
import com.boostlingo.android.Language;
import com.boostlingo.android.ServiceType;

import io.reactivex.CompletableObserver;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class MainActivity extends AppCompatActivity implements BLCallStateListener {
    private enum State {
        NOT_AUTHENTICATED,
        LOADING,
        AUTHENTICATED,
        CALLING,
        IN_PROGRESS,
    }

    private static final int MIC_PERMISSION_REQUEST_CODE = 1;
    private static final int SNACKBAR_DURATION = 4000;
    private static final String TOKEN = <TOKEN>;

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private Boostlingo boostlingo;
    private ConstraintLayout clRoot;
    private AppCompatSpinner spRegions;
    private AppCompatEditText etToken;
    private AppCompatButton btnSignIn;
    private AppCompatSpinner spLanguageFrom;
    private AppCompatSpinner spLanguageTo;
    private AppCompatSpinner spServiceType;
    private AppCompatSpinner spGender;
    private AppCompatButton btnCall;
    private AppCompatTextView tvStatus;
    private SwitchCompat swMute;
    private SwitchCompat swSpeaker;
    private AppCompatButton btnHangUp;
    private AppCompatButton btnCallDetails;

    private AudioManager audioManager;
    private int savedAudioMode = AudioManager.MODE_INVALID;
    private BLCall currentCall;
    private int lastCallId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        clRoot = findViewById(R.id.cl_root);

        spRegions = findViewById(R.id.sp_regions);
        ArrayAdapter<String> regionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Boostlingo.getRegions());
        regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRegions.setAdapter(regionAdapter);
        spRegions.setSelection(Boostlingo.getRegions().indexOf("qa"));

        etToken = findViewById(R.id.et_token);
        etToken.setText(TOKEN);

        btnSignIn = findViewById(R.id.btn_signin);
        btnSignIn.setOnClickListener(v -> {
            updateUI(State.LOADING);
            boostlingo = new Boostlingo(this, TOKEN, spRegions.getSelectedItem().toString(), BLLogLevel.DEBUG);
            boostlingo.setBLCallStateListener(this);
            boostlingo.getCallDictionaries().subscribe(new SingleObserver<CallDictionaries>() {
                @Override
                public void onSubscribe(Disposable d) {
                    compositeDisposable.add(d);
                }

                @Override
                public void onSuccess(CallDictionaries callDictionaries) {
                    runOnUiThread(() -> {
                        ArrayAdapter<Language> languageAdapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_spinner_item, callDictionaries.languages);
                        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

                        spLanguageFrom.setAdapter(languageAdapter);
                        for (Language language : callDictionaries.languages) {
                            if (language.id == 4) {
                                spLanguageFrom.setSelection(callDictionaries.languages.indexOf(language));
                                break;
                            }
                        }

                        spLanguageTo.setAdapter(languageAdapter);
                        for (Language language : callDictionaries.languages) {
                            if (language.id == 1) {
                                spLanguageTo.setSelection(callDictionaries.languages.indexOf(language));
                                break;
                            }
                        }

                        ArrayAdapter<ServiceType> serviceTypeAdapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_spinner_item, callDictionaries.serviceTypes);
                        serviceTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

                        spServiceType.setAdapter(serviceTypeAdapter);
                        for (ServiceType serviceType : callDictionaries.serviceTypes) {
                            if (serviceType.id == 1) {
                                spServiceType.setSelection(callDictionaries.serviceTypes.indexOf(serviceType));
                                break;
                            }
                        }

                        ArrayAdapter<Gender> genderAdapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_spinner_item, callDictionaries.genders);
                        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

                        spGender.setAdapter(genderAdapter);
                        for (Gender gender : callDictionaries.genders) {
                            if (gender.id == 1) {
                                spGender.setSelection(callDictionaries.genders.indexOf(gender));
                                break;
                            }
                        }

                        updateUI(State.AUTHENTICATED);
                    });
                }

                @Override
                public void onError(Throwable e) {
                    runOnUiThread(() -> {
                        updateUI(State.NOT_AUTHENTICATED);
                        Snackbar.make(clRoot,
                                e != null ? "Error: " + e.getLocalizedMessage() : "Error",
                                SNACKBAR_DURATION).show();
                    });
                }
            });
        });

        spLanguageFrom = findViewById(R.id.sp_language_from);
        spLanguageTo = findViewById(R.id.sp_language_to);
        spServiceType = findViewById(R.id.sp_service_type);
        spGender = findViewById(R.id.sp_gender);

        btnCall = findViewById(R.id.btn_call);
        btnCall.setOnClickListener(v -> {
            // Ensure the microphone permission is enabled.
            if (!checkPermissionForMicrophone()) {
                requestPermissionForMicrophone();
            } else {
                makeCall();
            }
        });

        tvStatus = findViewById(R.id.tv_status);

        swMute = findViewById(R.id.sw_mute);
        swMute.setOnCheckedChangeListener((v, b) -> currentCall.setMuted(b));

        swSpeaker = findViewById(R.id.sw_speaker);
        swSpeaker.setOnCheckedChangeListener((v, b) -> audioManager.setSpeakerphoneOn(b));

        btnHangUp = findViewById(R.id.btn_hangup);
        btnHangUp.setOnClickListener(v -> {
            updateUI(State.LOADING);
            boostlingo.hangUp().subscribe(new CompletableObserver() {
                @Override
                public void onSubscribe(Disposable d) {
                    compositeDisposable.add(d);
                }

                @Override
                public void onComplete() {
                    runOnUiThread(() -> updateUI(State.AUTHENTICATED));
                }

                @Override
                public void onError(Throwable e) {
                    runOnUiThread(() -> {
                        updateUI(State.AUTHENTICATED);
                        Snackbar.make(clRoot,
                                e != null ? "Error: " + e.getLocalizedMessage() : "Error",
                                SNACKBAR_DURATION).show();
                    });
                }
            });
        });

        btnCallDetails = findViewById(R.id.btn_call_details);
        btnCallDetails.setOnClickListener(v -> {
            updateUI(State.LOADING);
            boostlingo.getCallDetails(lastCallId).subscribe(new SingleObserver<CallDetails>() {
                @Override
                public void onSubscribe(Disposable d) {
                    compositeDisposable.add(d);
                }

                @Override
                public void onSuccess(CallDetails callDetails) {
                    runOnUiThread(() -> {
                        updateUI(State.AUTHENTICATED);
                        Snackbar.make(clRoot,
                                "Duration: " + callDetails.duration,
                                SNACKBAR_DURATION).show();
                    });
                }

                @Override
                public void onError(Throwable e) {
                    runOnUiThread(() -> {
                        updateUI(State.AUTHENTICATED);
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

        updateUI(State.NOT_AUTHENTICATED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        compositeDisposable.dispose();
    }

    private void makeCall() {
        updateUI(State.LOADING);
        boostlingo.makeCall(
                new CallRequest(
                        ((Language) spLanguageFrom.getSelectedItem()).id,
                        ((Language) spLanguageTo.getSelectedItem()).id,
                        ((ServiceType) spServiceType.getSelectedItem()).id,
                        ((Gender) spGender.getSelectedItem()).id))
                .subscribe(new CompletableObserver() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onComplete() {
                        runOnUiThread(() -> {
                            updateUI(State.CALLING);
                        });
                    }

                    @Override
                    public void onError(Throwable e) {
                        runOnUiThread(() -> {
                            updateUI(State.AUTHENTICATED);
                            Snackbar.make(clRoot,
                                    e != null ? "Error: " + e.getLocalizedMessage() : "Error",
                                    SNACKBAR_DURATION).show();
                        });
                    }
                });
    }

    private void updateUI(State state) {
        switch (state) {
            case NOT_AUTHENTICATED:
                spRegions.setEnabled(true);
                btnSignIn.setEnabled(true);
                spLanguageFrom.setEnabled(false);
                spLanguageTo.setEnabled(false);
                spServiceType.setEnabled(false);
                spGender.setEnabled(false);
                btnCall.setEnabled(false);
                tvStatus.setText("No active call");
                swMute.setEnabled(false);
                swSpeaker.setEnabled(false);
                btnHangUp.setEnabled(false);
                btnCallDetails.setEnabled(false);
                break;
            case LOADING:
                spRegions.setEnabled(false);
                btnSignIn.setEnabled(false);
                spLanguageFrom.setEnabled(false);
                spLanguageTo.setEnabled(false);
                spServiceType.setEnabled(false);
                spGender.setEnabled(false);
                btnCall.setEnabled(false);
                tvStatus.setText("Loading...");
                swMute.setEnabled(false);
                swSpeaker.setEnabled(false);
                btnHangUp.setEnabled(false);
                btnCallDetails.setEnabled(false);
                break;
            case AUTHENTICATED:
                spRegions.setEnabled(false);
                btnSignIn.setEnabled(false);
                spLanguageFrom.setEnabled(true);
                spLanguageTo.setEnabled(true);
                spServiceType.setEnabled(true);
                spGender.setEnabled(true);
                btnCall.setEnabled(true);
                tvStatus.setText("No active call");
                swMute.setEnabled(false);
                swSpeaker.setEnabled(false);
                btnHangUp.setEnabled(false);
                btnCallDetails.setEnabled(true);
                break;
            case CALLING:
                spRegions.setEnabled(false);
                btnSignIn.setEnabled(false);
                spLanguageFrom.setEnabled(false);
                spLanguageTo.setEnabled(false);
                spServiceType.setEnabled(false);
                spGender.setEnabled(false);
                btnCall.setEnabled(false);
                tvStatus.setText("Calling");
                swMute.setEnabled(false);
                swSpeaker.setEnabled(false);
                btnHangUp.setEnabled(true);
                btnCallDetails.setEnabled(false);
                break;
            case IN_PROGRESS:
                spRegions.setEnabled(false);
                btnSignIn.setEnabled(false);
                spLanguageFrom.setEnabled(false);
                spLanguageTo.setEnabled(false);
                spServiceType.setEnabled(false);
                spGender.setEnabled(false);
                btnCall.setEnabled(false);
                tvStatus.setText("Call with: " + ((currentCall != null && currentCall.getInterlocutorInfo() != null) ? currentCall.getInterlocutorInfo().requiredName : ""));
                swMute.setEnabled(true);
                swSpeaker.setEnabled(true);
                btnHangUp.setEnabled(true);
                btnCallDetails.setEnabled(false);
                break;
        }
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // Check if microphone permissions is granted.
        if (requestCode == MIC_PERMISSION_REQUEST_CODE && permissions.length > 0) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(clRoot,
                        "Microphone permissions needed. Please allow in your application settings.",
                        SNACKBAR_DURATION).show();
            } else {
                makeCall();
            }
        }
    }

    @Override
    public void callConnected(@NonNull BLCall call) {
        runOnUiThread(() -> {
            currentCall = call;
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
            updateUI(State.AUTHENTICATED);
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
            updateUI(State.AUTHENTICATED);
            setAudioFocus(false);
            Snackbar.make(clRoot,
                    e != null ? "Call did disconnect with error: " + e.getLocalizedMessage() : "Call did disconnect",
                    SNACKBAR_DURATION).show();
        });
    }
}
