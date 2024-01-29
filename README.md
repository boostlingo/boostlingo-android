# Module Boostlingo Android SDK

The Boostlingo Android library enables developers to embed the Boostlingo caller directly into their own applications. This can then be used for placing calls in the Boostlingo platform.

## Getting Started

In order to place calls in Boostlingo, you must have a requestor account. You can then embed Boostlingo into your application, request a Boostlingo API token from your server, and start making calls.

## Installation

### AAR

[Download](https://github.com/boostlingo/boostlingo-android/releases/download/v1.0.2/boostlingo-release.aar) the lastest version of the Boostlingo library and put it into your /libs folder.

Add all needed dependencies into your `build.gradle`:

```kotlin
// Boostlingo
implementation fileTree(dir: 'libs', include: ['*.aar'])

// SignalR
implementation 'com.microsoft.signalr:signalr:7.0.4'

// Rx
implementation 'io.reactivex.rxjava3:rxkotlin:3.0.1'
implementation 'io.reactivex.rxjava3:rxandroid:3.0.2'

// Retrofit
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:adapter-rxjava3:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'

// OkHttp
implementation 'com.squareup.okhttp3:logging-interceptor:4.10.0'
implementation 'com.squareup.okhttp3:okhttp:4.10.0'

// Twilio Voice
implementation 'com.twilio:voice-android:6.1.3'

// Twilio Video
implementation 'com.twilio:video-android-ktx:7.6.1'

// Twilio AudioSwitch
implementation 'com.twilio:audioswitch:1.1.7'
```

## Usage

These steps will guide you through the basic process of placing calls through Boostlingo.

### Request Boostlingo authentication token

First step is to obtain a boostlingo authentication token from your server.  Never store an API token or username/password in your front end/mobile code.  Your server should be the one that logs the user in, obtains the authentication token, and passes it back down to the web application.

### Obtain Boostlingo authentication token via API endpoint

```json
POST https://app.boostlingo.com/api/web/account/signin
```

Request Model

```json
{
"email": "<string>",
"password": "<string>"
}
```

Response Model
`token` is what will be needed by the boostlingo sdk

```json
{
"userAccountId": "<integer>",
"refreshToken": "<string>",
"role": "<string>",
"token": "<string>",
"companyAccountId": "<integer>"
}
```

### Quickstart

This is a working example that will demonstrate how to Boostlingo calls.

Enter your TOKEN in the token field, as well as your REGION in the region field.

### Create instance of Boostlingo class and load dictionaries

We recommend you do this only once. The Boostlingo library will cache specific data and create instances of classes that do not need to be refreshed very frequently. The next step is typically to pull down the call dictionaries. Whether you expose these directly or are just mapping languages and service types with your internal types, loading these lists will almost definitely be required.

```kotlin
val boostlingoSdk: BoostlingoSDK by lazy {
    BoostlingoSDK(
        authToken,
        this,
        BLLogLevel.DEBUG,
        region
    )
}

boostlingoSdk.initialize()
    .subscribeOn(Schedulers.io())
    .andThen(
        boostlingoSdk.getCallDictionaries()
            .subscribeOn(Schedulers.io())
    )
    .observeOn(AndroidSchedulers.mainThread())
    .doOnSubscribe { updateUI(State.LOADING) }
    .subscribe(
        { callDictionaries ->
            updateUI(State.INIT)
            binding.languageToTextView.text = callDictionaries.languages.first { language -> language.id == 1 }.englishName
            binding.languageFromTextView.text = callDictionaries.languages.first { language -> language.id == 4 }.englishName
            binding.serviceTypeTextView.text = callDictionaries.serviceTypes.first { language -> language.id == 1 }.name
            binding.genderTextView.text = callDictionaries.genders.first().name
        },
        {
            updateUI(State.NOT_INIT)
            val httpException = it as? HttpException
            if (httpException != null) {
                Snackbar.make(
                    binding.rootView,
                    "Error: " + httpException.localizedMessage + " StatusCode: " + httpException.code(),
                    Snackbar.LENGTH_LONG
                ).show()
            } else {
                Snackbar.make(
                    binding.rootView,
                    "Error: " + it.localizedMessage,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    )
    .let { compositeDisposable.add(it) }
```

### Handling BLCallEvent

```kotlin
private fun subscribeToCallEvent() {
    boostlingoSdk.callEventObservable
        .subscribeOn(AndroidSchedulers.mainThread())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            ::handleCallEvent,
            ::handleError
        )
        .let { compositeDisposable.add(it) }
}

private fun handleCallEvent(callEvent: BLCallEvent) {
    when (callEvent) {
        is BLCallEvent.CallConnected -> {
            Log.d(null, "BLCallEvent.CallConnected")
            audioSwitch.activate()
            currentCall = callEvent.call as BLVideoCall
            updateUI(State.IN_PROGRESS)

            Log.d(null, "Participants: ${callEvent.call.participants.count()}")
            callEvent.call
                .participants
                .firstOrNull()
                ?.let {
                    currentCall?.addRenderer(it.identity, binding.primaryVideoView)
                }
        }
        is BLCallEvent.CallDisconnected -> {
            Log.d(null, "BLCallEvent.CallDisconnected")
            audioSwitch.deactivate()
            currentCall = null
            updateUI(State.NO_CALL)
            Snackbar.make(
                binding.rootView,
                "Call did disconnect with error: " + callEvent.e?.localizedMessage,
                Snackbar.LENGTH_LONG
            ).show()
        }
        is BLCallEvent.CallFailedToConnect -> {
            Log.d(null, "BLCallEvent.CallFailedToConnect")
            audioSwitch.deactivate()
            currentCall = null
            updateUI(State.NO_CALL)
            Snackbar.make(
                binding.rootView,
                "Call did fail to connect with error: " + callEvent.e?.localizedMessage,
                Snackbar.LENGTH_LONG
            ).show()
        }
        BLCallEvent.ChatConnected -> {
            Log.d(null, "BLCallEvent.ChatConnected")
        }
        BLCallEvent.ChatDisconnected -> {
            Log.d(null, "BLCallEvent.ChatDisconnected")
        }
        is BLCallEvent.ChatMessageReceived -> {
            Log.d(null, "BLCallEvent.ChatMessageReceived: ${callEvent.message}")
        }
        is BLCallEvent.ParticipantAdded -> {
            Log.d(null, "BLCallEvent.ParticipantAdded ${callEvent.participant.accountId}")
            Log.d(null, "Participants: ${callEvent.call.participants.count()}")
            callEvent.participant
                .let {
                    currentCall?.addRenderer(it.identity, binding.primaryVideoView)
                }
        }
        is BLCallEvent.ParticipantRemoved -> {
            Log.d(null, "BLCallEvent.ParticipantUpdated: " + callEvent.participant.accountId)
        }
        is BLCallEvent.ParticipantUpdated -> {
            Log.d(null, "BLCallEvent.ParticipantUpdated: " + callEvent.participant.accountId)
        }
    }
}

private fun handleError(t: Throwable) {
    updateUI(State.NO_CALL)
    Snackbar.make(
        binding.rootView,
        "Error: " + t.localizedMessage,
        Snackbar.LENGTH_LONG
    ).show()
}
```

### Placing a call

Before placing a call you will need to check `Manifest.permission.CAMERA`, `Manifest.permission.RECORD_AUDIO` and `Manifest.permission.BLUETOOTH_CONNECT` permissions and activate `com.twilio.audioswitch.AudioSwitch`

```kotlin
private fun makeCall() {
    val callRequest = CallRequest(
        languageFromId = 4,
        languageToId = 1,
        serviceTypeId = 1,
        genderId = null,
        data = listOf(
            AdditionalField(
                key = "MyInternalId",
                value = "1984"
            ),
            AdditionalField(
                key = "CustomCallField",
                value = "CustomCallFieldValue"
            )
        ),
        isVideo = false
    )

    boostlingoSdk.makeVoiceCall(callRequest)
        .subscribeOn(AndroidSchedulers.mainThread())
        .observeOn(AndroidSchedulers.mainThread())
        .doOnSubscribe { updateUI(State.CALLING) }
        .subscribe(
            {
                currentCall = it
            },
            ::handleError
        )
        .let { compositeDisposable.add(it) }
}

private fun makeCall() {
    val callRequest = CallRequest(
        languageFromId = 4,
        languageToId = 1,
        serviceTypeId = 1,
        genderId = null,
        data = listOf(
            AdditionalField(
                key = "MyInternalId",
                value = "1984"
            ),
            AdditionalField(
                key = "CustomCallField",
                value = "CustomCallFieldValue"
            )
        ),
        isVideo = true
    )

    boostlingoSdk.makeVideoCall(
        callRequest,
        binding.thumbnailVideoView
    )
        .subscribeOn(AndroidSchedulers.mainThread())
        .observeOn(AndroidSchedulers.mainThread())
        .doOnSubscribe { updateUI(State.CALLING) }
        .subscribe(
            {
                currentCall = it
            },
            ::handleError
        )
}
```

### Adding a video participant renderer

```kotlin
is BLCallEvent.ParticipantAdded -> {
    Log.d(null, "BLCallEvent.ParticipantAdded ${callEvent.participant.accountId}")
    Log.d(null, "Participants: ${callEvent.call.participants.count()}")
    callEvent.participant
        .let {
            currentCall?.addRenderer(it.identity, binding.primaryVideoView)
        }
}
```

### Sending a chat message

```kotlin
boostlingoSdk.sendChatMessage("TEST")
    .subscribeOn(AndroidSchedulers.mainThread())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
        {
            Snackbar.make(
                binding.rootView,
                "Chat message sent: " + it.text,
                Snackbar.LENGTH_LONG
            ).show()
        },
        ::handleError
    )
    .let { compositeDisposable.add(it) }

is BLCallEvent.ChatMessageReceived -> {
    Log.d(null, "BLCallEvent.ChatMessageReceived: ${callEvent.message}")
}
```

### Dialing a third-party participant

Getting requestor profile image url.

```kotlin
currentCall.dialThirdParty("18004444444")
    .subscribeOn(AndroidSchedulers.mainThread())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
        {
            Log.d(null, "dialThirdParty success")
        },
        {
            Log.d(null, it.localizedMessage.orEmpty())
        }
    )
    .let { compositeDisposable.add(it) }
```

## More Documentation

You can find more documentation and useful information below:

* [Quickstart](https://github.com/boostlingo/boostlingo-android/tree/master)
* [Doc](http://connect.boostlingo.com/sdk/boostlingo-android/1.0/docs/index.html)

# Package com.boostlingo.android

Boostlingo SDK package.
