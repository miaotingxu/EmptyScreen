package com.haier.emptyscreen.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.haier.emptyscreen.R;
import com.haier.emptyscreen.utils.StorageUtils;

import java.util.ArrayList;
import java.util.List;

public class StorageDeviceAdapter extends RecyclerView.Adapter<StorageDeviceAdapter.ViewHolder> {

    private final Context mContext;
    private final List<StorageUtils.StorageDevice> mDevices;
    private OnDeviceClickListener mOnDeviceClickListener;
    private int mSelectedPosition = -1;

    public interface OnDeviceClickListener {
        void onDeviceClick(StorageUtils.StorageDevice device, int position);
    }

    public StorageDeviceAdapter(Context context) {
        mContext = context;
        mDevices = new ArrayList<>();
    }

    public void setDevices(List<StorageUtils.StorageDevice> devices) {
        mDevices.clear();
        if (devices != null) {
            mDevices.addAll(devices);
        }
        mSelectedPosition = -1;
        notifyDataSetChanged();
    }

    public void setOnDeviceClickListener(OnDeviceClickListener listener) {
        mOnDeviceClickListener = listener;
    }

    public void setSelectedPosition(int position) {
        int oldPosition = mSelectedPosition;
        mSelectedPosition = position;
        if (oldPosition >= 0 && oldPosition < mDevices.size()) {
            notifyItemChanged(oldPosition);
        }
        if (position >= 0 && position < mDevices.size()) {
            notifyItemChanged(position);
        }
    }

    public int getSelectedPosition() {
        return mSelectedPosition;
    }

    public StorageUtils.StorageDevice getDevice(int position) {
        if (position >= 0 && position < mDevices.size()) {
            return mDevices.get(position);
        }
        return null;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.item_storage_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StorageUtils.StorageDevice device = mDevices.get(position);
        
        holder.tvDeviceName.setText(device.name);
        holder.tvDeviceType.setText(device.isRemovable ? "外部存储" : "内置存储");
        holder.tvAvailableSpace.setText("可用: " + device.getFormattedAvailableSpace(mContext));
        holder.tvTotalSpace.setText("共: " + device.getFormattedTotalSpace(mContext));
        
        int progress = (int) device.getUsagePercent();
        holder.progressBar.setProgress(progress);
        holder.tvUsagePercent.setText(progress + "%");
        
        if (position == mSelectedPosition) {
            holder.itemView.setBackgroundColor(0x404CAF50);
        } else {
            holder.itemView.setBackgroundColor(0x00000000);
        }
        
        holder.itemView.setOnClickListener(v -> {
            if (mOnDeviceClickListener != null) {
                mOnDeviceClickListener.onDeviceClick(device, holder.getAdapterPosition());
            }
        });
        
        holder.itemView.setFocusable(true);
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                holder.itemView.setBackgroundColor(0x20FFFFFF);
            } else if (position == mSelectedPosition) {
                holder.itemView.setBackgroundColor(0x404CAF50);
            } else {
                holder.itemView.setBackgroundColor(0x00000000);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mDevices.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDeviceName;
        TextView tvDeviceType;
        TextView tvAvailableSpace;
        TextView tvTotalSpace;
        ProgressBar progressBar;
        TextView tvUsagePercent;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDeviceName = itemView.findViewById(R.id.tv_device_name);
            tvDeviceType = itemView.findViewById(R.id.tv_device_type);
            tvAvailableSpace = itemView.findViewById(R.id.tv_available_space);
            tvTotalSpace = itemView.findViewById(R.id.tv_total_space);
            progressBar = itemView.findViewById(R.id.progress_bar);
            tvUsagePercent = itemView.findViewById(R.id.tv_usage_percent);
        }
    }
}
