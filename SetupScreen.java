package com.lapharcius.randomstudent;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.File;
import com.google.api.services.sheets.v4.SheetsScopes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class SetupScreen extends Activity
        implements NavigationView.OnNavigationItemSelectedListener,
        EasyPermissions.PermissionCallbacks
{
    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;
    static final int REQUEST_DISPLAY_STUDENTS = 1004;

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String PREF_FILE_NAME = "fileName";
    private static final String[] SCOPES = {
            DriveScopes.DRIVE_METADATA_READONLY,
            SheetsScopes.SPREADSHEETS_READONLY
    };

    SparseArray<String> fileIds;
    GoogleAccountCredential mCredential;
    ArrayAdapter<CharSequence> a;
    static BroadcastReceiver networkChangeReceiver;
    ListView mFilenames;
    static List < String > mSavedFilenames;
    ListView savedListView;
    TextView mExceptionText;

    Spinner spinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null)
        {   Log.i("LOGMESSAGE", "blablldldlfllf");}
        setContentView(R.layout.activity_main);
        
        // First, let's register a BroadcastReceiver to track network connection changes.
        // Since the data is mostly stored in a local database, we generally won't need
        // internet other than when first setting up the data.
        
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        networkChangeReceiver = new NetworkReceiver();
        registerReceiver(networkChangeReceiver, filter);

        // Next, set up the Google Account Credential
        // Again, this is mostly unnecessary for anything other than first setting up the data.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES)).setBackOff(new ExponentialBackOff());

        // Used to show Exception text when debugging faulty logic.
        mExceptionText = (TextView) findViewById(R.id.outputText);

//        database = studentsDatabase.getInstance(this);
//        database.createDB();

/*        if (database.countItems() != 0) {
            Intent i = new Intent(MainActivity.this, displayStudents.class);
            String fileName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_FILE_NAME, null);
            unregisterReceiver(networkChangeReceiver);

            i.putExtra("fileName", fileName);
            i.putExtra("loginName", mCredential.getSelectedAccountName());
            startActivityForResult(i, REQUEST_DISPLAY_STUDENTS);
        }
*/
        if (savedInstanceState != null && mSavedFilenames != null)
        {
            // Add the previously saved filenames to our current ListView.
            mFilenames = (ListView) findViewById(R.id.myListView);
            List < String > output = mSavedFilenames;
            // Consider using mSavedFilenames here instead of local "output" List.
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(),
                    android.R.layout.simple_list_item_1, android.R.id.text1, output);
            mFilenames.setAdapter(adapter);
            mFilenames.setEnabled(true);
        }
        else
        {
            setUpExampleTableData();
            setUpExampleSpinnerData();
            mFilenames.setEnabled(false);
        }

        fileIds = new SparseArray<>();

        mFilenames.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                SharedPreferences settings =
                        getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(PREF_FILE_NAME, parent.getItemAtPosition(position).toString());
                editor.apply();

                Intent i = new Intent(SetupScreen.this, displayStudents.class);

                i.putExtra("fileName", parent.getItemAtPosition(position).toString());
                i.putExtra("SpreadsheetId", fileIds.get(position));
                i.putExtra("loginName", mCredential.getSelectedAccountName());
                startActivity(i);
            }
        });

        enableDrawerOptions();
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        // Reminder to self... This following line will cause Android to use the actual icon colors.
        // Previously, it was displaying the icons in black and white.
        navigationView.setItemIconTintList(null);

        setUpFloatingButton();
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

    @Override
    protected void onPause() {
        // This is obvious, but we don't want to constantly listening for changes in network. This
        // could end up consuming resources. So, when paused, we unregister.

        super.onPause();
        Log.i("LOGMESSAGE", "OnPause");
        savedListView = mFilenames;
        unregisterReceiver(networkChangeReceiver);

    }

    @Override
    protected void onResume() {

        // Again, we don't want to constantly listening for changes in network, which could end up
        // consuming resources. So, when paused, we unregister. On resume, we reregister.

        super.onResume();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        networkChangeReceiver = new NetworkReceiver();
        this.registerReceiver(networkChangeReceiver, filter);
        mFilenames = savedListView ;
    }

    @Override
    protected void onDestroy() {
        // Again, we don't want to constantly listening for changes in network, which could end up// consuming resources. So, when the activity is destroyed, we unregister. On create, we
        // reregister.
        //
        // To do - I would like to modify this to ***not*** unregister when a change in orientation
        // occurs. I don't know yet how to determine whether this onDestroy was called due to
        // killing the app vs. a change in orientation.

        // To fix this, perhaps I should unregister on onStop???
        //
        super.onDestroy();
//        if (networkChangeReceiver != null){
//            unregisterReceiver(networkChangeReceiver);
//        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        savedListView = mFilenames;
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     *
     * @param requestCode  The request code passed in
     *                     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */

    @Override

    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */

    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (!googlePlayServicesAreAvailable()) {
            acquireGooglePlayServices();
        } else if (!isDeviceOnline()) {
            mExceptionText.setText(R.string.no_connection_available);
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else {
            new MakeDriveRequestTask(mCredential).execute();
        }
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     *
     * @return true if Google Play Services is available and up to
     * date on this device; false otherwise.
     */
    private boolean googlePlayServicesAreAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_GoogleLogin) {
            getResultsFromApi();
            spinner.setVisibility(View.GONE);
            TextView t = (TextView)findViewById(R.id.periodTextField);
            t.setVisibility(View.GONE);
            savedListView = (ListView) findViewById(R.id.myListView);
        } else if (id == R.id.nav_GoogleLogout) {

            if (mCredential.getSelectedAccountName() != null) {
                SharedPreferences settings =
                        getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(PREF_ACCOUNT_NAME, null);
                editor.apply();
                mCredential.setSelectedAccountName(null);
            }

            Toast.makeText(getApplicationContext(), "Logged out successfully", Toast.LENGTH_LONG).show();
            mFilenames = (ListView) findViewById(R.id.myListView);
            mFilenames.clearChoices();
            setUpExampleSpinnerData();
            setUpExampleTableData();
        } else if (id == R.id.nav_deleteDB) {
            deleteDatabase("students.db");
            Toast.makeText(getApplicationContext(), "All Entries Deleted!", Toast.LENGTH_LONG).show();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);

        enableDrawerOptions();
        return true;
    }

    void enableDrawerOptions() {
        if (mCredential.getSelectedAccountName() != null) {
            NavigationView n = (NavigationView) findViewById(R.id.nav_view);
            Menu m = n.getMenu();
            MenuItem mi = m.findItem(R.id.nav_GoogleLogin);
            mi.setEnabled(true);
            mi = m.findItem(R.id.nav_GoogleLogout);
            mi.setEnabled(true);
//            mi = m.findItem(R.id.nav_GoogleLogin);
//            mi.setEnabled(false);
            mi = m.findItem(R.id.nav_deleteDB);
            mi.setEnabled(true);

        } else {
            NavigationView n = (NavigationView) findViewById(R.id.nav_view);
            Menu m = n.getMenu();
            MenuItem mi = m.findItem(R.id.nav_GoogleLogout);
            mi.setEnabled(false);
            mi = m.findItem(R.id.nav_GoogleLogin);
            mi.setEnabled(true);
            mi = m.findItem(R.id.nav_deleteDB);
            mi.setEnabled(false);
        }

    }

    void setUpFloatingButton() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar bar = Snackbar.make(view, "Developed by Christopher J. Galasso", Snackbar.LENGTH_LONG);
                View v = bar.getView();
                v.setBackgroundColor(getResources().getColor(R.color.VillanovaAthleticBlue));
//                v.setBackgroundColor(Color.rgb(242, 169, 0));
                bar.setAction("Action", null).show();
            }
        });
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

    }

    /**
     * Checks whether the device currently has a network connection.
     *
     * @return true if the device has a network connection, false otherwise.
     */
    public boolean isDeviceOnline()
    {
        try
        {
            ConnectivityManager connMgr =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = null;
            if (connMgr != null) {
                networkInfo = connMgr.getActiveNetworkInfo();
            }
            return (networkInfo != null && networkInfo.isConnected());
        }
        catch (NullPointerException e)
        {
            Log.i("LOGMESSAGE", e.toString());
            return false;
        }
    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */

    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
                myToolbar.setTitle(getResources().getText(R.string.actionBarTitle) + " - " + mCredential.getSelectedAccountName());
                new MakeDriveRequestTask(mCredential).execute();
                Toast.makeText(getApplicationContext(), mCredential.getSelectedAccountName() + " already logged in", Toast.LENGTH_LONG).show();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */

    private void acquireGooglePlayServices()
    {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode))
        {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }

    void setUpExampleTableData() {
        mFilenames = (ListView) findViewById(R.id.myListView);
        mFilenames.setAdapter(null);
        String [] testValues = getResources().getStringArray(R.array.example_fileNames);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1, testValues);
        mFilenames.setAdapter(adapter);
    }

    void setUpExampleSpinnerData() {
        spinner = (Spinner) findViewById(R.id.spinner);
        spinner.setEnabled(false);

        Cursor cursor = studentsDatabase.getDatabase().query(true, "students", new String[]{"_id", "period"}, null, null, null, null, null, null);
        if (cursor.getCount() == 0) {
            a = ArrayAdapter.createFromResource(this, R.array.example_periods, android.R.layout.simple_spinner_item);
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(a);
        }
        cursor.close();

    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i("LOGMESSAGE", "onActivityResult");
        switch (requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    mExceptionText.setText(R.string.requires_google_play_services_string);
                } else {
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
                        myToolbar.setTitle(getResources().getText(R.string.actionBarTitle) + " - " + mCredential.getSelectedAccountName());
                        getResultsFromApi();
                        Toast.makeText(getApplicationContext(), accountName + " successfully logged in.", Toast.LENGTH_LONG).show();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK)
                {
                }
                break;
            case REQUEST_DISPLAY_STUDENTS:
                Log.i("LOGMESSAGE", "OnActivityResult");
                setUpExampleTableData();
                setUpExampleSpinnerData();
                mFilenames.setEnabled(false);
                break;
            default:
        }

    }

    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     *
     * @param connectionStatusCode code describing the presence (or lack of)
     *                             Google Play Services on this device.
     */

    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                SetupScreen.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }


    /**
     * An asynchronous task that handles the Google Sheets API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeDriveRequestTask extends AsyncTask<Void, Void, List<String>> {
        private com.google.api.services.drive.Drive mDriveService = null;
        private Exception mLastError = null;
        private GoogleAccountCredential mCredential;

        MakeDriveRequestTask(GoogleAccountCredential credential) {

            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mCredential = credential;

            mDriveService = new com.google.api.services.drive.Drive.Builder(
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
            try {
                return getDataFromApi();
            } catch (Exception e) {
                Log.i("LOGMESSAGE", e.toString());
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch a list of names and majors of students in a sample spreadsheet:
         * https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit
         *
         * @return List of names and majors
         * @throws IOException no description for this tag is necessary
         */
        private List<String> getDataFromApi() throws IOException {
            List<String> fileInfo = new ArrayList<>();
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);

            }
            else
            {
                Log.i("LOGMESSAGE", "Account == null");
            }

            FileList result = mDriveService.files().list()
                    .setQ("mimeType='application/vnd.google-apps.spreadsheet'")
                    .setPageSize(100)
                    .setFields("kind, nextPageToken, files(name, id)")
                    .execute();

            List<File> files = result.getFiles();

            if (files != null)
            {
                int listIndex = 0;
                for (File file : files)
                {
                    fileIds.put(listIndex, file.getId());
                    listIndex++;
                    fileInfo.add(String.format("%s\n", file.getName()));
                }
            }

            return fileInfo;
        }


        @Override
        protected void onPreExecute ()
        {
            mFilenames = (ListView) findViewById(R.id.myListView);
            mFilenames.setAdapter(null);
            mExceptionText.setText("");
        }

        @Override
        protected void onPostExecute (List < String > output) {
            if (output == null || output.size() == 0) {
                mExceptionText.setText(R.string.no_results_returned);
            } else {
                mFilenames = (ListView) findViewById(R.id.myListView);

                ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(),
                        android.R.layout.simple_list_item_1, android.R.id.text1, output);
                mFilenames.setAdapter(adapter);
                mFilenames.setEnabled(true);

                mSavedFilenames = new ArrayList<>(output);
            }
        }
        @SuppressWarnings("StatementWithEmptyBody")
        @Override
        protected void onCancelled ()
        {
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            REQUEST_AUTHORIZATION);
                } else {
                    mExceptionText.setText(String.valueOf(R.string.error_message + mLastError.getMessage()));
                }
            } else {
                mExceptionText.setText(R.string.request_cancelled);
            }
        }
    }

}

