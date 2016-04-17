package com.wuyz.listenphonecall;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends Activity {
    private final static String TAG = "MainActivity";

    private SharedPreferences mPreferences;
    private CheckBox mEnableCheck;
    private ListView mRecordList;
    private RecordAdapter mAdapter;
    private TextView mEmptyText;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-M-d", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean enable = mPreferences.getBoolean("enable", true);
        Log2.d(TAG, "onCreate enable %b", enable);
        final Intent serviceIntent = new Intent(this, RecordService.class);
        if (enable)
            startService(serviceIntent);
        else
            stopService(serviceIntent);

        mEnableCheck = (CheckBox) findViewById(R.id.open_check);
        mEnableCheck.setChecked(enable);
        mEnableCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPreferences.edit().putBoolean("enable", isChecked).apply();
                if (isChecked) {
                    startService(serviceIntent);
                    Toast.makeText(MainActivity.this, R.string.enable_service_message, Toast.LENGTH_SHORT).show();
                } else {
                    stopService(serviceIntent);
                    Toast.makeText(MainActivity.this, R.string.disable_service_message, Toast.LENGTH_SHORT).show();
                }

                Cursor cursor = getContentResolver().query(RecordProvider.CONTENT_URI, null, null, null, null);
                Log2.d(TAG, "onCheckedChanged cursor[%s]", cursor == null ? "null" : cursor.getCount());
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        Log2.d(TAG, "onCheckedChanged id[%d]", cursor.getLong(0));
                    }
                    cursor.close();
                }
                Log2.d(TAG, "onCheckedChanged end");
            }
        });

        mEmptyText = (TextView) findViewById(R.id.empty_text);
        mRecordList = (ListView) findViewById(R.id.record_list);

        mAdapter = new RecordAdapter(this);
        mAdapter.fileChanged();
        mAdapter.setNameFilter(null);
        mRecordList.setAdapter(mAdapter);
        mRecordList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File file = new File(mAdapter.getItem(position));
                if (file.exists() && file.isFile()) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setDataAndType(Uri.fromFile(file), "audio/*");
                    startActivity(intent);
                }
            }
        });
        mRecordList.setEmptyView(mEmptyText);
    }

    @Override
    protected void onDestroy() {
        if (mAdapter != null) {
            mAdapter.handler.removeCallbacksAndMessages(null);
            if (mAdapter.progressDialog != null) {
                mAdapter.progressDialog.dismiss();
                mAdapter.progressDialog = null;
            }
            mAdapter = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        initSearchView(menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void initSearchView(Menu menu) {
        final SearchView searchView = (SearchView) menu.findItem(R.id.search).getActionView();
        searchView.setIconifiedByDefault(true);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log2.d(TAG, "onQueryTextSubmit: %s", query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log2.d(TAG, "onQueryTextChange: %s", newText);
                if (newText != null && !newText.equals(mAdapter.nameFilter)) {
                    mAdapter.setNameFilter(newText);
                }
                return true;
            }
        });
        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                String s = (String) searchView.getQuery();
                if (s != null && s.length() > 0)
                    searchView.setQuery(null, true);
                return true;
            }
        });
    }

    private static class RecordAdapter extends BaseAdapter {

        private ArrayList<String> baseItems;
        private ArrayList<String> items;
        private String nameFilter;
        private LayoutInflater inflater;
        private Context context;
        private ProgressDialog progressDialog;
        private Handler handler = new Handler();

        public RecordAdapter(Context context) {
            this.context = context;
            inflater = LayoutInflater.from(context);
        }

        public void fileChanged() {
            baseItems = getRecords(Utils.getRecordPath());
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
                        if (progressDialog != null){
                            progressDialog.dismiss();
                        }
                        progressDialog = new ProgressDialog(context);
                        progressDialog.setIndeterminate(true);
                        progressDialog.show();
                        MediaScannerConnection.scanFile(context, new String[]{Utils.getRecordPath()}, null, scanCallback);
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
    }

    private static class ViewHolder {
        ImageView type;
        TextView name;
        TextView number;
        TextView size;
        TextView date;
        ImageView delete;
    }

    public static ArrayList<String> getRecords(String path) {
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
            if (name.equals(".") || name.equals("..") || !name.endsWith(".amr"))
                break;
            String fullPath = path + "/" + name;
            list.add(fullPath);
//			Log2.d(TAG, "getRecords: %s", fullPath);
        }
        Collections.sort(list, comparator);
        return list;
    }

    private static final Comparator<String> comparator = new Comparator<String>() {
        @Override
        public int compare(String lhs, String rhs) {
            return rhs.substring(rhs.lastIndexOf('_') + 1).compareTo(lhs.substring(lhs.lastIndexOf('_') + 1));
        }
    };
}
