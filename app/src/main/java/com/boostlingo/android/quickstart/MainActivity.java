package com.boostlingo.android.quickstart;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.AppCompatSpinner;
import android.widget.ArrayAdapter;

import com.boostlingo.android.BLApiCallException;
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

public class MainActivity extends AppCompatActivity {

    private enum State {
        NOT_AUTHENTICATED,
        LOADING,
        AUTHENTICATED
    }

    public static final String CALL_REQUEST_EXTRA = "CALL_REQUEST_EXTRA";
    public static final String BOOSTLINGO_REGION_EXTRA = "BOOSTLINGO_REGION_EXTRA";
    public static final String BOOSTLINGO_AUTH_TOKEN_EXTRA = "BOOSTLINGO_AUTH_TOKEN_EXTRA";
    public static final String CALL_ID_EXTRA = "CALL_ID_EXTRA";
    public static final int CALL_ID_RESULT_CODE = 1;
    public static final int SNACKBAR_DURATION = 4000;
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
    private AppCompatButton btnVoiceCall;
    private AppCompatButton btnVideoCall;
    private AppCompatButton btnCallDetails;

    private int lastCallId;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CALL_ID_RESULT_CODE) {
            if (data != null && data.hasExtra(CALL_ID_EXTRA)) {
                lastCallId = data.getIntExtra(CALL_ID_EXTRA, 0);
            }
        }
    }

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
//        spRegions.setSelection(Boostlingo.getRegions().indexOf("us"));

        etToken = findViewById(R.id.et_token);
        etToken.setText(TOKEN);

        btnSignIn = findViewById(R.id.btn_signin);
        btnSignIn.setOnClickListener(v -> {
            updateUI(State.LOADING);
            boostlingo = new Boostlingo(this, TOKEN, spRegions.getSelectedItem().toString(), BLLogLevel.DEBUG);

            boostlingo.initialize().subscribe(new CompletableObserver() {
                @Override
                public void onSubscribe(Disposable d) {

                }

                @Override
                public void onComplete() {
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
//                            if (language.id == 9) {
                                        spLanguageFrom.setSelection(callDictionaries.languages.indexOf(language));
                                        break;
                                    }
                                }

                                spLanguageTo.setAdapter(languageAdapter);
                                for (Language language : callDictionaries.languages) {
                                    if (language.id == 1) {
//                            if (language.id == 35) {
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
                                BLApiCallException apiCallException = (BLApiCallException)e;
                                if (apiCallException != null) {
                                    Snackbar.make(clRoot,
                                            e != null ? "Error: " + apiCallException.getLocalizedMessage() + " StatusCode: " + apiCallException.getStatusCode() : "Error",
                                            SNACKBAR_DURATION).show();
                                } else {
                                    Snackbar.make(clRoot,
                                            e != null ? "Error: " + e.getLocalizedMessage() : "Error",
                                            SNACKBAR_DURATION).show();
                                }
                            });
                        }
                    });
                }

                @Override
                public void onError(Throwable e) {
                    runOnUiThread(() -> {
                        updateUI(State.NOT_AUTHENTICATED);
                        BLApiCallException apiCallException = (BLApiCallException)e;
                        if (apiCallException != null) {
                            Snackbar.make(clRoot,
                                    e != null ? "Error: " + apiCallException.getLocalizedMessage() + " StatusCode: " + apiCallException.getStatusCode() : "Error",
                                    SNACKBAR_DURATION).show();
                        } else {
                            Snackbar.make(clRoot,
                                    e != null ? "Error: " + e.getLocalizedMessage() : "Error",
                                    SNACKBAR_DURATION).show();
                        }
                    });
                }
            });
        });

        spLanguageFrom = findViewById(R.id.sp_language_from);
        spLanguageTo = findViewById(R.id.sp_language_to);
        spServiceType = findViewById(R.id.sp_service_type);
        spGender = findViewById(R.id.sp_gender);

        btnVoiceCall = findViewById(R.id.btn_voice_call);
        btnVoiceCall.setOnClickListener(v -> {
            updateUI(State.LOADING);
            CallRequest callRequest = new CallRequest(
                    ((Language) spLanguageFrom.getSelectedItem()).id,
                    ((Language) spLanguageTo.getSelectedItem()).id,
                    ((ServiceType) spServiceType.getSelectedItem()).id,
                    ((Gender) spGender.getSelectedItem()).id);
            boostlingo.validateCallReq(callRequest)
                    .subscribe(new CompletableObserver() {
                        @Override
                        public void onSubscribe(Disposable d) {
                            compositeDisposable.add(d);
                        }

                        @Override
                        public void onComplete() {
                            runOnUiThread(() -> {
                                updateUI(State.AUTHENTICATED);
                                Intent intent = new Intent(MainActivity.this, VoiceCallActivity.class);
                                intent.putExtra(CALL_REQUEST_EXTRA, callRequest);
                                intent.putExtra(BOOSTLINGO_REGION_EXTRA, spRegions.getSelectedItem().toString());
                                intent.putExtra(BOOSTLINGO_AUTH_TOKEN_EXTRA, TOKEN);
                                startActivityForResult(intent, CALL_ID_RESULT_CODE);
                            });
                        }

                        @Override
                        public void onError(Throwable e) {
                            runOnUiThread(() -> {
                                updateUI(State.AUTHENTICATED);
                                BLApiCallException apiCallException = (BLApiCallException)e;
                                if (apiCallException != null) {
                                    Snackbar.make(clRoot,
                                            e != null ? "Error: " + apiCallException.getLocalizedMessage() + " StatusCode: " + apiCallException.getStatusCode() : "Error",
                                            SNACKBAR_DURATION).show();
                                } else {
                                    Snackbar.make(clRoot,
                                            e != null ? "Error: " + e.getLocalizedMessage() : "Error",
                                            SNACKBAR_DURATION).show();
                                }
                            });
                        }
                    });
        });

        btnVideoCall = findViewById(R.id.btn_video_call);
        btnVideoCall.setOnClickListener(v -> {
            updateUI(State.LOADING);
            CallRequest callRequest = new CallRequest(
                    ((Language) spLanguageFrom.getSelectedItem()).id,
                    ((Language) spLanguageTo.getSelectedItem()).id,
                    ((ServiceType) spServiceType.getSelectedItem()).id,
                    ((Gender) spGender.getSelectedItem()).id);
            boostlingo.validateCallReq(callRequest)
                    .subscribe(new CompletableObserver() {
                @Override
                public void onSubscribe(Disposable d) {
                    compositeDisposable.add(d);
                }

                @Override
                public void onComplete() {
                    runOnUiThread(() -> {
                        updateUI(State.AUTHENTICATED);
                        Intent intent = new Intent(MainActivity.this, VideoCallActivity.class);
                        intent.putExtra(CALL_REQUEST_EXTRA, callRequest);
                        intent.putExtra(BOOSTLINGO_REGION_EXTRA, spRegions.getSelectedItem().toString());
                        intent.putExtra(BOOSTLINGO_AUTH_TOKEN_EXTRA, TOKEN);
                        startActivityForResult(intent, CALL_ID_RESULT_CODE);
                    });
                }

                @Override
                public void onError(Throwable e) {
                    runOnUiThread(() -> {
                        updateUI(State.AUTHENTICATED);
                        BLApiCallException apiCallException = (BLApiCallException)e;
                        if (apiCallException != null) {
                            Snackbar.make(clRoot,
                                    e != null ? "Error: " + apiCallException.getLocalizedMessage() + " StatusCode: " + apiCallException.getStatusCode() : "Error",
                                    SNACKBAR_DURATION).show();
                        } else {
                            Snackbar.make(clRoot,
                                    e != null ? "Error: " + e.getLocalizedMessage() : "Error",
                                    SNACKBAR_DURATION).show();
                        }
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
                        BLApiCallException apiCallException = (BLApiCallException)e;
                        if (apiCallException != null) {
                            Snackbar.make(clRoot,
                                    e != null ? "Error: " + apiCallException.getLocalizedMessage() + " StatusCode: " + apiCallException.getStatusCode() : "Error",
                                    SNACKBAR_DURATION).show();
                        } else {
                            Snackbar.make(clRoot,
                                    e != null ? "Error: " + e.getLocalizedMessage() : "Error",
                                    SNACKBAR_DURATION).show();
                        }
                    });
                }
            });
        });

        updateUI(State.NOT_AUTHENTICATED);
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
            case NOT_AUTHENTICATED:
                spRegions.setEnabled(true);
                btnSignIn.setEnabled(true);
                spLanguageFrom.setEnabled(false);
                spLanguageTo.setEnabled(false);
                spServiceType.setEnabled(false);
                spGender.setEnabled(false);
                btnVoiceCall.setEnabled(false);
                btnVideoCall.setEnabled(false);
                btnCallDetails.setEnabled(false);
                break;
            case LOADING:
                spRegions.setEnabled(false);
                btnSignIn.setEnabled(false);
                spLanguageFrom.setEnabled(false);
                spLanguageTo.setEnabled(false);
                spServiceType.setEnabled(false);
                spGender.setEnabled(false);
                btnVoiceCall.setEnabled(false);
                btnVideoCall.setEnabled(false);
                btnCallDetails.setEnabled(false);
                break;
            case AUTHENTICATED:
                spRegions.setEnabled(false);
                btnSignIn.setEnabled(false);
                spLanguageFrom.setEnabled(true);
                spLanguageTo.setEnabled(true);
                spServiceType.setEnabled(true);
                spGender.setEnabled(true);
                btnVoiceCall.setEnabled(true);
                btnVideoCall.setEnabled(true);
                btnCallDetails.setEnabled(true);
                break;
        }
    }
}
