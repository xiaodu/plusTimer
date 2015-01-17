package com.pluscubed.plustimer.ui;

import android.app.ListFragment;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.util.SparseArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.helper.DateAsXAxisLabelFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.pluscubed.plustimer.R;
import com.pluscubed.plustimer.model.PuzzleType;
import com.pluscubed.plustimer.model.Session;
import com.pluscubed.plustimer.model.Solve;
import com.pluscubed.plustimer.utils.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * History SessionList Fragment
 */

public class HistorySessionListFragment extends ListFragment {

    private static final String STATE_PUZZLETYPE_DISPLAYNAME =
            "puzzletype_displayname";

    private String mPuzzleTypeName;

    private TextView mStatsText;

    private GraphView mGraph;

    private ActionMode mActionMode;

    private boolean mMillisecondsEnabled;

    @Override
    public void onPause() {
        super.onPause();
        PuzzleType.valueOf(mPuzzleTypeName).getHistorySessions().save
                (getActivity());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PUZZLETYPE_DISPLAYNAME, mPuzzleTypeName);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        PuzzleType.initialize(getActivity());
        if (savedInstanceState != null) {
            mPuzzleTypeName = savedInstanceState.getString
                    (STATE_PUZZLETYPE_DISPLAYNAME);
        } else {
            mPuzzleTypeName = PuzzleType.getCurrent().name();
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initSharedPrefs();
        getListView().setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
        getListView().setMultiChoiceModeListener(new AbsListView
                .MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode mode,
                                                  int position, long id,
                                                  boolean checked) {

            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                getActivity().getMenuInflater().inflate(R.menu
                        .context_solve_or_session_list, menu);
                mActionMode = mode;
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.context_solvelist_delete_menuitem:
                        for (int i = getListView().getCount() - 1; i >= 0;
                             i--) {
                            if (getListView().isItemChecked(i)) {
                                PuzzleType.valueOf(mPuzzleTypeName)
                                        .getHistorySessions()
                                        .deleteSession((Session) getListView
                                                        ().getItemAtPosition(i),
                                                getActivity());
                            }
                        }
                        mode.finish();
                        onSessionListChanged();
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                mActionMode = null;
            }
        });
        LinearLayout headerView = (LinearLayout) getActivity()
                .getLayoutInflater()
                .inflate(R.layout.history_sessionlist_header, getListView(),
                        false);
        mStatsText = (TextView) headerView
                .findViewById(R.id.history_sessionlist_header_stats_textview);
        mGraph = new GraphView(getActivity());
        LinearLayout.LayoutParams layoutParams = new LinearLayout
                .LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Util.convertDpToPx(getActivity(), 220));
        layoutParams.setMargins(0, 0, 0, Util.convertDpToPx(getActivity(), 20));
        mGraph.setLayoutParams(layoutParams);
        mGraph.getLegendRenderer().setVisible(true);
        mGraph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);
        // set date label formatter
        mGraph.getGridLabelRenderer().setLabelFormatter(new DateAsXAxisLabelFormatter(getActivity()) {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                    return super.formatLabel(value, true);
                } else {
                    return Util.timeStringFromNs((long) value, mMillisecondsEnabled);
                }
            }
        });
        mGraph.getGridLabelRenderer().setNumHorizontalLabels(3); // only 4 because of the space
        /*mGraph.setCustomLabelFormatter(new CustomLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                    return Util.timeDateStringFromTimestamp(getActivity()
                            .getApplicationContext(), (long) value);
                } else {
                    return Util.timeStringFromNs((long) value,
                            mMillisecondsEnabled);
                }

            }
        });*/
        /*mGraph.setDrawDataPoints(true);
        mGraph.setDataPointsRadius(Util.convertDpToPx(getActivity(), 3));*/
        mGraph.getViewport().setXAxisBoundsManual(true);
        mGraph.getViewport().setYAxisBoundsManual(true);
        headerView.addView(mGraph, 1);
        getListView().addHeaderView(headerView, null, false);
        try {
            setListAdapter(new SessionListAdapter());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onSessionListChanged() {
        updateStats();
        ((SessionListAdapter) getListAdapter()).onSessionListChanged();
    }

    public void updateStats() {
        PuzzleType.valueOf(mPuzzleTypeName).getHistorySessions().sort();
        List<Session> historySessions = PuzzleType.valueOf(mPuzzleTypeName)
                .getHistorySessions().getList();
        if (historySessions.size() > 0) {
            StringBuilder s = new StringBuilder();

            /* STATS TEXT */

            //Get best solves of each history session and add to list
            ArrayList<Solve> bestSolvesOfSessionsArray = new ArrayList<>();
            for (Session session : historySessions) {
                bestSolvesOfSessionsArray.add(Util.getBestSolveOfList(session
                        .getSolves()));
            }

            //Add PB of all historySessions
            s.append(getString(R.string.pb)).append(": ")
                    .append(Util.getBestSolveOfList(bestSolvesOfSessionsArray)
                            .getTimeString(mMillisecondsEnabled));

            //Add PB of Ao5,12,50,100,1000
            s.append(getBestAverageOfNumberOfSessions(new int[]{1000, 100,
                            50, 12, 5},
                    historySessions));

            mStatsText.setText(s.toString());

            /* AVERAGES SERIES */


            //Get the timestamps of each session, and put in a SparseArray
            ArrayList<Long> sessionTimestamps = new ArrayList<>();
            for (Session session : historySessions) {
                sessionTimestamps.add(session.getTimestamp());
            }

            //This SparseArray contains one SparseArray<Long> for each average number (5,12,etc)
            //Each SparseArray<Long> contains the best averages (of the average number) of each history session
            //Essentially, array of series
            SparseArray<SparseArray<Long>> bestAveragesOfSessions = new SparseArray<>();
            for (int averageNumber : new int[]{5, 12, 50, 100, 1000}) {
                //One series
                SparseArray<Long> timesOfBestAveragesOfAvgNmbr = new SparseArray<>();
                for (int i = 0; i < historySessions.size(); i++) {
                    Session session = historySessions.get(i);
                    if (session.getNumberOfSolves() >= averageNumber) {
                        long bestAverage = session.getBestAverageOf(averageNumber);
                        if (bestAverage != Long.MAX_VALUE
                                &&
                                bestAverage != Session.GET_AVERAGE_INVALID_NOT_ENOUGH) {
                            timesOfBestAveragesOfAvgNmbr.put(i, bestAverage);
                        }
                    }
                }
                if (timesOfBestAveragesOfAvgNmbr.size() > 0) {
                    bestAveragesOfSessions.put(averageNumber, timesOfBestAveragesOfAvgNmbr);
                }

            }

            //Array of series (of data points) -> maps to bestAveragesOfSesion
            ArrayList<LineGraphSeries<DataPoint>> bestAverageGraphViewSeries
                    = new ArrayList<>();
            for (int i = 0; i < bestAveragesOfSessions.size(); i++) {
                //One series
                SparseArray<Long> averageSeriesArray = bestAveragesOfSessions.valueAt(i);
                if (averageSeriesArray.size() > 0) {
                    DataPoint[] averageSeriesDataPoints = new DataPoint[averageSeriesArray.size()];
                    for (int k = 0; k < averageSeriesArray.size(); k++) {
                        averageSeriesDataPoints[k] = new DataPoint(
                                sessionTimestamps.get(averageSeriesArray.keyAt(k)),
                                averageSeriesArray.valueAt(k)
                        );
                    }
                    int lineColor = Color.RED;
                    switch (bestAveragesOfSessions.keyAt(i)) {
                        case 5:
                            lineColor = Color.RED;
                            break;
                        case 12:
                            lineColor = Color.GREEN;
                            break;
                        case 50:
                            lineColor = Color.MAGENTA;
                            break;
                        case 100:
                            lineColor = Color.BLACK;
                            break;
                        case 1000:
                            lineColor = Color.YELLOW;
                    }
                    LineGraphSeries<DataPoint> averageSeries = new LineGraphSeries<>(
                            averageSeriesDataPoints
                    );
                    averageSeries.setThickness(Util.convertDpToPx(getActivity(), 2));
                    averageSeries.setDrawDataPoints(true);
                    averageSeries.setDataPointsRadius(Util.convertDpToPx(getActivity(), 3));
                    averageSeries.setTitle(String.format(getString(R.string.bao), bestAveragesOfSessions.keyAt(i)));
                    averageSeries.setColor(lineColor);
                    bestAverageGraphViewSeries.add(averageSeries);
                }
            }

            /* BEST TIMES SERIES */

            //Get best times of each session excluding DNF,
            // and create GraphViewData array bestTimes
            SparseArray<Long> bestTimesOfSessionsArray = new SparseArray<>();
            for (int i = 0; i < historySessions.size(); i++) {
                Session session = historySessions.get(i);
                if (Util.getBestSolveOfList(session.getSolves()).getPenalty() != Solve.Penalty.DNF) {
                    bestTimesOfSessionsArray.put(
                            i,
                            Util.getBestSolveOfList(session.getSolves())
                                    .getTimeTwo()
                    );
                }
            }
            DataPoint[] bestTimesSeriesDataPoints
                    = new DataPoint[bestTimesOfSessionsArray.size()];
            for (int i = 0; i < bestTimesOfSessionsArray.size(); i++) {
                bestTimesSeriesDataPoints[i] = new DataPoint(
                        sessionTimestamps.get(bestTimesOfSessionsArray.keyAt(i)),
                        bestTimesOfSessionsArray.valueAt(i)
                );
            }

            LineGraphSeries<DataPoint> bestTimesSeries = new LineGraphSeries<>(
                    bestTimesSeriesDataPoints
            );

            bestTimesSeries.setThickness(Util.convertDpToPx(getActivity(), 2));
            bestTimesSeries.setDrawDataPoints(true);
            bestTimesSeries.setDataPointsRadius(Util.convertDpToPx(getActivity(), 3));
            bestTimesSeries.setTitle(String.format(getString(R.string.best_times)));
            bestTimesSeries.setColor(Color.BLUE);

            boolean averageMoreThanOne = false;
            for (int i = 0; i < bestAveragesOfSessions.size(); i++) {
                if (bestAveragesOfSessions.valueAt(i).size() > 1) {
                    averageMoreThanOne = true;
                }
            }
            if (averageMoreThanOne || bestTimesOfSessionsArray.size() > 1) {
                mGraph.setVisibility(View.VISIBLE);
                mGraph.removeAllSeries();
                mGraph.addSeries(bestTimesSeries);
                for (LineGraphSeries<DataPoint> averageSeries :
                        bestAverageGraphViewSeries) {
                    mGraph.addSeries(averageSeries);
                }

                ArrayList<Long> allPointsValue = new ArrayList<>();
                for (int i = 0; i < bestAveragesOfSessions.size(); i++) {
                    for (int k = 0; k < bestAveragesOfSessions.valueAt(i).size();
                         k++) {
                        allPointsValue.add(bestAveragesOfSessions.valueAt(i)
                                .valueAt(k));
                    }
                }
                for (int i = 0; i < bestTimesOfSessionsArray.size(); i++) {
                    allPointsValue.add(bestTimesOfSessionsArray.valueAt(i));
                }

                //Set bounds for Y
                long lowestValue = Collections.min(allPointsValue);
                long highestValue = Collections.max(allPointsValue);
                //Check to make sure the minimum bound is more than 0 (if
                // yes, set bound to 0)
                mGraph.getViewport().setMinY(
                        lowestValue - (highestValue - lowestValue) * 0.1 >= 0 ?
                                lowestValue - (highestValue - lowestValue) * 0.1
                                : 0);
                mGraph.getViewport().setMaxY(highestValue + (highestValue -
                        lowestValue) * 0.1);

                long firstTimestamp = Long.MAX_VALUE;
                for (int i = 0; i < bestAveragesOfSessions.size(); i++) {
                    if (sessionTimestamps.get(bestAveragesOfSessions.valueAt(i)
                            .keyAt(0))
                            < firstTimestamp) {
                        firstTimestamp = sessionTimestamps
                                .get(bestAveragesOfSessions.valueAt(i).keyAt(0));
                    }
                }
                if (sessionTimestamps.get(bestTimesOfSessionsArray.keyAt(0)) <
                        firstTimestamp) {
                    firstTimestamp = sessionTimestamps.get(bestTimesOfSessionsArray.keyAt(0));
                }

                //Set bounds for X

                long lastTimestamp = Long.MIN_VALUE;
                for (int i = 0; i < bestAveragesOfSessions.size(); i++) {
                    if (sessionTimestamps.get(bestAveragesOfSessions.valueAt(i)
                            .keyAt(bestAveragesOfSessions.valueAt(i).size() - 1))
                            > lastTimestamp) {
                        lastTimestamp = sessionTimestamps.get
                                (bestAveragesOfSessions.valueAt(i)
                                        .keyAt(bestAveragesOfSessions.valueAt(i)
                                                .size() -
                                                1));
                    }
                }
                if (sessionTimestamps.get(bestTimesOfSessionsArray.keyAt
                        (bestTimesOfSessionsArray.size() - 1))
                        > lastTimestamp) {
                    lastTimestamp = sessionTimestamps
                            .get(bestTimesOfSessionsArray.keyAt(bestTimesOfSessionsArray.size()
                                    - 1));
                }
                mGraph.getViewport().setMinX(
                        firstTimestamp - (lastTimestamp - firstTimestamp) * 0.1
                );
                mGraph.getViewport().setMaxX(
                        lastTimestamp + (lastTimestamp - firstTimestamp) * 0.1
                );
            } else {
                mGraph.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Returns string with best averages of [numbers].
     *
     * @param numbers  the numbers for the averages
     * @param sessions list of sessions
     * @return String with the best averages of [numbers]
     */
    public String getBestAverageOfNumberOfSessions(int[] numbers,
                                                   List<Session> sessions) {
        StringBuilder builder = new StringBuilder();
        for (int number : numbers) {
            ArrayList<Long> bestAverages = new ArrayList<>();
            if (sessions.size() > 0) {
                for (Session session : sessions) {
                    long bestAverage = session.getBestAverageOf(number);
                    if (bestAverage != Session.GET_AVERAGE_INVALID_NOT_ENOUGH) {
                        //If the average is possible for the number
                        bestAverages.add(bestAverage);
                    }
                }
                if (bestAverages.size() > 0) {
                    Long bestAverage = Collections.min(bestAverages);
                    builder.append("\n").append(getString(R.string.pb))
                            .append(" ")
                            .append(String.format(getString(R.string.ao),
                                    number)).append(": ")
                            .append(bestAverage == Long.MAX_VALUE ? "DNF"
                                    : Util.timeStringFromNs(bestAverage,
                                    mMillisecondsEnabled));
                }
            }
        }
        return builder.toString();
    }

    public void finishActionMode() {
        if (mActionMode != null) {
            mActionMode.finish();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_history_sessionlist, menu);

        Spinner menuPuzzleSpinner = (Spinner) MenuItemCompat.getActionView
                (menu.findItem(R.id
                        .menu_activity_history_sessionlist_puzzletype_spinner));
        ArrayAdapter<PuzzleType> puzzleTypeSpinnerAdapter = new
                SpinnerPuzzleTypeAdapter(getActivity().getLayoutInflater(),
                ((ActionBarActivity) getActivity()).getSupportActionBar()
                        .getThemedContext());
        menuPuzzleSpinner.setAdapter(puzzleTypeSpinnerAdapter);
        menuPuzzleSpinner.setSelection(puzzleTypeSpinnerAdapter.getPosition
                (PuzzleType.valueOf(mPuzzleTypeName)), true);
        menuPuzzleSpinner.setOnItemSelectedListener(new AdapterView
                .OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                mPuzzleTypeName = (parent.getItemAtPosition(position))
                        .toString();
                onSessionListChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history_sessionlist,
                container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        //Update list when session is deleted in HistorySolveList
        onSessionListChanged();
        //update puzzle spinner in case settings were changed
        getActivity().invalidateOptionsMenu();
        initSharedPrefs();
    }

    private void initSharedPrefs() {
        mMillisecondsEnabled = PreferenceManager.getDefaultSharedPreferences
                (getActivity()).getBoolean(SettingsActivity
                .PREF_MILLISECONDS_CHECKBOX, true);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Intent i = new Intent(getActivity(), HistorySolveListActivity.class);
        int index = PuzzleType.valueOf(mPuzzleTypeName).getHistorySessions()
                .getList().indexOf(l.getItemAtPosition(position));
        i.putExtra(HistorySolveListActivity.EXTRA_HISTORY_SESSION_POSITION,
                index);
        i.putExtra(HistorySolveListActivity
                .EXTRA_HISTORY_PUZZLETYPE_DISPLAYNAME, mPuzzleTypeName);
        startActivity(i);
    }

    public class SessionListAdapter extends ArrayAdapter<Session> {

        SessionListAdapter() throws IOException {
            super(getActivity(), 0, new ArrayList<Session>());
            onSessionListChanged();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getActivity().getLayoutInflater().inflate(R
                        .layout.list_item_single_line, parent, false);
            }
            Session session = getItem(position);
            TextView text = (TextView) convertView.findViewById(android.R.id
                    .text1);
            text.setText(session.getTimestampString(getActivity()));

            return convertView;
        }

        public void onSessionListChanged() {
            clear();
            List<Session> sessions = PuzzleType.valueOf(mPuzzleTypeName)
                    .getHistorySessions().getList();
            Collections.reverse(sessions);
            addAll(sessions);
            notifyDataSetChanged();
        }

    }

}
