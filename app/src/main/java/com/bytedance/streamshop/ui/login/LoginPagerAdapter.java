package com.bytedance.streamshop.ui.login;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class LoginPagerAdapter extends FragmentStateAdapter {

    public LoginPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return new LoginFragment();
        } else {
            return new RegisterFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
