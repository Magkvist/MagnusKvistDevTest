package com.example.magnuskvist.giantleapdevtest;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends Activity {

    ArrayList<String[]> arrayList = new ArrayList<>();
    viewAdapter adapter;
    String placeholderSearchText;
    int page = 0;
    ListView mainListView;
    Button loadMoreButton;
    Button filterButton;
    TextView needMoreTextView;
    EditText searchEditText;

    SharedPreferences Sprefs;

    int sizeCount = 10; //How many organizations to show per page. This can be changed here if wanted

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Sprefs = getSharedPreferences("Sprefs", 0);

        mainListView = (ListView) findViewById(R.id.mainListView);

        //Add textWatchers
        searchEditText = (EditText) findViewById(R.id.searchText);
        assert searchEditText != null;
        searchEditText.addTextChangedListener(new nameTextWatcher());

        //Add listItems
        adapter = new viewAdapter(MainActivity.this, arrayList);
        assert mainListView != null;
        mainListView.setAdapter(adapter);

        //Activate the load More Button
        loadMoreButton = new Button(MainActivity.this);
        loadMoreButton.setText(R.string.loadmore);
        loadMoreButton.setOnClickListener(new loadMoreButtonClickListener()); //Creating another class instead of having the whole code up here.
        mainListView.addFooterView(loadMoreButton);
        loadMoreButton.setVisibility(View.GONE);

        needMoreTextView = new TextView(MainActivity.this);
        needMoreTextView.setText(R.string.need);
        needMoreTextView.setVisibility(View.GONE);
        needMoreTextView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        mainListView.addFooterView(needMoreTextView);

        //Activate the filter button
        filterButton = (Button) findViewById(R.id.filterButton);
        filterButton.setOnClickListener(new filterButtonClickListener());

    }

    class filterButtonClickListener implements View.OnClickListener {
        //This class takes care of the filter/settings button.

        @Override
        public void onClick(View v) {

            final Dialog dialog = new Dialog(MainActivity.this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.filter_custom_view);
            dialog.show();

            //Activating the views
            final CheckBox bankruptCheckBox = (CheckBox)dialog.findViewById(R.id.bankruptCheckBox);
            final EditText amountEmployeesEditText = (EditText)dialog.findViewById(R.id.amountEmployeesEditText);
            final EditText zipCodeEditText = (EditText)dialog.findViewById(R.id.zipCodeEditText);

            bankruptCheckBox.setChecked(Sprefs.getBoolean("bankruptCheckBox", false));
            amountEmployeesEditText.setText(Sprefs.getString("amountEmployeesEditText", ""));
            zipCodeEditText.setText(Sprefs.getString("zipCodeEditText", ""));

            Button closeButton = (Button)dialog.findViewById(R.id.closeButton);
            closeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.cancel();
                }
            });


            Button saveButton = (Button)dialog.findViewById(R.id.saveButton);
            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    Sprefs.edit().putBoolean("bankruptCheckBox", bankruptCheckBox.isChecked()).apply();
                    Sprefs.edit().putString("amountEmployeesEditText", amountEmployeesEditText.getText().toString()).apply();

                    //If not 0 or 4 numbers in zip, don't save value and tell user via Toast.
                    if(zipCodeEditText.getText().length() == 4 || zipCodeEditText.getText().length() == 0 ) {
                        Sprefs.edit().putString("zipCodeEditText", zipCodeEditText.getText().toString()).apply();
                    }else{
                        Toast.makeText(MainActivity.this, R.string.zipneed, Toast.LENGTH_SHORT).show();
                    }

                    //Need to check if there is enough input to search when we get back to the main screen

                    try {//Try because the searchEditText can be empty. This throws an error
                        if ('0' <= searchEditText.getText().charAt(0) && searchEditText.getText().charAt(0) <= '9') { //If this works, its a number
                            Log.d("debug", "Number!");

                            if (searchEditText.getText().length() >= 9) {
                                arrayList.clear();
                                new FetchJson().execute();
                            }

                        } else {

                            if (searchEditText.getText().length() > 1) {
                                arrayList.clear();
                                new FetchJson().execute();
                            }
                        }
                    }catch (Exception e){
                        //This means there aint nothing there! And that's quite okey.
                    }

                    dialog.cancel();

                    //User probably doesn't want the keyboard at this point.
                    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

                }
            });
        }
    }

    class FetchJson extends AsyncTask<String, String, String> {

        //This class takes care of loading the JSONfile, and assigning it to the ArrayList/listView.

        JSONObject jsonObject;
        String jsonOutput;
        String localSearchText="";
        Boolean loadButtonFlag = true;
        JSONArray resultsArray=null;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        protected String doInBackground(String... args) {

            localSearchText = placeholderSearchText; //This is to load an instance of these values here. In case they change etc. under runtime.


            //Here we take care of the needed filters. The filterstring remains empty if there are non to take care of.
            String filters="";

            //If the checkbox is TRUE we do NOT want bankrupt firms
            if(Sprefs.getBoolean("bankruptCheckBox",false)) {
                filters += "%20and%20konkurs%20eq%20false";
            }

            if(!Sprefs.getString("amountEmployeesEditText", "").equals("") && !Sprefs.getString("amountEmployeesEditText", "0").equals("0") ){
                filters += "%20and%20antallAnsatte%20gt%20" + Sprefs.getString("amountEmployeesEditText", "");
            }

            if(!Sprefs.getString("zipCodeEditText", "").equals("")){
                filters += "%20and%20forretningsadresse%2Fpostnummer%20eq%20%27"+Sprefs.getString("zipCodeEditText", "") + "%27";
            }

            Log.d("debug", "Filters: " + filters);

            String stringUrl = "http://data.brreg.no/enhetsregisteret/enhet.json?page="+page+"&size="+sizeCount+"&"+ localSearchText + filters;//Loading the JSON with the appropriate link.
            Log.d("debug", "StringURL: " + stringUrl);

            //Standard call to a httpconnection follows. The buffer is made quite large on purpose.
            StringBuilder response = new StringBuilder();
            try {
                URL url = new URL(stringUrl);
                HttpURLConnection httpconnection = (HttpURLConnection) url.openConnection();
                if (httpconnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader input = new BufferedReader(new InputStreamReader(httpconnection.getInputStream()), 16384);
                    String strLine;

                    while ((strLine = input.readLine()) != null) {
                        response.append(strLine);
                    }
                    input.close();
                }

                jsonOutput = response.toString();
                jsonObject = new JSONObject(jsonOutput);
                resultsArray = jsonObject.getJSONArray("data");
                Log.d("debug", "Json: " + jsonOutput);

            } catch (Exception e) {
                Log.d("debug", "Error.. " + e);
            }

            //If, and only if its zero page we need to empty the arrayList. If its page one + we simply make the list longer.
            if(page==0) {
                arrayList.clear();
            }

            //Fetching the info from the JSONobject
            for (int x = 0; x < sizeCount; x++) {
                try {

                    // Array info: 0 = Name, 1 = OrgNr, 2 = Address, 3 = link,  4 = RegDate, 5 = Employees, 6 = Homepage

                    String[] array = new String[8];

                    //The following part is messy, but an easy and efficient way to check every item for availability. The non available will be marked with "NA"

                    //This one will always have an value if everything else is correct. If not the try/catch will fail and no item will load. Which is perfect for us!
                    array[0] = (resultsArray.getJSONObject(x).getString("navn"));

                    try{
                        array[1] = (resultsArray.getJSONObject(x).getString("organisasjonsnummer"));
                    }catch (Exception e){
                        array[1] = "NA";
                    }

                    try{
                        array[2] = (resultsArray.getJSONObject(x).getJSONObject("forretningsadresse").getString("adresse")) +", " + (resultsArray.getJSONObject(x).getJSONObject("forretningsadresse").getString("postnummer")) + " " + (resultsArray.getJSONObject(x).getJSONObject("forretningsadresse").getString("poststed")) ;
                    }catch (Exception e){
                        array[2] = "NA";
                    }

                    try{
                        array[3] = (resultsArray.getJSONObject(x).getString("stiftelsesdato"));
                    }catch (Exception e){
                        array[3] = "NA";
                    }

                    try{
                        array[4] = (resultsArray.getJSONObject(x).getString("antallAnsatte"));
                    }catch (Exception e){
                        array[4] = "NA";
                    }

                    try{
                        array[5] = (resultsArray.getJSONObject(x).getString("hjemmeside"));
                    }catch (Exception e){
                        array[5] = "NA";
                    }

                    arrayList.add(array);

                }catch (Exception e){
                    //If this happens there are either no , or none objects left. If this is true we don't want the loadMoreButton.

                    loadButtonFlag = false;

                }
            }
            return null;
        }

        protected void onPostExecute(String file_url) {

            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    adapter.notifyDataSetChanged();

                    if(loadButtonFlag) {
                        loadMoreButton.setVisibility(View.VISIBLE);
                    }else{
                        loadMoreButton.setVisibility(View.GONE);
                    }
                    //The needMoreTextView is stating that something is missing. We don't want this right now.
                    needMoreTextView.setVisibility(View.GONE);
                }
            });
        }
    }

    class loadMoreButtonClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {

            //Load more means we need another page. And we also need to load from that. This is mostly handled inside the FetchJson class.
            page++;
            new FetchJson().execute();
        }
    }

    class viewAdapter extends BaseAdapter {

        //This class holds a lot of "standard" stuff. "getView" further down is the interesting part.
        Context context;
        ArrayList<String[]> data;

        private LayoutInflater inflater = null;

        public viewAdapter(Context context, ArrayList<String[]> data) {

            this.context = context;
            this.data = data;
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int position) {
            return data.get(position);

        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {

            View view = convertView;

            if (view == null) {
                view = inflater.inflate(R.layout.searchbar_custom_list_item, null);
            }

            TextView nameText = (TextView) view.findViewById(R.id.searchText);
            TextView orgNrText = (TextView) view.findViewById(R.id.orgNrText);
            TextView addressText = (TextView) view.findViewById(R.id.addressText);

            //This makes the whole list item clickable.
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    //Lunch custom infoviewer. Instead of launching a new activity(CPU intensive) we make a neat dialog.

                    final Dialog dialog = new Dialog(MainActivity.this);
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

                    dialog.setContentView(R.layout.orginfo_custom_view);

                    TextView orgNameDetText = (TextView) dialog.findViewById(R.id.orgNameDetText);
                    TextView addressDetText = (TextView) dialog.findViewById(R.id.addressDetText);
                    TextView orgNrDetText = (TextView) dialog.findViewById(R.id.orgNrDetText);
                    TextView regDetText = (TextView) dialog.findViewById(R.id.regDetText);
                    TextView empleyeesDetText = (TextView) dialog.findViewById(R.id.employeesDetText);
                    TextView homepageDetText = (TextView) dialog.findViewById(R.id.homepageDetText);

                    Button closeButton = (Button)dialog.findViewById(R.id.closeButton);

                    closeButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.cancel();
                        }
                    });

                    //From the arraylist: 0 = Name, 1 = OrgNr, 2 = Address, 3 = link,  4 = RegDate, 5 = Employees, 6 = Homepage

                    //Some placeholders for loading resources and so on.
                    String orgNrDetTextPH = getString(R.string.orgnr) + " " + arrayList.get(position)[1];
                    String regDetTextPH = getString(R.string.registered) + " "  + arrayList.get(position)[3];
                    String employeesDetTextPH = getString(R.string.employees) + " "  + arrayList.get(position)[4];
                    String homepageDetTextPH = getString(R.string.homepage) + " "  + arrayList.get(position)[5];

                    orgNameDetText.setText(arrayList.get(position)[0]);
                    orgNrDetText.setText(orgNrDetTextPH);


                    //If there is an address we want it to be clickable. Also the view will launch Google Maps with the address already filled in.
                    if(!arrayList.get(position)[2].equals("NA")) {
                        addressDetText.setText(Html.fromHtml("<a href=\"\">"+arrayList.get(position)[2]+"</a>"));
                        addressDetText.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Uri intentUri = Uri.parse("geo:0,0?q=" + arrayList.get(position)[2]);
                                Intent mapIntent = new Intent(Intent.ACTION_VIEW, intentUri);
                                mapIntent.setPackage("com.google.android.apps.maps");
                                startActivity(mapIntent);
                            }
                        });
                    }else{
                        addressDetText.setText("Address: NA");
                    }

                    //Det == Detailed
                    regDetText.setText(regDetTextPH);
                    empleyeesDetText.setText(employeesDetTextPH);
                    homepageDetText.setText(homepageDetTextPH);

                    try{
                        dialog.show();
                    }
                    catch (Exception e){}

                }
            });

            nameText.setText(data.get(position)[0]);
            orgNrText.setText(data.get(position)[1]);

            if(data.get(position)[2].equals("NA")){
                addressText.setText("Address: " + data.get(position)[2]);
            }else {
                addressText.setText(data.get(position)[2]);
            }
            return view;
        }

    }

    class nameTextWatcher implements TextWatcher {


        //This class acts the instance a button is pressed. It checks the text and acts accordingly.
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

            //Reset the page.
            page=0;
            mainListView.setSelection(0);//Taking the listView to the top

            //Check if its a orgnumber, if so, we need 9 numbers. If its not number, its text, and we need atleast 2 chars. This is required from the Brreg API.
            try {

                if ('0' <= s.charAt(0) && s.charAt(0) <= '9') { //If this works, its a number
                    Log.d("debug", "Number!");

                    if (s.length() >= 9) {
                        placeholderSearchText =  "$filter=organisasjonsnummer%20eq%20" + s;
                        new FetchJson().execute();

                    } else {
                        loadMoreButton.setVisibility(View.GONE);
                        needMoreTextView.setVisibility(View.VISIBLE);
                        arrayList.clear();
                        adapter.notifyDataSetChanged();
                    }
                } else {

                    Log.d("debug", "Text!");
                    //This means its an org name
                    if (s.length() > 1) {
                        String ph = s.toString().replaceAll(" ", "%20");
                        placeholderSearchText = "$filter=startswith(navn,'" + ph + "')";
                        new FetchJson().execute();

                    } else {
                        Log.d("debug", "less than 1 char..");
                        loadMoreButton.setVisibility(View.GONE);
                        needMoreTextView.setVisibility(View.VISIBLE);
                        arrayList.clear();
                        adapter.notifyDataSetChanged();
                    }
                }

            }catch (Exception e){
                Log.d("debug", "expt: " + e);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {

        }
    }

}
