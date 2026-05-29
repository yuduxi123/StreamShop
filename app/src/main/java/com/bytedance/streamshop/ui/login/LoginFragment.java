package com.bytedance.streamshop.ui.login;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class LoginFragment extends Fragment {

    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;
    private MaterialButton loginBtn;
    private TextView errorView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        usernameInput = view.findViewById(R.id.login_username);
        passwordInput = view.findViewById(R.id.login_password);
        loginBtn = view.findViewById(R.id.login_btn);
        errorView = view.findViewById(R.id.login_error);

        loginBtn.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        // 基本校验：不能为空
        if (username.isEmpty()) {
            showError("请输入账号");
            return;
        }
        if (password.isEmpty()) {
            showError("请输入密码");
            return;
        }

        errorView.setVisibility(View.GONE);
        loginBtn.setEnabled(false);
        loginBtn.setText("登录中...");

        new Thread(() -> {
            try {
                ApiService api = new ApiService();
                api.login(username, password);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // 登录成功，关闭页面并通知Profile刷新
                        getActivity().setResult(android.app.Activity.RESULT_OK);
                        getActivity().finish();
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        showError("登录失败：账号或密码错误");
                        loginBtn.setEnabled(true);
                        loginBtn.setText("登录");
                    });
                }
            }
        }).start();
    }

    private void showError(String message) {
        errorView.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }
}
