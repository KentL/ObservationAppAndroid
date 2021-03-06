/**	 ObservationApp, Copyright 2016, University of Prince Edward Island,
 550 University Avenue, C1A4P3,
 Charlottetown, PE, Canada
 *
 * 	 @author Kent Li <zhuoli@upei.ca>
 *
 *   This file is part of ObservationApp.
 *
 *   ObservationApp is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   CycleTracks is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with CycleTracks.  If not, see <http://www.gnu.org/licenses/>.
 */


package ca.zhuoliupei.observationapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Locale;

import Const.DrupalServicesFieldKeysConst;
import Const.HTTPConst;
import DrupalForAndroidSDK.DrupalAuthSession;
import DrupalForAndroidSDK.DrupalServicesNode;
import DrupalForAndroidSDK.DrupalServicesView;
import HelperClass.GeoUtil;
import HelperClass.MyObservationCacheManager;
import HelperClass.NotificationUtil;
import HelperClass.PreferenceUtil;
import HelperClass.ToolBarStyler;
import Model.ObservationEntryObject;

public class ObservationDetailActivity extends AppCompatActivity {
    private static final String TITLE="title";
    private static final String NID="nid";
    private static final String AUTHOR="author_name";
    private static final String IMAGE ="Image";
    private static final String DESCRIPTION="Description";
    private static final String DIARY_RECORD="Climate Diary Record";
    private static final String DATE="Date Observed";
    private static final String LATLON="Location lat/long";
    private static final String ADDRESS="Location Observed";
    private static final String LATITUDE="lat";
    private static final String LONGITUDE ="lon";
    private static final String COUNTRY_CODE ="country";
    private static final String PROVINCE ="administrative_area";
    private static final String CITY ="locality";
    private static final String STREET ="thoroughfare";
    private static final String POSTAL_CODE="postal_code";


    private static final int INVALID=-1;
    private DownloadObservationDetailTask downloadObservationDetailTask;
    private DownloadObservationImageTask downloadObservationImageTask;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout rootLinearLayout;
    private int nid;
    private int mapZoomRate;//range from 2 to 21 according to GoogleMap api
    private String baseUrl,endpoint;
    private DrupalServicesView drupalServicesView;
    private DrupalAuthSession drupalAuthSession;
    private ObservationEntryObject observationEntryObject;
    private boolean userIsAuthor=false;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (downloadObservationDetailTask!=null)
            downloadObservationDetailTask.cancel(true);
        if (downloadObservationImageTask!=null)
            downloadObservationImageTask.cancel(true);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_observation_detail, menu);
        menu.findItem(R.id.action_delete).setVisible(userIsAuthor);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete: {
                handleDeleteAction();
                return true;
            }
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_observation_detail);
        initializeVariables();
        initializeUI();
        setWidgetListeners();
    }

    //Wrapped in onCreate()
    private void initializeVariables(){
        Intent intent=getIntent();
        mapZoomRate=13;
        nid=Integer.parseInt(intent.getExtras().getString(NID, String.valueOf(INVALID))) ;
        baseUrl=getString(R.string.drupal_site_url);
        endpoint=getString(R.string.drupal_server_endpoint);
        swipeRefreshLayout=(SwipeRefreshLayout)findViewById(R.id.swiperefresh_ObservationDetailActivity);
        rootLinearLayout=(LinearLayout)findViewById(R.id.content_ll_ObservationDetailActivity);
        drupalAuthSession=new DrupalAuthSession();
        drupalAuthSession.setSession(PreferenceUtil.getCookie(this));
        drupalServicesView=new DrupalServicesView(baseUrl,endpoint);
        drupalServicesView.setAuth(drupalAuthSession);
    }
    private void initializeUI(){
        initializeToolBar();
        initializeContentView();
    }
    private void setWidgetListeners(){
        setTransparentImageViewOnTouch();
    }

    // initializeUI() subroutines
    private void initializeToolBar(){
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar_ObservationDetailActivity);
        ToolBarStyler.styleToolBar(this, myToolbar, "Observation");
    }
    private void initializeContentView(){
        hideScrollView();
        showLoadingView();
        beginDownloadObservationDetail();
    }


    // setWidgetListeners() subroutines
    private void setTransparentImageViewOnTouch(){
        //http://stackoverflow.com/questions/16974983/google-maps-api-v2-supportmapfragment-inside-scrollview-users-cannot-scroll-th
        final ScrollView mainScrollView = (ScrollView) findViewById(R.id.content_sv_ObservationDetailActivity);
        ImageView transparentImageView = (ImageView) findViewById(R.id.transparent_image);

        transparentImageView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        // Disallow ScrollView to intercept touch events.
                        mainScrollView.requestDisallowInterceptTouchEvent(true);
                        // Disable touch on transparent view
                        return false;

                    case MotionEvent.ACTION_UP:
                        // Allow ScrollView to intercept touch events.
                        mainScrollView.requestDisallowInterceptTouchEvent(false);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        mainScrollView.requestDisallowInterceptTouchEvent(true);
                        return false;

                    default:
                        return true;
                }
            }
        });
    }


    //Begin Asynctasks
    private void beginDownloadObservationDetail(){
        downloadObservationDetailTask=new DownloadObservationDetailTask(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            downloadObservationDetailTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            downloadObservationDetailTask.execute();
        }
    }

    // Asynctasks
    private class DownloadObservationDetailTask extends AsyncTask<Void,Void,HashMap<String, String>>{
        Context context;
        public DownloadObservationDetailTask(Context context){
            this.context=context;
        }
        @Override
        protected HashMap<String, String> doInBackground(Void... voids) {
            if (nid==INVALID) {
                return null;
            }
            try {
                BasicNameValuePair[] params=new BasicNameValuePair[1];
                params[0]=new BasicNameValuePair(NID,String.valueOf(nid));
                return drupalServicesView.retrieve(DrupalServicesView.ViewType.SINGLE_NODE_DETAIL, params);
            }catch (Exception ex){
                return null;
            }
        }

        @Override
        protected void onPostExecute(HashMap<String, String> resultMap) {
            if (resultMap==null){
                showNetworkErrorView();
                return;
            }
            String statusCode=resultMap.get(DrupalServicesFieldKeysConst.STATUS_CODE);
            String responseBody=resultMap.get(DrupalServicesFieldKeysConst.RESPONSE_BODY);
            if (statusCode.equals(HTTPConst.HTTP_OK_200))
                if (responseBody.equals("[]")||responseBody.length()<5)
                    showNodeNotFoundView();
                else {
                    loadContentViewWithDetail(responseBody);
                    checkUserIsAuthor();
                    if (userIsAuthor){
                        invalidateOptionsMenu();
                    }
                }
            else if(statusCode.equals(HTTPConst.HTTP_NOT_FOUND_404))
                showNodeNotFoundView();
        }
    }
    private class DownloadObservationImageTask extends AsyncTask<String, Void, Bitmap> {
        ImageView bmImage;

        public DownloadObservationImageTask(ImageView bmImage) {
            this.bmImage = bmImage;
        }

        protected Bitmap doInBackground(String... urls) {
            String urldisplay = urls[0];
            Bitmap mIcon11 = null;
            try {
                InputStream in = new java.net.URL(urldisplay).openStream();
                mIcon11 = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.e("Error", e.getMessage());
                e.printStackTrace();
            }
            return mIcon11;
        }

        protected void onPostExecute(Bitmap result) {
            bmImage.setImageBitmap(result);
        }
    }
    private class DeletePostTask extends AsyncTask<String,Void,Boolean>{
        Context context;
        String nid;
        public DeletePostTask(Context context){
            this.context=context;
        }
        @Override
        protected Boolean doInBackground(String... params) {
            if (params.length<=0)
                return false;
            nid=params[0];
            if (drupalAuthSession==null)
                return false;
            DrupalServicesNode drupalServicesNode=new DrupalServicesNode(baseUrl,endpoint);
            drupalServicesNode.setAuth(drupalAuthSession);
            HashMap<String ,String > map;
            try {
                map = drupalServicesNode.delete(Integer.parseInt(nid));
            }catch (Exception ex){
                return false;
            }
            if (!map.get(DrupalServicesFieldKeysConst.STATUS_CODE).equals(HTTPConst.HTTP_OK_200))
                return false;

            return true;
        }

        @Override
        protected void onPostExecute(Boolean succeed) {
            NotificationUtil.removeNotification(context, NotificationUtil.NotificationID.DELETE_USER_POST_NOTIFICATION_ID);
            if(succeed)
                MyObservationCacheManager.getInstance(context).deleteCache(nid);
            else
                NotificationUtil.showNotification(context, NotificationUtil.NotificationID.UPLOAD_FAILED_NOTIFICATION_ID);
        }
    }

    private void loadContentViewWithDetail(String detailJsonStr){
        try {
            //Hide the refresh animation
            hideLoadingView();

            findViewById(R.id.content_sv_ObservationDetailActivity).setVisibility(View.VISIBLE);
            //Load info
            JSONObject jsonObject = new JSONArray(detailJsonStr).getJSONObject(0);
            observationEntryObject=constructObservationEntryObject(jsonObject);
            if (observationEntryObject.description!=null && !observationEntryObject.description.isEmpty())
                ((TextView)findViewById(R.id.txtDescription_ObservationDetailActivity)).setText(observationEntryObject.description);
            if (observationEntryObject.title!=null && !observationEntryObject.title.isEmpty())
                ((TextView)findViewById(R.id.txtTitle_ObservationDetailActivity)).setText(observationEntryObject.title);
            if (observationEntryObject.date!=null && !observationEntryObject.date.isEmpty()) {
                String date=observationEntryObject.date.split("[ ]")[0];
                ((TextView) findViewById(R.id.txtDateTime_ObservationDetailActivity)).setText(date);
            }
            if (observationEntryObject.record!=null && !observationEntryObject.record.isEmpty()) {
                ((TextView) findViewById(R.id.txtRecord_ObservationDetailActivity)).setText(observationEntryObject.record);
            }
            if (observationEntryObject.address!=null && !observationEntryObject.address.isEmpty())
                ((TextView)findViewById(R.id.txt_location_ObservationDetailActivity)).setText(observationEntryObject.address);
            else {
                String latlon= String.format("Lat/Lon: %s  %s", observationEntryObject.lattitude, observationEntryObject.longitude);
                ((TextView)findViewById(R.id.txt_location_ObservationDetailActivity)).setText(latlon);
            }

            // In case lon/lat contains invalid char
            try {
                //Show Location in Google Map
                GoogleMap googleMap;
                googleMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map_ObservationDetailActivity)).getMap();
                double lat = Double.parseDouble(observationEntryObject.lattitude);
                double lon = Double.parseDouble(observationEntryObject.longitude);
                final LatLng location = new LatLng(lat, lon);
                DecimalFormat df = new DecimalFormat("#.00");
                String markerSnippet= String.format("（Lat/Lon:%s,%s）", df.format(lat), df.format(lon));
                googleMap.addMarker(new MarkerOptions().position(location).title(observationEntryObject.title).snippet(markerSnippet));
                googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                googleMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
                googleMap.getUiSettings().setZoomGesturesEnabled(true);
                CameraUpdate center = CameraUpdateFactory.newLatLng(new LatLng(lat, lon));
                CameraUpdate zoom = CameraUpdateFactory.zoomTo(mapZoomRate);
                googleMap.moveCamera(center);
                googleMap.animateCamera(zoom);
            }catch (Exception ex){
                Toast.makeText(this,R.string.network_error,Toast.LENGTH_SHORT).show();
            }

            //Start download image if node image exists
            if (observationEntryObject.photoServerUri!=null && !observationEntryObject.photoServerUri.isEmpty()){
                ImageView imageView=(ImageView)findViewById(R.id.img_photo_ObservationDetailActivity);
                downloadObservationImageTask=new DownloadObservationImageTask(imageView);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    downloadObservationImageTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,observationEntryObject.photoServerUri);
                } else {
                    downloadObservationImageTask.execute(observationEntryObject.photoServerUri);
                }
            }
        }catch (Exception ex){
            showNetworkErrorView();
        }
    }

    //Helper functions
    private ObservationEntryObject constructObservationEntryObject(JSONObject jsonObject){
        ObservationEntryObject object=new ObservationEntryObject();
        try {
            String nid = jsonObject.getString(NID);
            object.nid = nid;
        }catch (Exception ex){}
        try {
            String author = jsonObject.getString(AUTHOR);
            object.author = author;
        }catch (Exception ex){}
        try {
            String title = jsonObject.getString(TITLE);
            object.title = title;
        }catch (Exception ex){}
        try {
            String description = jsonObject.getString(DESCRIPTION);
            if (description.equals("[]"))
                object.description="";
            else
                object.description = description;
        }catch (Exception ex){}
        try {
            String imageHtmlStr = jsonObject.getString(IMAGE);
            Document photoHtml= Jsoup.parseBodyFragment(imageHtmlStr);
            String photoServerUri=photoHtml.body().getElementsByTag("img").attr("src") ;
            object.photoServerUri = photoServerUri;
        }catch (Exception ex){}
        try {
            String date = jsonObject.getString(DATE);
            object.date = date;
        }catch (Exception ex){}
        try {
            String record = jsonObject.getString(DIARY_RECORD);
            object.record = record;
        }catch (Exception ex){}
        try {
            JSONObject latlonObject = jsonObject.getJSONObject(LATLON);
            object.lattitude = latlonObject.getString(LATITUDE);
            object.longitude = latlonObject.getString(LONGITUDE);
        }catch (Exception ex){}
        try {
            JSONObject addressObject = jsonObject.getJSONObject(ADDRESS);
            String countryCode= addressObject.getString(COUNTRY_CODE);
            String province= addressObject.getString(PROVINCE);
            String city= addressObject.getString(CITY);
            String street= addressObject.getString(STREET);
            String postalCode= addressObject.getString(POSTAL_CODE);

            Address address=new Address(Locale.getDefault());
            if (countryCode!=null && !countryCode.isEmpty())
                address.setCountryCode(countryCode);
            if (province!=null && !province.isEmpty())
                address.setAdminArea(province);
            if (city!=null && !city.isEmpty())
                address.setLocality(city);
            if (street!=null && !street.isEmpty())
                address.setThoroughfare(street);
            if (postalCode!=null && !postalCode.isEmpty())
                address.setPostalCode(postalCode);
            object.address=GeoUtil.getFullAddress(address);
        }catch (Exception ex){}

        return object;
    }
    private void showNodeNotFoundView(){
        hideLoadingView();
        findViewById(R.id.invisible_fl_ObservationDetailActivity).setVisibility(View.VISIBLE);
        findViewById(R.id.content_ll_ObservationDetailActivity).setVisibility(View.GONE);
    }
    private void showNetworkErrorView(){
        Toast.makeText(this, R.string.network_error, Toast.LENGTH_SHORT).show();
        hideLoadingView();
    }
    private void hideScrollView(){
        //Hide the grid view
        ScrollView scrollView=(ScrollView)findViewById(R.id.content_sv_ObservationDetailActivity);
        scrollView.setVisibility(View.GONE);
    }
    private void showLoadingView(){
        rootLinearLayout.setVisibility(View.GONE);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
        swipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {
                swipeRefreshLayout.setRefreshing(true);
            }
        });
    }
    private void hideLoadingView(){
        swipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {
                swipeRefreshLayout.setRefreshing(false);
            }
        });
        swipeRefreshLayout.setVisibility(View.GONE);
        rootLinearLayout.setVisibility(View.VISIBLE);
    }
    private void showConfirmDeletePostDialog(final Context context){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.delete_post_msg_observationDetailActivity)
                .setTitle(R.string.delete_post_title_observationDetailActivity);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                String nid="";
                if (observationEntryObject!=null)
                    nid=observationEntryObject.nid;
                if (!nid.isEmpty()) {
                    new DeletePostTask(context).execute(nid);
                    NotificationUtil.showNotification(context, NotificationUtil.NotificationID.DELETE_USER_POST_NOTIFICATION_ID);
                }else {
                    Toast.makeText(context,R.string.failed_delete_observationDetailActivity,Toast.LENGTH_SHORT).show();
                }
                finish();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {/*Do nothing*/}
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void checkUserIsAuthor(){
        if (observationEntryObject==null)
        {
            userIsAuthor=false;
            return;
        }
        try{
            String author=observationEntryObject.author;
            if (author.equals(PreferenceUtil.getCurrentUser(this)))
                userIsAuthor = true;
            return;
        }catch (Exception ex){}
        userIsAuthor = false;
    }
    private void handleDeleteAction(){

        if (observationEntryObject!=null) {
            showConfirmDeletePostDialog(this);
        }
    }
}
