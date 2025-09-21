package com.svvaap.quizastra;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BattleUsersAdapter extends RecyclerView.Adapter<BattleUsersAdapter.VH> {

    public interface OnChallengeClick {
        void onChallenge(User user);
    }

    private final List<User> users;
    private final OnChallengeClick callback;

    public BattleUsersAdapter(@NonNull List<User> users, @NonNull OnChallengeClick callback) {
        this.users = users;
        this.callback = callback;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_battle_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        User u = users.get(position);
        h.name.setText(u.getName());
        h.subtitle.setText("Online");
        h.btn.setOnClickListener(v -> callback.onChallenge(u));

        boolean set = false;
        // 1) Try Base64 photo
        String b64 = u.getPhotoBase64();
        if (b64 != null && !b64.trim().isEmpty()) {
            try {
                String content = b64;
                if (content.startsWith("data:image")) {
                    int idx = content.indexOf(',');
                    if (idx != -1) content = content.substring(idx + 1);
                }
                byte[] bytes = Base64.decode(content, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp != null) {
                    h.avatar.setImageBitmap(bmp);
                    set = true;
                }
            } catch (Exception ignored) {}
        }

        // 2) Try URI photo (if any)
        if (!set) {
            String uriStr = u.getPhotoUri();
            if (uriStr != null && !uriStr.trim().isEmpty()) {
                try {
                    h.avatar.setImageURI(Uri.parse(uriStr));
                    set = true;
                } catch (Exception ignored) {}
            }
        }

        // 3) Try avatar resource id
        if (!set && u.getAvatarResId() != null && u.getAvatarResId() != 0) {
            try {
                h.avatar.setImageResource(u.getAvatarResId());
                set = true;
            } catch (Exception ignored) {}
        }

        // 4) Fallback to default
        if (!set) {
            h.avatar.setImageResource(R.drawable.avatar_default);
        }
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView avatar; TextView name; TextView subtitle; Button btn;
        VH(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.imgAvatar);
            name = itemView.findViewById(R.id.textUserName);
            subtitle = itemView.findViewById(R.id.textSubtitle);
            btn = itemView.findViewById(R.id.btnChallenge);
        }
    }
}
