package svvaap.quizastra;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

    private final List<FirebaseDatabaseHelper.LeaderboardEntry> entries;

    public LeaderboardAdapter(List<FirebaseDatabaseHelper.LeaderboardEntry> entries) {
        this.entries = entries;
    }

    @NonNull
    @Override
    public LeaderboardAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LeaderboardAdapter.ViewHolder holder, int position) {
        FirebaseDatabaseHelper.LeaderboardEntry e = entries.get(position);
        int rank = position + 4; // since top 3 are header
        holder.username.setText(e.name);
        holder.textRank.setText(String.valueOf(rank));
        holder.textSubtitle.setText("Rank #" + rank);
        holder.textScore.setText(e.points + " pts");
        // Load avatar if provided as Base64
        boolean set = false;
        try {
            if (e.photoBase64 != null && !e.photoBase64.isEmpty()) {
                String b64 = e.photoBase64;
                if (b64.startsWith("data:image")) {
                    int idx = b64.indexOf(",");
                    if (idx != -1) b64 = b64.substring(idx + 1);
                }
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp != null) {
                    holder.userAvatar.setImageBitmap(bmp);
                    set = true;
                }
            }
        } catch (Exception ignored) {}
        if (!set) holder.userAvatar.setImageResource(R.drawable.avatar_default);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView username, textRank, textSubtitle, textScore;
        ImageView userAvatar;

        public ViewHolder(View itemView) {
            super(itemView);
            username = itemView.findViewById(R.id.username);
            userAvatar = itemView.findViewById(R.id.userAvatar);
            textRank = itemView.findViewById(R.id.textRank);
            textSubtitle = itemView.findViewById(R.id.textSubtitle);
            textScore = itemView.findViewById(R.id.textScore);
        }
    }
}
