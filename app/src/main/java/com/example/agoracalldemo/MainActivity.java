package com.example.agoracalldemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.agoracalldemo.databinding.ActivityMainBinding;
import com.example.core.call.api.CallClient;
import com.example.core.call.api.CallClientFactory;
import com.example.core.call.api.CallListener;
import com.example.core.call.model.CallError;
import com.example.core.call.model.CallJoinRequest;
import com.example.core.call.model.CallSnapshot;
import com.example.core.call.model.CallState;
import com.example.core.call.model.CallType;

public class MainActivity extends AppCompatActivity implements CallListener {
    private ActivityMainBinding binding;
    private CallClient callClient;
    private CallJoinRequest pendingJoinRequest;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onPermissionsResult
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.channelInput.setText("test");
        createVideoViews();
        callClient = CallClientFactory.createDemoClient(
                getApplicationContext(),
                BuildConfig.AGORA_APP_ID,
                BuildConfig.AGORA_APP_CERT
        );
        callClient.addListener(this);
        bindActions();
    }

    private void createVideoViews() {
        SurfaceView remoteView = new SurfaceView(this);
        binding.remoteVideoContainer.addView(
                remoteView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        SurfaceView localView = new SurfaceView(this);
        localView.setZOrderMediaOverlay(true);
        binding.localVideoContainer.addView(
                localView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        // CallClient is created immediately after the views, then these are attached in onCreate.
        binding.localVideoContainer.setTag(localView);
        binding.remoteVideoContainer.setTag(remoteView);
    }

    private void bindActions() {
        callClient.attachLocalVideo((SurfaceView) binding.localVideoContainer.getTag());
        callClient.attachRemoteVideo((SurfaceView) binding.remoteVideoContainer.getTag());

        binding.joinButton.setOnClickListener(view -> prepareJoin());
        binding.leaveButton.setOnClickListener(view -> callClient.leave());
        binding.microphoneButton.setOnClickListener(view -> {
            CallSnapshot snapshot = callClient.getSnapshot();
            callClient.setMicrophoneEnabled(!snapshot.isMicrophoneEnabled());
        });
        binding.cameraButton.setOnClickListener(view -> {
            CallSnapshot snapshot = callClient.getSnapshot();
            callClient.setCameraEnabled(!snapshot.isCameraEnabled());
        });
        binding.switchCameraButton.setOnClickListener(view -> callClient.switchCamera());
        binding.speakerButton.setOnClickListener(view -> {
            CallSnapshot snapshot = callClient.getSnapshot();
            callClient.setSpeakerEnabled(!snapshot.isSpeakerEnabled());
        });
    }

    private void prepareJoin() {
        String channelName = binding.channelInput.getText().toString().trim();
        if (TextUtils.isEmpty(channelName)) {
            binding.channelInput.setError(getString(R.string.channel_hint));
            return;
        }

        int uid;
        try {
            String uidText = binding.uidInput.getText().toString().trim();
            long parsedUid = TextUtils.isEmpty(uidText) ? 0 : Long.parseLong(uidText);
            if (parsedUid < 0 || parsedUid > Integer.MAX_VALUE) {
                throw new NumberFormatException("UID out of range");
            }
            uid = (int) parsedUid;
        } catch (NumberFormatException error) {
            binding.uidInput.setError(getString(R.string.invalid_uid));
            return;
        }

        CallType callType = binding.videoCallCheckbox.isChecked()
                ? CallType.VIDEO
                : CallType.AUDIO;
        pendingJoinRequest = new CallJoinRequest(
                channelName,
                uid,
                callType,
                binding.tokenInput.getText().toString()
        );
        hideKeyboard();

        String[] missingPermissions = findMissingPermissions(callType);
        if (missingPermissions.length == 0) {
            startPendingJoin();
        } else {
            permissionLauncher.launch(missingPermissions);
        }
    }

    private String[] findMissingPermissions(CallType callType) {
        List<String> required = new ArrayList<>();
        required.add(Manifest.permission.RECORD_AUDIO);
        if (callType == CallType.VIDEO) {
            required.add(Manifest.permission.CAMERA);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            required.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        List<String> missing = new ArrayList<>();
        for (String permission : required) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        return missing.toArray(new String[0]);
    }

    private void onPermissionsResult(Map<String, Boolean> result) {
        if (pendingJoinRequest == null) {
            return;
        }
        boolean audioGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;
        boolean cameraGranted = pendingJoinRequest.getCallType() != CallType.VIDEO
                || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        if (audioGranted && cameraGranted) {
            startPendingJoin();
        } else {
            pendingJoinRequest = null;
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
        }
    }

    private void startPendingJoin() {
        if (pendingJoinRequest == null) {
            return;
        }
        CallJoinRequest request = pendingJoinRequest;
        pendingJoinRequest = null;
        callClient.join(request);
    }

    private void hideKeyboard() {
        View currentFocus = getCurrentFocus();
        if (currentFocus == null) {
            return;
        }
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        manager.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        currentFocus.clearFocus();
    }

    @Override
    public void onCallSnapshotChanged(CallSnapshot snapshot) {
        runOnUiThread(() -> render(snapshot));
    }

    private void render(CallSnapshot snapshot) {
        binding.statusText.setText(snapshot.getStatusMessage());

        boolean preparing = snapshot.getState() == CallState.PREPARING;
        boolean idle = snapshot.getState() == CallState.IDLE
                || snapshot.getState() == CallState.FAILED;
        boolean active = snapshot.getState() == CallState.JOINING
                || snapshot.getState() == CallState.WAITING_REMOTE
                || snapshot.getState() == CallState.CONNECTED
                || snapshot.getState() == CallState.RECONNECTING;
        boolean video = snapshot.getCallType() == CallType.VIDEO;

        binding.setupPanel.setVisibility(idle || preparing ? View.VISIBLE : View.GONE);
        binding.joinButton.setEnabled(idle);
        binding.controlPanel.setVisibility(active ? View.VISIBLE : View.GONE);
        binding.localVideoContainer.setVisibility(
                active && video && snapshot.isCameraEnabled() ? View.VISIBLE : View.GONE
        );
        binding.cameraButton.setVisibility(video ? View.VISIBLE : View.GONE);
        binding.switchCameraButton.setVisibility(video ? View.VISIBLE : View.GONE);

        binding.microphoneButton.setText(snapshot.isMicrophoneEnabled()
                ? R.string.mic_on
                : R.string.mic_off);
        binding.cameraButton.setText(snapshot.isCameraEnabled()
                ? R.string.camera_on
                : R.string.camera_off);
        binding.speakerButton.setText(snapshot.isSpeakerEnabled()
                ? R.string.speaker_on
                : R.string.speaker_off);

        binding.emptyVideoHint.setText(video
                ? R.string.video_waiting_hint
                : R.string.audio_call_hint);
        binding.emptyVideoHint.setVisibility(
                active && (!video || snapshot.getRemoteUid() == 0) ? View.VISIBLE : View.GONE
        );
    }

    @Override
    public void onCallError(CallError error) {
        runOnUiThread(() -> Toast.makeText(
                this,
                error.getMessage(),
                Toast.LENGTH_LONG
        ).show());
    }

    @Override
    protected void onDestroy() {
        if (callClient != null) {
            callClient.removeListener(this);
            callClient.release();
        }
        binding = null;
        super.onDestroy();
    }
}
