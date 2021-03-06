package com.deange.gimgur.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.deange.gimgur.R;
import com.deange.gimgur.misc.Utils;
import com.deange.gimgur.model.ImageResult;
import com.deange.gimgur.model.ImgurAlbum;
import com.deange.gimgur.model.QueryResponse;
import com.deange.gimgur.net.HttpTask;
import com.deange.gimgur.net.UrlConstants;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ImageFragment extends Fragment implements PrefetchAdapter.OnPrefetchListener, ImageAdapter.OnItemChangeListener, View.OnClickListener {

    public static final String TAG = ImageFragment.class.getSimpleName();

    private ImageAdapter mAdapter;
    private TextView mItemsText;
    private ImageButton mUploadButton;
    private ListView mListview;
    private FrameLayout mBlockingView;

    private RefreshAffordanceProvider mRefreshAffordanceProvider = Fallback.INSTANCE;

    private final HttpTask.HttpCallback<QueryResponse> GET_CALLBACK = new HttpTask.HttpCallback<QueryResponse>() {
        @Override
        public void onHttpResponseReceived(final QueryResponse queryResult) {

            mRefreshAffordanceProvider.onRefreshAffordanceRequested(false);

            // Gotta make sure we are still attached to the activity!
            if ((getActivity() != null) && (queryResult != null)) {
                mAdapter.addResponse(queryResult);
                mAdapter.notifyDataSetChanged();
            }
        }
    };

    private final HttpTask.HttpCallback<ImgurAlbum> POST_CALLBACK = new HttpTask.HttpCallback<ImgurAlbum>() {
        @Override
        public void onHttpResponseReceived(final ImgurAlbum album) {

            mBlockingView.setVisibility(View.GONE);
            mRefreshAffordanceProvider.onRefreshAffordanceRequested(false);

            if ((getActivity() != null) && (album != null)) {

                final String title = getString(R.string.intent_share_album);
                final String albumUrl = UrlConstants.getImgurAlbum(album.getId());

                for (int i = 0; i < mAdapter.getCount(); i++) {
                    final ImageResult result = mAdapter.getItem(i);
                    result.setSelected(false);
                }

                // Hack for existing items already being displayed
                for (int i = 0; i < mListview.getChildCount(); i++) {
                    if (mListview.getChildAt(i) != null) {
                        ((CompoundButton) mListview.getChildAt(i).findViewById(R.id.list_item_compound)).setChecked(false);
                    }
                }

                updateText(0);

                new AlertDialog.Builder(getActivity())
                        .setTitle(title)
                        .setMessage(albumUrl)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialog, final int which) {
                            }
                        })
                        .setNeutralButton(R.string.dialog_share, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialog, final int which) {
                                final Intent i = new Intent(Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(Intent.EXTRA_SUBJECT, title)
                                        .putExtra(Intent.EXTRA_TEXT, albumUrl);
                                startActivity(Intent.createChooser(i, title));
                            }
                        })
                        .setNegativeButton(R.string.dialog_open, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialog, final int which) {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(albumUrl)));
                            }
                        })
                        .show();
            }
        }
    };

    public ImageFragment() {
        setRetainInstance(true);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        Log.v(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        mAdapter = new ImageAdapter(getActivity(), this);
        mAdapter.setOnPrefetchListener(this);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {

        final View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        mBlockingView = (FrameLayout) rootView.findViewById(R.id.fragment_main_blocking);
        mListview = (ListView) rootView.findViewById(R.id.fragment_main_grid_view);
        mListview .setAdapter(mAdapter);

        final ViewGroup viewGroup = (ViewGroup) rootView.findViewById(R.id.fragment_main_upload_root);
        Utils.setLayoutTransition(viewGroup);

        mUploadButton = (ImageButton) rootView.findViewById(R.id.fragment_main_upload);
        mUploadButton.setOnClickListener(this);

        mItemsText = (TextView) rootView.findViewById(R.id.fragment_main_items);
        updateText(0);

        return rootView;
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        Log.v(TAG, "onActivityCreated()");
        super.onActivityCreated(savedInstanceState);
    }

    public void doQuery(final String queryString) {
        final URL url;

        if (!TextUtils.isEmpty(queryString)) {
            try {

                if (!TextUtils.equals(queryString, mAdapter.getCurrentQuery())) {
                    mAdapter.clear();
                }

                if (mAdapter.getLastResponse() == null) {
                    url = new URL(UrlConstants.getQueryUrl(queryString));

                } else {
                    url = new URL(mAdapter.getLastResponse().getNextUrl());
                }

                mAdapter.setCurrentQuery(queryString);
                mRefreshAffordanceProvider.onRefreshAffordanceRequested(true);
                HttpTask.get(url, QueryResponse.class, GET_CALLBACK);

            } catch (final MalformedURLException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateText(final int itemsSaved) {
        final boolean isZero = (itemsSaved == 0);

        mItemsText.setText(isZero ? "" : String.valueOf(itemsSaved));
        mItemsText.setVisibility(isZero ? View.GONE : View.VISIBLE);
        mUploadButton.setColorFilter(isZero ? Color.LTGRAY : Color.TRANSPARENT);
        mUploadButton.setEnabled(!isZero);
    }

    private void handleUpload() {

        final List<String> urls = new ArrayList<>();

        for (int i = 0; i < mAdapter.getCount(); i++) {
            final ImageResult imageResult = mAdapter.getItem(i);

            if (imageResult.isSelected()) {
                urls.add(imageResult.getUrl());
            }
        }

        mBlockingView.setVisibility(View.VISIBLE);
        mRefreshAffordanceProvider.onRefreshAffordanceRequested(true);
        HttpTask.post(urls, POST_CALLBACK);
    }

    public void setRefreshAffordanceProvider(final RefreshAffordanceProvider refreshAffordanceProvider) {
        Log.v(TAG, "setRefreshAffordanceProvider()");

        mRefreshAffordanceProvider = refreshAffordanceProvider == null
                ? Fallback.INSTANCE : refreshAffordanceProvider;
    }

    @Override
    public void onPrefetchRequested(final int position) {
        doQuery(mAdapter.getCurrentQuery());
    }

    @Override
    public void onItemStateChanged(final int position) {

        int itemsSaved = 0;

        for (int i = 0; i < mAdapter.getCount(); i++) {
            final ImageResult imageResult = mAdapter.getItem(i);

            if (imageResult.isSelected()) {
                itemsSaved++;
            }
        }

        updateText(itemsSaved);
    }

    @Override
    public void onClick(final View v) {
        switch (v.getId()) {
            case R.id.fragment_main_upload:
                handleUpload();
                break;
        }
    }

    public interface RefreshAffordanceProvider {
        public void onRefreshAffordanceRequested(final boolean show);
    }

    private static final class Fallback implements RefreshAffordanceProvider {
        public static final Fallback INSTANCE = new Fallback();

        @Override
        public void onRefreshAffordanceRequested(final boolean show) {
            Log.w(TAG, "Fallback: onRefreshAffordanceRequested()");
        }
    }
}
