package com.wuyz.listenphonecall;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;


public class MainActivity extends Activity {
    private final static String TAG = "MainActivity";

    private SharedPreferences mPreferences;
    private CheckBox mEnableCheck;
    private ListView mRecordList;
    private RecordAdapter mAdapter;
    private TextView mEmptyText;

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

        mEnableCheck = findViewById(R.id.open_check);
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

        mEmptyText = findViewById(R.id.empty_text);
        mRecordList = findViewById(R.id.record_list);

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
            mAdapter.onDestroy();
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
                mAdapter.query(newText);
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
}
