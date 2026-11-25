package com.instalarm.app;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;

public class AlarmsAdapter extends RecyclerView.Adapter<AlarmsAdapter.AlarmViewHolder> {

    public interface OnAlarmActionListener {
        void onToggle(AlarmModel alarm, boolean isEnabled);
        void onDelete(AlarmModel alarm, int position);
        void onEdit(AlarmModel alarm);
        void onPreview(AlarmModel alarm);
        void onSkipOnce(AlarmModel alarm);
        void onDuplicate(AlarmModel alarm);
    }

    private final Context context;
    private final List<AlarmModel> alarms;
    private final OnAlarmActionListener listener;

    private static final int COLOR_ACTIVE_DAY = Color.parseColor("#00A8FF");
    private static final int COLOR_INACTIVE_DAY = Color.parseColor("#4A5568");

    public AlarmsAdapter(Context context, List<AlarmModel> alarms, OnAlarmActionListener listener) {
        this.context = context;
        this.alarms = alarms;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AlarmViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_alarm, parent, false);
        return new AlarmViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlarmViewHolder holder, int position) {
        AlarmModel alarm = alarms.get(position);

        holder.tvAlarmTime.setText(alarm.getFormattedTime());
        holder.tvAlarmAmPm.setText(alarm.getAmPm());

        // Update S M T W T F S day indicators
        String days = alarm.getDays() != null ? alarm.getDays() : "Once";
        boolean isDaily = "Daily".equalsIgnoreCase(days);
        boolean isOnce = "Once".equalsIgnoreCase(days);

        holder.cardDaySun.setTextColor(isDaily || days.contains("Sun") ? COLOR_ACTIVE_DAY : COLOR_INACTIVE_DAY);
        holder.cardDayMon.setTextColor(isDaily || days.contains("Mon") ? COLOR_ACTIVE_DAY : COLOR_INACTIVE_DAY);
        holder.cardDayTue.setTextColor(isDaily || days.contains("Tue") ? COLOR_ACTIVE_DAY : COLOR_INACTIVE_DAY);
        holder.cardDayWed.setTextColor(isDaily || days.contains("Wed") ? COLOR_ACTIVE_DAY : COLOR_INACTIVE_DAY);
        holder.cardDayThu.setTextColor(isDaily || days.contains("Thu") ? COLOR_ACTIVE_DAY : COLOR_INACTIVE_DAY);
        holder.cardDayFri.setTextColor(isDaily || days.contains("Fri") ? COLOR_ACTIVE_DAY : COLOR_INACTIVE_DAY);
        holder.cardDaySat.setTextColor(isDaily || days.contains("Sat") ? COLOR_ACTIVE_DAY : COLOR_INACTIVE_DAY);

        // Mission icon badge
        String task = alarm.getDismissTask();
        if (task != null && task.startsWith("reels")) {
            holder.tvMissionBadgeIcon.setText(" 📸");
        } else if ("math".equalsIgnoreCase(task)) {
            holder.tvMissionBadgeIcon.setText(" 🧮");
        } else if ("shake".equalsIgnoreCase(task)) {
            holder.tvMissionBadgeIcon.setText(" 📳");
        } else {
            holder.tvMissionBadgeIcon.setText("");
        }

        // Subtitle (Clean Label only, Alarmy Image 1 style)
        String label = alarm.getLabel();
        if (label != null && !label.trim().isEmpty()) {
            holder.tvAlarmSubtitle.setText("🐥 " + label.trim());
        } else {
            holder.tvAlarmSubtitle.setText("🐥 Wake up early");
        }

        // Alpha feedback for disabled alarms
        float alpha = alarm.isEnabled() ? 1.0f : 0.40f;
        holder.itemView.setAlpha(alpha);

        holder.switchAlarmEnabled.setOnCheckedChangeListener(null);
        holder.switchAlarmEnabled.setChecked(alarm.isEnabled());

        holder.switchAlarmEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            alarm.setEnabled(isChecked);
            if (listener != null) {
                listener.onToggle(alarm, isChecked);
            }
        });

        // 3-dots Card Menu (Delete / Preview / Skip once / Duplicate - Image 2)
        holder.btnCardMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, holder.btnCardMenu);
            popup.getMenu().add(0, 1, 0, "Delete");
            popup.getMenu().add(0, 2, 1, "Preview alarm");
            popup.getMenu().add(0, 3, 2, "Skip once");
            popup.getMenu().add(0, 4, 3, "Duplicate alarm");
            popup.setOnMenuItemClickListener(item -> {
                if (listener == null) return false;
                switch (item.getItemId()) {
                    case 1:
                        listener.onDelete(alarm, holder.getAdapterPosition());
                        return true;
                    case 2:
                        listener.onPreview(alarm);
                        return true;
                    case 3:
                        listener.onSkipOnce(alarm);
                        return true;
                    case 4:
                        listener.onDuplicate(alarm);
                        return true;
                }
                return false;
            });
            popup.show();
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEdit(alarm);
            }
        });
    }

    @Override
    public int getItemCount() {
        return alarms.size();
    }

    static class AlarmViewHolder extends RecyclerView.ViewHolder {
        TextView tvAlarmTime, tvAlarmAmPm, tvAlarmSubtitle, tvMissionBadgeIcon;
        TextView cardDaySun, cardDayMon, cardDayTue, cardDayWed, cardDayThu, cardDayFri, cardDaySat;
        SwitchMaterial switchAlarmEnabled;
        ImageButton btnCardMenu;

        public AlarmViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAlarmTime = itemView.findViewById(R.id.tvAlarmTime);
            tvAlarmAmPm = itemView.findViewById(R.id.tvAlarmAmPm);
            tvAlarmSubtitle = itemView.findViewById(R.id.tvAlarmSubtitle);
            tvMissionBadgeIcon = itemView.findViewById(R.id.tvMissionBadgeIcon);

            cardDaySun = itemView.findViewById(R.id.cardDaySun);
            cardDayMon = itemView.findViewById(R.id.cardDayMon);
            cardDayTue = itemView.findViewById(R.id.cardDayTue);
            cardDayWed = itemView.findViewById(R.id.cardDayWed);
            cardDayThu = itemView.findViewById(R.id.cardDayThu);
            cardDayFri = itemView.findViewById(R.id.cardDayFri);
            cardDaySat = itemView.findViewById(R.id.cardDaySat);

            switchAlarmEnabled = itemView.findViewById(R.id.switchAlarmEnabled);
            btnCardMenu = itemView.findViewById(R.id.btnCardMenu);
        }
    }
}
