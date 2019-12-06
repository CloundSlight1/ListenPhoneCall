package com.wuyz.listenphonecall;

import android.app.ProgressDialog;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Handler;
import android.provider.CallLog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class RecordAdapter extends BaseAdapter {

    private static final String TAG = "RecordAdapter";
    private static final Comparator<String> COMPARATOR = new Comparator<String>() {
        @Override
        public int compare(String lhs, String rhs) {
            return rhs.substring(rhs.lastIndexOf('_') + 1).compareTo(lhs.substring(lhs.lastIndexOf('_') + 1));
        }
    };

    private ArrayList<String> baseItems;
    private ArrayList<String> items;
    private String nameFilter;
    private LayoutInflater inflater;
    private Context context;
    private ProgressDialog progressDialog;
    private Handler handler = new Handler();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-M-d", Locale.getDefault());

    public RecordAdapter(Context context) {
        this.context = context;
        inflater = LayoutInflater.from(context);
    }

    public void fileChanged() {
        baseItems = getRecords(Utils.getRecordPath(context));
        setItems();
        notifyDataSetChanged();
    }

    public void setNameFilter(String nameFilter) {
        this.nameFilter = nameFilter;
        setItems();
        notifyDataSetChanged();
    }

    private void setItems() {
        if (nameFilter == null || nameFilter.isEmpty())
            items = baseItems;
        else {
            items = new ArrayList<>();
            for (String file : baseItems) {
                String name = file.substring(file.lastIndexOf('/'), file.lastIndexOf('.'));
                if (name.contains(nameFilter))
                    items.add(file);
            }
        }
        Log2.d(TAG, "setItems: %d", (items == null) ? 0 : items.size());
    }

    @Override
    public int getCount() {
        return (items == null) ? 0 : items.size();
    }

    @Override
    public String getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.record_item2, parent, false);
            holder = new ViewHolder();
            holder.type = (ImageView) convertView.findViewById(R.id.call_type);
            holder.name = (TextView) convertView.findViewById(R.id.call_name);
            holder.number = (TextView) convertView.findViewById(R.id.call_number);
            holder.size = (TextView) convertView.findViewById(R.id.call_size);
            holder.date = (TextView) convertView.findViewById(R.id.call_date);
            holder.delete = (ImageView) convertView.findViewById(R.id.delete);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        String path = items.get(position);
        final File file = new File(path);
        if (file.isFile()) {
            // lxx_551_0_12215111.amr
            String name = file.getName().substring(0, file.getName().lastIndexOf('.'));
            String[] arr = name.split("_");
            if (arr.length == 4) {
                holder.name.setText(arr[0]);
                holder.number.setText(arr[1]);
                if (Integer.parseInt(arr[2]) == CallLog.Calls.INCOMING_TYPE)
                    holder.type.setImageResource(R.drawable.call_in);
                else
                    holder.type.setImageResource(R.drawable.call_out);
                holder.size.setText((file.length() / 1024) + "k");
                holder.date.setText(dateFormat.format(new Date(Long.parseLong(arr[3]))));
            } else {
                holder.name.setText(name);
                holder.number.setText(null);
                holder.size.setText(null);
                holder.date.setText(null);
            }
        } else {
            holder.name.setText(path);
            holder.number.setText(null);
            holder.size.setText(null);
            holder.date.setText(null);
        }
        holder.delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (file.delete()) {
                    Log2.d(TAG, "delete file: %s", file.getPath());
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                    }
                    progressDialog = new ProgressDialog(context);
                    progressDialog.setIndeterminate(true);
                    progressDialog.show();
                    MediaScannerConnection.scanFile(context, new String[]{Utils.getRecordPath(context)}, null, scanCallback);
                }
            }
        });
        return convertView;
    }

    private final MediaScannerConnection.MediaScannerConnectionClient scanCallback = new MediaScannerConnection.MediaScannerConnectionClient() {
        @Override
        public void onMediaScannerConnected() {

        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
            Log2.d(TAG, "onScanCompleted: %s", path);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    fileChanged();
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                        progressDialog = null;
                    }
                }
            });
        }
    };

    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    public void query(String query) {
        if (query != null && !query.equals(nameFilter)) {
            setNameFilter(query);
        }
    }

    static class ViewHolder {
        ImageView type;
        TextView name;
        TextView number;
        TextView size;
        TextView date;
        ImageView delete;
    }

    private static ArrayList<String> getRecords(String path) {
        if (path == null || path.isEmpty())
            return null;
        File parent = new File(path);
        if (!parent.isDirectory())
            return null;
        String[] files = parent.list();
        if (files == null || files.length == 0)
            return null;
        ArrayList<String> list = new ArrayList<>(files.length);
        for (String name : files) {
            if (".".equals(name) || "..".equals(name) || !name.endsWith(".amr"))
                break;
            String fullPath = path + "/" + name;
            list.add(fullPath);
//			Log2.d(TAG, "getRecords: %s", fullPath);
        }
        Collections.sort(list, COMPARATOR);
        return list;
    }
}