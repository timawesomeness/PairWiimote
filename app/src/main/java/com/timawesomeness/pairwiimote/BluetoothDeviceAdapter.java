package com.timawesomeness.pairwiimote;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BluetoothDeviceAdapter extends RecyclerView.Adapter<BluetoothDeviceAdapter.BluetoothDeviceViewHolder> {
    List<BluetoothDevice> data;
    int size;

    public BluetoothDeviceAdapter(List<BluetoothDevice> data) {
        this.data = data;
        size = data.size();
    }

    @NonNull
    @Override
    public BluetoothDeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new BluetoothDeviceViewHolder(
                LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull BluetoothDeviceViewHolder holder, int position) {
        BluetoothDevice device = data.get(position);
        holder.top.setText(device.getName() == null
                ? holder.itemView.getResources().getString(R.string.unnamed_device)
                : device.getName());
        holder.bottom.setText(device.getAddress());
        holder.itemView.setOnClickListener(view -> {
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
            device.createBond();
        });
        if (device.getName() != null && device.getName().contains("Nintendo RVL-")) {
            holder.top.setTextColor(Color.rgb(0, 191, 0));
            holder.top.setTypeface(null, Typeface.BOLD);
        } else {
            holder.top.setTextColor(holder.bottom.getTextColors());
            holder.top.setTypeface(null, Typeface.NORMAL);
        }
    }

    @Override
    public int getItemCount() {
        return size;
    }

    /**
     * Notify the adapter that the data's been reset
     */
    public void notifyDataReset() {
        size = 0;
        notifyDataSetChanged();
    }

    /**
     * Notify the adapter that there's a new item
     */
    public void notifyNewItem() {
        size += 1;
        notifyItemInserted(size == 0 ? 0 : size - 1);
    }

    static class BluetoothDeviceViewHolder extends RecyclerView.ViewHolder {
        TextView top;
        TextView bottom;

        public BluetoothDeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            TypedValue outValue = new TypedValue();
            itemView.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            itemView.setBackgroundResource(outValue.resourceId);
            top = itemView.findViewById(android.R.id.text1);
            bottom = itemView.findViewById(android.R.id.text2);
        }
    }
}
