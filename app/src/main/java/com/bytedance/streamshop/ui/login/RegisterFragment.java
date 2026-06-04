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

public class RegisterFragment extends Fragment {

    private TextInputEditText usernameInput;
    private TextInputEditText accountInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmInput;
    private MaterialButton registerBtn;
    private TextView errorView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        usernameInput = view.findViewById(R.id.register_username);
        accountInput = view.findViewById(R.id.register_account);
        passwordInput = view.findViewById(R.id.register_password);
        confirmInput = view.findViewById(R.id.register_confirm);
        registerBtn = view.findViewById(R.id.register_btn);
        errorView = view.findViewById(R.id.register_error);

        registerBtn.setOnClickListener(v -> attemptRegister());
    }

    private void attemptRegister() {
        String username = usernameInput.getText().toString().trim();
        String account = accountInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String confirm = confirmInput.getText().toString().trim();

        // 校验用户名
        if (username.isEmpty()) {
            showError("请输入用户名");
            return;
        }

        // 校验账号：11位及以上数字
        if (account.isEmpty()) {
            showError("请输入数字账号");
            return;
        }
        if (!account.matches("\\d+")) {
            showError("账号必须为纯数字");
            return;
        }
        if (account.length() < 11) {
            showError("账号长度需在11位及以上");
            return;
        }

        // 校验密码：包含英文字母和数字
        if (password.isEmpty()) {
            showError("请输入密码");
            return;
        }
        if (password.length() < 6) {
            showError("密码长度不能少于6位");
            return;
        }
        if (!password.matches(".*[a-zA-Z].*") || !password.matches(".*\\d.*")) {
            showError("密码必须同时包含英文字母和数字");
            return;
        }

        // 确认密码
        if (!password.equals(confirm)) {
            showError("两次输入的密码不一致");
            return;
        }

        errorView.setVisibility(View.GONE);
        registerBtn.setEnabled(false);
        registerBtn.setText("注册中...");

        new Thread(() -> {
            try {
                ApiService api = new ApiService();
                api.register(username, account, password);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        getActivity().setResult(android.app.Activity.RESULT_OK);
                        getActivity().finish();
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        String msg = e.getMessage();
                        if (msg != null && msg.contains("409")) {
                            showError("该账号已被注册");
                        } else {
                            showError("注册失败，请重试");
                        }
                        registerBtn.setEnabled(true);
                        registerBtn.setText("注册");
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
