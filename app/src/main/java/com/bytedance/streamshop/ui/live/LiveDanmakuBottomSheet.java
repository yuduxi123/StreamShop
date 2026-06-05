package com.bytedance.streamshop.ui.live;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.LiveWebSocketClient;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class LiveDanmakuBottomSheet extends BottomSheetDialogFragment {

    public interface OnDanmakuToggleListener {
        void onDanmakuToggled(boolean enabled);
    }

    private LiveWebSocketClient wsClient;
    private boolean danmakuEnabled = true;
    private OnDanmakuToggleListener toggleListener;

    public static LiveDanmakuBottomSheet newInstance(LiveWebSocketClient wsClient, boolean danmakuEnabled) {
        LiveDanmakuBottomSheet fragment = new LiveDanmakuBottomSheet();
        fragment.wsClient = wsClient;
        fragment.danmakuEnabled = danmakuEnabled;
        return fragment;
    }

    public void setOnDanmakuToggleListener(OnDanmakuToggleListener listener) {
        this.toggleListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_danmaku_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView toggleBtn = view.findViewById(R.id.danmaku_toggle_btn);
        EditText inputView = view.findViewById(R.id.danmaku_input);

        toggleBtn.setSelected(danmakuEnabled);
        toggleBtn.setOnClickListener(v -> {
            danmakuEnabled = !danmakuEnabled;
            toggleBtn.setSelected(danmakuEnabled);
            if (toggleListener != null) {
                toggleListener.onDanmakuToggled(danmakuEnabled);
            }
        });

        inputView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                String content = inputView.getText().toString().trim();
                if (!content.isEmpty() && wsClient != null) {
                    wsClient.sendDanmaku(content);
                    inputView.setText("");
                }
                return true;
            }
            return false;
        });
    }
}
