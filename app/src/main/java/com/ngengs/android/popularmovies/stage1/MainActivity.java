package com.ngengs.android.popularmovies.stage1;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BaseTransientBottomBar;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ngengs.android.popularmovies.stage1.adapters.MovieListAdapter;
import com.ngengs.android.popularmovies.stage1.data.MoviesDetail;
import com.ngengs.android.popularmovies.stage1.data.MoviesList;
import com.ngengs.android.popularmovies.stage1.globals.Values;
import com.ngengs.android.popularmovies.stage1.utils.GridSpacesItemDecoration;
import com.ngengs.android.popularmovies.stage1.utils.MoviesDBService;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity implements Callback<MoviesList> {
    private static final String TAG = "MainActivity";

    private RecyclerView rv;
    private ProgressBar progressBar;
    private TextView textMessage;
    private ImageView imageTools;
    private View tools;
    private SwipeRefreshLayout swipeRefreshLayout;
    private GridLayoutManager layoutManager;
    private Snackbar snackbar;

    private MoviesDBService moviesDBService;
    private Call<MoviesList> callService;
    private MovieListAdapter adapter;

    private int sortType;
    private int pageTotal = 1;
    private int pageNow = 0;
    private boolean forceRefresh;
    private boolean loading;
    private boolean fromPagination;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loading = false;
        fromPagination = false;

        rv = (RecyclerView) findViewById(R.id.recyclerView);
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefresh);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        textMessage = (TextView) findViewById(R.id.textTools);
        imageTools = (ImageView) findViewById(R.id.imageTools);
        tools = findViewById(R.id.tools);

        // Make sure all view not visible
        rv.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);

        layoutManager = new GridLayoutManager(this, 2);
        adapter = new MovieListAdapter(this, null, new MovieListAdapter.ClickListener() {
            @Override
            public void OnClickListener(int position, View view) {
                Intent intent = new Intent(MainActivity.this, DetailMovieActivity.class);
                intent.putExtra("DATA", adapter.get(position));
                startActivity(intent);
            }
        });
        rv.setLayoutManager(layoutManager);
        rv.setAdapter(adapter);
        rv.addItemDecoration(new GridSpacesItemDecoration(2, getResources().getDimensionPixelSize(R.dimen.grid_spacing)));
        rv.setHasFixedSize(true);
        rv.setNestedScrollingEnabled(false);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @SuppressWarnings("EmptyMethod")
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                boolean endHasBeenReached = (firstVisibleItemPosition + visibleItemCount + 5) >= totalItemCount;

                if (!loading && pageNow < pageTotal && endHasBeenReached) {
                    Log.d(TAG, "onScrolled: CatchData");
                    forceRefresh = false;
                    fromPagination = true;
                    if (sortType == Values.TYPE_POPULAR) getPopularMovies();
                    else getTopRatedMovies();
                }
            }
        });

        imageTools.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sortType == Values.TYPE_POPULAR) getPopularMovies();
                else getTopRatedMovies();
            }
        });

        forceRefresh = false;
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                forceRefresh = true;
                pageNow = 0;
                pageTotal = 1;
                fromPagination = false;
                if (sortType == Values.TYPE_POPULAR) getPopularMovies();
                else getTopRatedMovies();
            }
        });

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Values.URL_BASE)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        moviesDBService = retrofit.create(MoviesDBService.class);

        sortType = Values.TYPE_POPULAR;
        if (savedInstanceState != null) {
            sortType = savedInstanceState.getInt("SORT_TYPE", Values.TYPE_POPULAR);
            pageNow = savedInstanceState.getInt("PAGE_NOW", 0);
            pageTotal = savedInstanceState.getInt("PAGE_TOTAL", 1);
            Serializable temp = savedInstanceState.getSerializable("DATA");
            if (temp != null) {
                adapter.clear();
                //noinspection unchecked
                adapter.add((List) temp);
                rv.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                tools.setVisibility(View.GONE);
                changeTitle();
            }
        } else {
            changeTitle();
            getPopularMovies();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (adapter.getItemCount() > 0) {
            List<MoviesDetail> data = adapter.get();
            outState.putSerializable("DATA", new ArrayList<>(data));
            outState.putInt("PAGE_NOW", pageNow);
            outState.putInt("PAGE_TOTAL", pageTotal);
            outState.putInt("SORT_TYPE", sortType);
        }
    }

    private void changeTitle() {
        if (sortType == Values.TYPE_POPULAR)
            setTitle(getResources().getString(R.string.title_popular));
        else
            setTitle(getResources().getString(R.string.title_top_rated));
    }

    private void getPopularMovies() {
        if (moviesDBService != null && pageNow < pageTotal) {
            loading = true;
            if (!forceRefresh) progressBar.setVisibility(View.VISIBLE);
            tools.setVisibility(View.GONE);
            if (callService != null) callService.cancel();
            callService = moviesDBService.listMoviesPopular(pageNow + 1);
            callService.enqueue(this);
        }
    }

    private void getTopRatedMovies() {
        if (moviesDBService != null && pageNow < pageTotal) {
            loading = true;
            if (!forceRefresh) progressBar.setVisibility(View.VISIBLE);
            tools.setVisibility(View.GONE);
            if (callService != null) callService.cancel();
            callService = moviesDBService.listMoviesTopRated(pageNow + 1);
            callService.enqueue(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_popular, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_sort_by_popular:
                forceRefresh = true;
                pageNow = 0;
                pageTotal = 1;
                sortType = Values.TYPE_POPULAR;
                loading = false;
                if (!item.isChecked()) {
                    changeTitle();
                    getPopularMovies();
                }
                break;
            case R.id.menu_sort_by_top_rated:
                forceRefresh = true;
                pageNow = 0;
                pageTotal = 1;
                sortType = Values.TYPE_HIGH_RATED;
                loading = false;
                if (!item.isChecked()) {
                    changeTitle();
                    getTopRatedMovies();
                }
                break;
        }
        item.setChecked(true);
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResponse(@NonNull Call<MoviesList> call, @NonNull Response<MoviesList> response) {
        if (progressBar.getVisibility() == View.VISIBLE) progressBar.setVisibility(View.GONE);
        if (forceRefresh) {
            adapter.clear();
            forceRefresh = false;
        }
        if (rv.getVisibility() == View.GONE) rv.setVisibility(View.VISIBLE);
        if (swipeRefreshLayout.isRefreshing()) swipeRefreshLayout.setRefreshing(false);

        if (snackbar != null) {
            snackbar.dismiss();
            snackbar = null;
        }
        if (tools.getVisibility() == View.VISIBLE) tools.setVisibility(View.GONE);
        loading = false;
        fromPagination = false;
        MoviesList responseBody = response.body();
        if (responseBody != null) {
            pageTotal = responseBody.getTotalPage();
            pageNow = responseBody.getPage();
            List<MoviesDetail> movies = responseBody.getMovies();
            if (responseBody.getStatusMessage() == null && movies.size() > 0) {
                adapter.add(movies);
            }
        }
    }

    @Override
    public void onFailure(@NonNull Call<MoviesList> call, @NonNull Throwable t) {
        if (progressBar.getVisibility() == View.VISIBLE) progressBar.setVisibility(View.GONE);
        if (forceRefresh) forceRefresh = false;
        if (swipeRefreshLayout.isRefreshing()) swipeRefreshLayout.setRefreshing(false);
        if (rv.getVisibility() == View.VISIBLE && adapter.getItemCount() == 0)
            rv.setVisibility(View.GONE);
        loading = false;

        if (fromPagination) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                imageTools.setImageDrawable(getResources().getDrawable(R.drawable.ic_refresh_white, null));
            } else {
                //noinspection deprecation
                imageTools.setImageDrawable(getResources().getDrawable(R.drawable.ic_refresh_white));
            }
            textMessage.setText(R.string.error_next_page);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                imageTools.setImageDrawable(getResources().getDrawable(R.drawable.ic_cloud_off_white, null));
            } else {
                //noinspection deprecation
                imageTools.setImageDrawable(getResources().getDrawable(R.drawable.ic_cloud_off_white));
            }
            textMessage.setText(R.string.error_no_connection);
        }
        tools.setVisibility(View.VISIBLE);

        if (snackbar != null) snackbar.dismiss();
        snackbar = Snackbar.make(textMessage, R.string.error_cant_get_data_check_connection, BaseTransientBottomBar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.retry, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sortType == Values.TYPE_POPULAR) getPopularMovies();
                else getTopRatedMovies();
            }
        });
        snackbar.show();
    }
}
