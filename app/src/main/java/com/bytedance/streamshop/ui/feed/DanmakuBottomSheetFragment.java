package com.bytedance.streamshop.ui.feed;

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
import com.bytedance.streamshop.data.remote.ApiService;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class DanmakuBottomSheetFragment extends BottomSheetDialogFragment {
    private static final String ARG_VIDEO_ID = "videoId";

    public interface OnDanmakuToggleListener {
        void onDanmakuToggled(boolean enabled);
    }

    private String videoId;
    private ApiService apiService;
    private boolean danmakuEnabled = true;
    private OnDanmakuToggleListener toggleListener;

    private ImageView toggleBtn;
    private EditText inputView;

    public static DanmakuBottomSheetFragment newInstance(String videoId) {
        DanmakuBottomSheetFragment fragment = new DanmakuBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_VIDEO_ID, videoId);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnDanmakuToggleListener(OnDanmakuToggleListener listener) {
        this.toggleListener = listener;
    }

    public void setDanmakuEnabled(boolean enabled) {
        this.danmakuEnabled = enabled;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            videoId = getArguments().getString(ARG_VIDEO_ID);
        }
        apiService = new ApiService();
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

        toggleBtn = view.findViewById(R.id.danmaku_toggle_btn);
        inputView = view.findViewById(R.id.danmaku_input);

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
                sendDanmaku();
                return true;
            }
            return false;
        });
    }

    private void sendDanmaku() {
        String content = inputView.getText().toString().trim();
        if (content.isEmpty()) return;

        inputView.setEnabled(false);

        new Thread(() -> {
            try {
                apiService.postDanmaku(videoId, content, "#FFFFFF", 0);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        inputView.setText("");
                        inputView.setEnabled(true);
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> inputView.setEnabled(true));
                }
            }
        }).start();
    }
}
