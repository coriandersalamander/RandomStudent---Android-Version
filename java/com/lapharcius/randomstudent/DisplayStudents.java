package com.lapharcius.randomstudent;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.CardView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import static android.widget.Adapter.NO_SELECTION;
import static com.lapharcius.randomstudent.FlingDetector.flingDirection.DIRECTION_LEFT;
import static com.lapharcius.randomstudent.FlingDetector.flingDirection.DIRECTION_RIGHT;
import static java.lang.Thread.sleep;

public class DisplayStudents extends Activity implements FlingDetector.OnGestureDetected
{

    String spreadsheetId;
    TextView mOutputText;
    boolean databaseUpdated = true;
    boolean spinning = false;
    CardView c;
    ListView l;

    static int savedSpinnerPosition;
    static int savedListPosition;
    private static final String[] SCOPES = {
            DriveScopes.DRIVE_METADATA_READONLY,
            SheetsScopes.SPREADSHEETS_READONLY
    };
    private static final String PREF_ACCOUNT_NAME = "accountName";

    static Vector<String> periods;
    static Map<String, String> gridIds;

    static List<String> savedOutput;
    ListView savedListView;
//    GestureDetector g;
    FlingDetector g;
    GoogleAccountCredential mCredential;
    studentsDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        database = studentsDatabase.getInstance(this);
        database.createDB();

        if (getResources().getConfiguration().screenWidthDp > getResources().getConfiguration().screenHeightDp) {
            setContentView(R.layout.activity_display_students_landscape);
        } else {
            setContentView(R.layout.activity_display_students_portrait);
        }
        periods = new Vector<>(0);
        gridIds = new HashMap<>(5);

        g = new FlingDetector(getApplicationContext());

        Intent i = getIntent();

        if ((Intent.ACTION_MAIN.equals(i.getAction()) &&
            studentsDatabase.getInstance(getApplicationContext()).countItems() == 0))
        {
            Intent setupScreen = new Intent(getApplicationContext(), SetupScreen.class);
            startActivity(setupScreen);
        }
        else {
            if (i.hasExtra("SpreadsheetId")) {
                spreadsheetId = i.getStringExtra("SpreadsheetId");
                Log.i("LOGMESSAGE", "Spreadsheet id == " + spreadsheetId);
            }
            if (i.hasExtra("fileName")) {
                setTitle(i.getStringExtra("fileName"));
            } else {
                setTitle(getResources().getString(R.string.app_name));
            }
            mCredential = GoogleAccountCredential.usingOAuth2(
                    getApplicationContext(), Arrays.asList(SCOPES))
                    .setBackOff(new ExponentialBackOff());

            if (i.hasExtra("loginName")) {
                mCredential.setSelectedAccountName(i.getStringExtra("loginName"));
            } else {
                String accountName = getPreferences(Context.MODE_PRIVATE)
                        .getString(PREF_ACCOUNT_NAME, null);

                mCredential.setSelectedAccountName(accountName);
            }

            mOutputText = (TextView) findViewById(R.id.outputText);
            l = (ListView) findViewById(R.id.myListView);
            //            l.setSelector(android.R.drawable.list_selector_background);
            l.setSelector(android.R.drawable.star_big_on);

            c = (CardView) findViewById(R.id.myCardView);

            ViewGroup vg = (ViewGroup) l.getParent();
            vg.removeView(l);

            c.addView(l);
            c.setRadius((float) 500.0);

            setupFloatingButton();


            if (savedInstanceState != null) {
                Log.i("LOGMESSAGE", String.valueOf(savedSpinnerPosition));
                ((Spinner) findViewById(R.id.spinner)).setSelection(savedSpinnerPosition);
                new MakeDatabaseRequestTask(savedSpinnerPosition).execute();
                ((ListView) findViewById(R.id.myListView)).setSelection(savedListPosition);
                ((ListView) findViewById(R.id.myListView)).addHeaderView(new View(this));
                ((ListView) findViewById(R.id.myListView)).addHeaderView(new View(this));
                ((ListView) findViewById(R.id.myListView)).addHeaderView(new View(this));
            }
            else if (!Intent.ACTION_MAIN.equals(i.getAction()))
            {
                // This delete/create method calls seem correct to me. Why does this not work?
                // Experiment: Try with "savedInstanceState == null" check.
//                        studentsDatabase.getInstance(this).deleteDatabase("students.db");
//                        studentsDatabase.getInstance(this).createDB();
                populateDatabase();
            }
            else
            {
                queryDatabase();
        /*                View headerView = getLayoutInflater().inflate(android.R.layout.simple_list_item_1,null);
                        l.addHeaderView(headerView);
                        l.addHeaderView(headerView);

                        l.addFooterView(headerView);
                        l.addFooterView(headerView);
        */
            }
        }
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        Log.i("LOGMESSAGE", "In onOptionsMenuClosed");
        super.onOptionsMenuClosed(menu);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        getLayoutInflater().setFactory(null);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_menu, menu);
        if (getLayoutInflater().getFactory() == null) {
            getLayoutInflater().setFactory(new LayoutInflater.Factory() {
                @Override
                public View onCreateView(String name, Context context, AttributeSet attrs) {
                    try {
                        try {
                            final LayoutInflater menuItem = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                            if (menuItem != null)
                            {
                                final View menuView = menuItem.createView(name, null, attrs);
                                new Handler().post(new Runnable() {
                                    public void run() {
                                        try {
                                            menuView.setBackgroundColor((Color.GRAY));
//                                        SpannableString s = new SpannableString(getResources().getString(R.string.set_up_new_roster));
//                                        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, s.length(), 0);
//                                        menuItem.set
//                                        menuView.setBackgroundResource(android.R.drawable.star_big_on);
                                        } catch (Exception e) {
                                            Log.i("LOGMESSAGE", "Error Setting Text Color: " + e.getMessage());
                                        }
                                    }
                                });
                                return menuView;
                            }
                        } catch (InflateException e) {
                            Log.i("LOGMESSAGE", "View inflater error: " + e.getMessage());
                        }
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            });
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i("LOGMESSAGE", "In onOptionsItemSelected!!");
//        if (item.get)
        Toast.makeText(getApplicationContext(), "Swipe for setup screen", Toast.LENGTH_LONG).show();
        Intent i = new Intent(this, SetupScreen.class);
        startActivity(i);
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        savedSpinnerPosition = ((Spinner) findViewById(R.id.spinner)).getSelectedItemPosition();
        savedListPosition = ((ListView) findViewById(R.id.myListView)).getSelectedItemPosition();

    }

    @Override
    protected void onPause()
    {
        super.onPause();
        savedListView = l;

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return g.onTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        // This "moveTaskToBack" logic might be a bug. I don't like to modify Google/Android's
        // default behavior, but for reasons I don't understand, Android calls OnDestroy when the
        // back button is pressed. For every activity other than the main screen, I can understand
        // this mentality. However, from the main screen, the back button should stick the app into
        // the background.
        moveTaskToBack(false);

    }

    void setupFloatingButton() {
//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.refreshDB);

        fab.setImageResource(R.mipmap.goicon);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FloatingActionButton f = (FloatingActionButton) findViewById(R.id.refreshDB);

                CountDownTimer countDown = new CountDownTimer((long) 10000, 100) {

                    @Override
                    public void onTick(long millisUntilFinished) {
                        if (spinning) {
                            int position = (int) (Math.random() * 100 % (l.getCount() - 1));
//                            int position = l.getCount() - 1;
                            l.smoothScrollToPosition(position);
                            l.setSelection(position);
                            l.setItemChecked(position, true);
//                            l.setPointerIcon();
                        }
                        Log.i("LOGMESSAGE", "OnTick");
                    }

                    @Override
                    public void onFinish() {
                        Log.i("LOGMESSAGE", "OnFinish");
                        spinning = false;
                        FloatingActionButton f = (FloatingActionButton) findViewById(R.id.refreshDB);
                        f.setImageResource(R.mipmap.goicon);
                    }
                };

                spinning = !spinning;
                if (!spinning) {
//                    c.setContentPadding(0,0, 0, 0);
                    f.setImageResource(R.mipmap.goicon);
                    countDown.cancel();
//                    l.setFocusableInTouchMode(true);
                    savedListPosition = l.getSelectedItemPosition();
                    l.setSelection(savedListPosition);
                    l.setItemChecked(savedListPosition, true);
//                    l.setFilterText("JOJOJOJOJO");
                    Log.i("LOGMESSAGE", "Stopped!");

                } else {
                    c.setContentPadding(0,200, 0, 200);
                    f.setImageResource(R.mipmap.stopicon);
                    countDown.start();
                    Log.i("LOGMESSAGE", "Started!");

                }
            }
        });
    }

    private void populateDatabase() {
        databaseUpdated = false;
        new MakeSheetsRequestTask(mCredential).execute();
    }

    private void queryDatabase() {
        new MakeDatabaseRequestTask(-1).execute();
    }

    @Override
    public boolean onGestureDetected() {
        if ((FlingDetector.currentFlingDirection == DIRECTION_LEFT) ||
            (FlingDetector.currentFlingDirection == DIRECTION_RIGHT))
        {
            Intent i = new Intent(getApplicationContext(), SetupScreen.class);
            startActivity(i);
        }
        return false;
    }


    private class MakeDatabaseRequestTask extends AsyncTask<Void, Void, List<String>>
    {

        String period;
        MakeDatabaseRequestTask(int aPeriod) {
            super();
            while (!databaseUpdated)
            {
                try
                {
                    sleep(5000);
                    Log.i("LOGMESSAGE", "Waiting for Sheets Request to Finish");
                } catch (InterruptedException e)
                {
                    Log.i("LOGMESSAGE", "MakeDatabaseRequestTask Interrupted!");
                }
            }

            if (periods.size() != 0)

            {
                periods.clear();
            }

            if (gridIds.size() != 0)
            {
                gridIds.clear();
            }

            Cursor periodEntries = studentsDatabase.getInstance(getApplicationContext()).getPeriodEntries();
            periodEntries.moveToFirst();
            do {
                periods.add(periodEntries.getString(0));
            } while (periodEntries.moveToNext());
            if (aPeriod == -1)
            {
                period = "All";
            }
            else
            {
                period = periods.elementAt(aPeriod);
            }
        }

        @Override
        protected List<String> doInBackground(Void... voids) {
            List<String> results = new ArrayList<>();
            Cursor studentCursor;
            if (period.equals("All"))
            {
                studentCursor = studentsDatabase.getInstance(getApplicationContext()).getAllEntries();
            }
            else
            {
                studentCursor = studentsDatabase.getInstance(getApplicationContext()).getEntries(period);
            }
            studentCursor.moveToFirst();

            do {
                int columnFirstName = studentCursor.getColumnIndex("firstname");
                int columnLastName = studentCursor.getColumnIndex("lastname");
                String studentName = studentCursor.getString(columnLastName) + ", " + studentCursor.getString(columnFirstName);

                results.add(studentName);

            } while (studentCursor.moveToNext());

            return results;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Spinner s = (Spinner) findViewById(R.id.spinner);
            s.setEnabled(false);
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getApplicationContext(),
                    R.layout.spinner_dropdown_item, periods);


            s.setAdapter(spinnerAdapter);
        }

        @Override
        protected void onPostExecute(List<String> strings) {
            super.onPostExecute(strings);
            l = (ListView) findViewById(R.id.myListView);
            ArrayAdapter<String> listAdapter = new ArrayAdapter<>(getApplicationContext(),
                    android.R.layout.simple_list_item_1, android.R.id.text1, strings);
            l.setAdapter(listAdapter);
            Spinner s = (Spinner) findViewById(R.id.spinner);
            s.setEnabled(true);
//            s.setSelected(true);
            s.setSelection(NO_SELECTION);
            s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    new MakeDatabaseRequestTask(i).execute();
                    adapterView.setSelection(i);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // Do Nothing
                }
            });
        }



        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
        }

        @Override
        protected void onCancelled(List<String> strings) {
            super.onCancelled(strings);
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }
    }
    private class MakeSheetsRequestTask extends AsyncTask<Void, Void, List<String>> {
        private com.google.api.services.sheets.v4.Sheets mService = null;
        private Exception mLastError = null;

        MakeSheetsRequestTask(GoogleAccountCredential credential) {
            databaseUpdated = false;
            Log.i("LOGMESSAGE", "In MakeSheetsRequestTask");

            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

            mService = new com.google.api.services.sheets.v4.Sheets.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Random Student")
                    .build();

        }

        /**
         * Background task to call Google Sheets API.
         *
         * @param params no parameters needed for this task.
         */

        @Override
        protected List<String> doInBackground(Void... params) {
            try
            {
                return getDataFromApi();
            }
            catch (Exception e) {
                Log.i("sheetresult", e.toString());
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch a list of names in a spreadsheet:
         * https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit
         *
         * @return List of names and majors
         * @throws IOException No description is necessary.
         */
        private List<String> getDataFromApi() throws IOException {
            String range;
            Log.i("LOGMESSAGE", "in getDataFromApi of sheets request");

            List<String> results = new ArrayList<>();

            Spreadsheet sheet = this.mService.spreadsheets().get(spreadsheetId).setIncludeGridData(false).execute();
            try {
                JSONObject js = new JSONObject(sheet.toString());
                JSONArray jsSheets = js.getJSONArray("sheets");
                String period;
                String gridId;

                for (int i = 0; i < jsSheets.length(); i++) {
                    period = jsSheets.getJSONObject(i).getJSONObject("properties").getString("title");
                    gridId = jsSheets.getJSONObject(i).getJSONObject("properties").getString("sheetId");
                    periods.add(period);
                    gridIds.put(period, gridId);
                }

            } catch (JSONException jse) {
                Log.i("sheetresult", jse.toString());
            }

            /* 1) Get entire file in JSON format
               2) Get all names and periods
               3) Add to DB
             */
            // get name and period
            for (int i = 0; i < gridIds.size(); i++) {
                String tempPeriod = periods.elementAt(i);
                range = tempPeriod + "!A2:B";

                ValueRange response = this.mService.spreadsheets().values()
                        .get(spreadsheetId, range)
                        .execute();

                List<List<Object>> values = response.getValues();
                if (values != null) {
//                results.add("Last Name, First Name");
                    for (List row : values) {
                        results.add(row.get(0) + ", " + row.get(1));
                        String sql;
                        sql = "INSERT INTO students(lastname, firstname, period) VALUES ('" + row.get(0).toString() + "','" + row.get(1).toString() + "','" + tempPeriod + "')";
                        studentsDatabase.getDatabase().execSQL(sql);
                    }
                }
            }

            databaseUpdated = true;
            return results;
        }

        @Override
        protected void onPreExecute() {
            l = (ListView) findViewById(R.id.myListView);
            l.setAdapter(null);
            mOutputText.setText("");
            if (periods.size() != 0)
            {
                periods.clear();
            }

            if (gridIds.size() != 0)
            {
                gridIds.clear();
            }
        }

        @Override
        protected void onPostExecute(List<String> output) {
            ArrayAdapter<String> listAdapter = new ArrayAdapter<>(getApplicationContext(),
                    android.R.layout.simple_list_item_1, android.R.id.text1, output);
            l = (ListView) findViewById(R.id.myListView);
            l.setAdapter(listAdapter);
            savedOutput = new ArrayList<>(output);

/*            for (String tempRow : output)
            {
                Log.i("LOGMESSAGE", tempRow);
            }
*/
//            spinner.setEnabled(true);

            Spinner spinner = (Spinner) findViewById(R.id.spinner);
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getApplicationContext(),
                    R.layout.spinner_dropdown_item, periods);
            spinner.setAdapter(spinnerAdapter);
//            for (int i = 0; i < periods.size(); ++i) {
//                Log.i("sheetresult", periods.elementAt(i) + "\n");
//                if (output.size() == 0) {
//                    mOutputText.setText(R.string.no_results_returned);
//                } else {
//                    output.add(0, "Data retrieved using the Sheets API:");
//                    l = (ListView) findViewById(R.id.myListView);

//                }
//            }
        }

        @SuppressWarnings("StatementWithEmptyBody")
        @Override
        protected void onCancelled() {

            if (mLastError != null) {
                String errorMessage = R.string.error_message + mLastError.getMessage();
                mOutputText.setText(errorMessage);
            }


        }
    }

}

