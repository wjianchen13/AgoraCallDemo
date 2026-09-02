package com.example.agoracalldemo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.agoracalldemo.databinding.ActivityMainBinding;
import com.example.core.call.api.CallClient;
import com.example.core.call.api.CallListener;
import com.example.core.call.model.CallError;
import com.example.core.call.model.CallInviteRequest;
import com.example.core.call.model.CallSnapshot;
import com.example.core.call.model.CallState;
import com.example.core.call.model.CallType;
import com.example.core.call.model.SignalingLoginRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements CallListener {
    private enum PendingPermissionAction {
        NONE,
        START_CALL,
        ACCEPT_CALL
    }

    private ActivityMainBinding binding;
    private CallClient callClient;
    private PendingPermissionAction pendingPermissionAction = PendingPermissionAction.NONE;
    private CallInviteRequest pendingCallRequest;
    private boolean callActivityOpening;

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

        binding.localAccountInput.setText(BuildConfig.YUNXIN_ACCOUNT_ID);
        binding.yunxinTokenInput.setText(BuildConfig.YUNXIN_ACCOUNT_TOKEN);
        callClient = ((CallDemoApplication) getApplication()).getCallClient();
        bindActions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        callClient.addListener(this);
    }

    @Override
    protected void onStop() {
        callClient.removeListener(this);
        super.onStop();
    }

    private void bindActions() {
        binding.loginButton.setOnClickListener(view -> loginYunxin());
        binding.logoutButton.setOnClickListener(view -> callClient.logoutSignaling());
        binding.callButton.setOnClickListener(view -> prepareOutgoingCall());
        binding.acceptButton.setOnClickListener(view -> prepareAcceptCall());
        binding.rejectButton.setOnClickListener(view -> callClient.rejectCall());
        binding.cancelCallButton.setOnClickListener(view -> callClient.leave());
    }

    private void loginYunxin() {
        String accountId = binding.localAccountInput.getText().toString().trim();
        String token = binding.yunxinTokenInput.getText().toString().trim();
        if (TextUtils.isEmpty(accountId)) {
            binding.localAccountInput.setError(getString(R.string.local_account_hint));
            return;
        }
        if (TextUtils.isEmpty(token)) {
            binding.yunxinTokenInput.setError(getString(R.string.yunxin_token_hint));
            return;
        }
        Integer uid = readRtcUid();
        if (uid == null) {
            return;
        }
        hideKeyboard();
        callClient.loginSignaling(new SignalingLoginRequest(accountId, token, uid));
    }

    private void prepareOutgoingCall() {
        String peerAccountId = binding.peerAccountInput.getText().toString().trim();
        if (TextUtils.isEmpty(peerAccountId)) {
            binding.peerAccountInput.setError(getString(R.string.peer_account_hint));
            return;
        }
        CallType type = binding.videoCallCheckbox.isChecked()
                ? CallType.VIDEO
                : CallType.AUDIO;
        pendingCallRequest = new CallInviteRequest(peerAccountId, type);
        requestCallPermissions(PendingPermissionAction.START_CALL, type);
    }

    private void prepareAcceptCall() {
        requestCallPermissions(
                PendingPermissionAction.ACCEPT_CALL,
                callClient.getSnapshot().getCallType()
        );
    }

    private void requestCallPermissions(PendingPermissionAction action, CallType callType) {
        hideKeyboard();
        String[] missingPermissions = findMissingPermissions(callType);
        if (missingPermissions.length == 0) {
            runPermissionAction(action);
        } else {
            pendingPermissionAction = action;
            permissionLauncher.launch(missingPermissions);
        }
    }

    private Integer readRtcUid() {
        try {
            String text = binding.uidInput.getText().toString().trim();
            long parsed = TextUtils.isEmpty(text) ? 0 : Long.parseLong(text);
            if (parsed < 0 || parsed > Integer.MAX_VALUE) {
                throw new NumberFormatException("UID out of range");
            }
            return (int) parsed;
        } catch (NumberFormatException error) {
            binding.uidInput.setError(getString(R.string.invalid_uid));
            return null;
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

    private void onPermissionsResult(Map<String, Boolean> ignored) {
        PendingPermissionAction action = pendingPermissionAction;
        pendingPermissionAction = PendingPermissionAction.NONE;
        CallType type = action == PendingPermissionAction.START_CALL
                && pendingCallRequest != null
                ? pendingCallRequest.getCallType()
                : callClient.getSnapshot().getCallType();
        boolean audioGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;
        boolean cameraGranted = type != CallType.VIDEO
                || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        if (audioGranted && cameraGranted) {
            runPermissionAction(action);
        } else {
            pendingCallRequest = null;
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
        }
    }

    private void runPermissionAction(PendingPermissionAction action) {
        if (action == PendingPermissionAction.START_CALL && pendingCallRequest != null) {
            CallInviteRequest request = pendingCallRequest;
            pendingCallRequest = null;
            callClient.startCall(request);
        } else if (action == PendingPermissionAction.ACCEPT_CALL) {
            callClient.acceptCall();
        }
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
        runOnUiThread(() -> {
            if (binding != null) {
                render(snapshot);
            }
        });
    }

    private void render(CallSnapshot snapshot) {
        CallState state = snapshot.getState();
        if (shouldShowCallActivity(state)) {
            openCallActivity();
            return;
        }
        callActivityOpening = false;

        binding.statusText.setText(snapshot.getStatusMessage());
        boolean loggedIn = snapshot.isSignalingLoggedIn();
        boolean setupVisible = state == CallState.IDLE
                || state == CallState.FAILED
                || state == CallState.LOGGING_IN
                || state == CallState.READY;
        boolean ringing = state == CallState.RINGING;
        boolean calling = state == CallState.CALLING;

        binding.setupPanel.setVisibility(setupVisible ? View.VISIBLE : View.GONE);
        binding.incomingPanel.setVisibility(ringing ? View.VISIBLE : View.GONE);
        binding.outgoingPanel.setVisibility(calling ? View.VISIBLE : View.GONE);
        binding.callSetupContainer.setVisibility(
                loggedIn && state == CallState.READY ? View.VISIBLE : View.GONE
        );
        binding.loginButton.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        binding.loginButton.setEnabled(state != CallState.LOGGING_IN);
        binding.logoutButton.setVisibility(
                loggedIn && state == CallState.READY ? View.VISIBLE : View.GONE
        );
        binding.localAccountInput.setEnabled(!loggedIn && state != CallState.LOGGING_IN);
        binding.yunxinTokenInput.setEnabled(!loggedIn && state != CallState.LOGGING_IN);
        binding.uidInput.setEnabled(!loggedIn && state != CallState.LOGGING_IN);
        binding.incomingTitle.setText(snapshot.getStatusMessage());
        binding.outgoingTitle.setText(snapshot.getStatusMessage());
    }

    private boolean shouldShowCallActivity(CallState state) {
        return state == CallState.ACCEPTING
                || state == CallState.PREPARING
                || state == CallState.JOINING
                || state == CallState.WAITING_REMOTE
                || state == CallState.CONNECTED
                || state == CallState.RECONNECTING;
    }

    private void openCallActivity() {
        if (callActivityOpening) {
            return;
        }
        callActivityOpening = true;
        startActivity(new Intent(this, CallActivity.class));
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
        binding = null;
        super.onDestroy();
    }
}
