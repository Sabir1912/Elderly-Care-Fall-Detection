package com.example.elderlycare.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.elderlycare.R;
import com.example.elderlycare.models.HospitalVisit;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HospitalVisitAdapter extends RecyclerView.Adapter<HospitalVisitAdapter.VisitViewHolder> {

    private Context context;
    private List<HospitalVisit> visitList;

    public HospitalVisitAdapter(Context context, List<HospitalVisit> visitList) {
        this.context = context;
        this.visitList = visitList;
    }

    public void setVisits(List<HospitalVisit> visitList) {
        this.visitList = visitList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VisitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_hospital_visit, parent, false);
        return new VisitViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VisitViewHolder holder, int position) {
        HospitalVisit visit = visitList.get(position);

        Date date = new Date(visit.getTimestamp());
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd", Locale.getDefault());
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMM", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        holder.tvDateDay.setText(dayFormat.format(date));
        holder.tvDateMonth.setText(monthFormat.format(date));
        
        holder.tvVisitReason.setText(visit.getDescription());
        holder.tvVisitTime.setText("Recorded at " + timeFormat.format(date));

        // For this prototype, let's randomly show the Prescription badge or based on a keyword
        if (visit.getDescription().toLowerCase().contains("prescription") || visit.getDescription().toLowerCase().contains("rx")) {
            holder.badgePrescription.setVisibility(View.VISIBLE);
        } else {
            holder.badgePrescription.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return visitList.size();
    }

    public static class VisitViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateDay, tvDateMonth, tvVisitReason, tvVisitTime;
        View badgePrescription;

        public VisitViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDateDay = itemView.findViewById(R.id.tvDateDay);
            tvDateMonth = itemView.findViewById(R.id.tvDateMonth);
            tvVisitReason = itemView.findViewById(R.id.tvVisitReason);
            tvVisitTime = itemView.findViewById(R.id.tvVisitTime);
            badgePrescription = itemView.findViewById(R.id.badgePrescription);
        }
    }
}
