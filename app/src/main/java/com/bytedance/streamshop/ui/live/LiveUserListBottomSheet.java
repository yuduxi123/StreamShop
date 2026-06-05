package com.bytedance.streamshop.ui.live;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.imageview.ShapeableImageView;

import org.json.JSONArray;
import org.json.JSONObject;

public class LiveUserListBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_USERS_JSON = "users_json";

    private JSONArray users;
    private RecyclerView userList;
    private TextView emptyText;
    private TextView titleText;
    private UserAdapter adapter;

    public static LiveUserListBottomSheet newInstance(JSONArray users) {
        LiveUserListBottomSheet fragment = new LiveUserListBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_USERS_JSON, users != null ? users.toString() : "[]");
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            try {
                users = new JSONArray(getArguments().getString(ARG_USERS_JSON, "[]"));
            } catch (Exception e) {
                users = new JSONArray();
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_live_user_list_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        titleText = view.findViewById(R.id.live_user_list_title);
        userList = view.findViewById(R.id.live_user_list);
        emptyText = view.findViewById(R.id.live_user_empty_text);

        userList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new UserAdapter();
        userList.setAdapter(adapter);

        updateUI();
    }

    private void updateUI() {
        int count = users != null ? users.length() : 0;
        titleText.setText("在线观众 (" + count + ")");
        if (count == 0) {
            emptyText.setVisibility(View.VISIBLE);
            userList.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            userList.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    private class UserAdapter extends RecyclerView.Adapter<UserAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_live_user, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            JSONObject user = users.optJSONObject(position);
            if (user != null) {
                holder.username.setText(user.optString("username", "匿名用户"));
            }
        }

        @Override
        public int getItemCount() { return users != null ? users.length() : 0; }

        class VH extends RecyclerView.ViewHolder {
            ShapeableImageView avatar;
            TextView username;

            VH(View v) {
                super(v);
                avatar = v.findViewById(R.id.live_user_avatar);
                username = v.findViewById(R.id.live_user_username);
            }
        }
    }
}
