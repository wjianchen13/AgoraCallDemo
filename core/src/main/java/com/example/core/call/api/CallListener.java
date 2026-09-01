package com.example.core.call.api;

import com.example.core.call.model.CallError;
import com.example.core.call.model.CallSnapshot;

public interface CallListener {
    void onCallSnapshotChanged(CallSnapshot snapshot);

    void onCallError(CallError error);
}
