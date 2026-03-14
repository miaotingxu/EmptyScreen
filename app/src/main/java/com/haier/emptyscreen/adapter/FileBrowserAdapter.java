package com.haier.emptyscreen.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.haier.emptyscreen.R;
import com.haier.emptyscreen.model.VideoFile;

import java.util.ArrayList;
import java.util.List;

public class FileBrowserAdapter extends RecyclerView.Adapter<FileBrowserAdapter.ViewHolder> {

    private final Context mContext;
    private final List<VideoFile> mItems;
    private OnItemClickListener mOnItemClickListener;
    private int mSelectedPosition = -1;

    public interface OnItemClickListener {
        void onItemClick(VideoFile item, int position);
        boolean onItemLongClick(VideoFile item, int position);
    }

    public FileBrowserAdapter(Context context) {
        mContext = context;
        mItems = new ArrayList<>();
    }

    public void setItems(List<VideoFile> items) {
        mItems.clear();
        if (items != null) {
            mItems.addAll(items);
        }
        mSelectedPosition = -1;
        notifyDataSetChanged();
    }

    public void clearItems() {
        mItems.clear();
        mSelectedPosition = -1;
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        mOnItemClickListener = listener;
    }

    public void setSelectedPosition(int position) {
        int oldPosition = mSelectedPosition;
        mSelectedPosition = position;
        if (oldPosition >= 0 && oldPosition < mItems.size()) {
            notifyItemChanged(oldPosition);
        }
        if (position >= 0 && position < mItems.size()) {
            notifyItemChanged(position);
        }
    }

    public int getSelectedPosition() {
        return mSelectedPosition;
    }

    public VideoFile getItem(int position) {
        if (position >= 0 && position < mItems.size()) {
            return mItems.get(position);
        }
        return null;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.item_file_browser, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VideoFile item = mItems.get(position);
        
        holder.tvFileName.setText(item.getName());
        
        if (item.isDirectory()) {
            holder.ivIcon.setImageResource(R.drawable.ic_folder);
            holder.tvFileSize.setText("");
            holder.tvFileDate.setText("");
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_video);
            holder.tvFileSize.setText(item.getFormattedSize());
            holder.tvFileDate.setText(item.getFormattedDate());
        }
        
        if (position == mSelectedPosition) {
            holder.itemView.setBackgroundColor(0x404CAF50);
        } else {
            holder.itemView.setBackgroundColor(0x00000000);
        }
        
        holder.itemView.setOnClickListener(v -> {
            if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(item, holder.getAdapterPosition());
            }
        });
        
        holder.itemView.setOnLongClickListener(v -> {
            if (mOnItemClickListener != null) {
                return mOnItemClickListener.onItemLongClick(item, holder.getAdapterPosition());
            }
            return false;
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
        return mItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvFileName;
        TextView tvFileSize;
        TextView tvFileDate;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvFileSize = itemView.findViewById(R.id.tv_file_size);
            tvFileDate = itemView.findViewById(R.id.tv_file_date);
        }
    }
}
