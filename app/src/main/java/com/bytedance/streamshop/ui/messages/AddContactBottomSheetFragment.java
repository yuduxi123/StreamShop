package com.bytedance.streamshop.ui.messages;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.bytedance.streamshop.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class AddContactBottomSheetFragment extends BottomSheetDialogFragment {

    public static AddContactBottomSheetFragment newInstance() {
        return new AddContactBottomSheetFragment();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_contact_sheet, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.add_friend_row).setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), AddFriendSearchActivity.class));
            dismiss();
        });

        view.findViewById(R.id.create_group_row).setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), CreateGroupActivity.class));
            dismiss();
        });
    }
}
