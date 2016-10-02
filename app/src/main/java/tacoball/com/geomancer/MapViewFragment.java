package tacoball.com.geomancer;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.mapsforge.core.model.BoundingBox;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import tacoball.com.geomancer.view.PointInfo;
import tacoball.com.geomancer.view.TaiwanMapView;

/**
 * 測量風水程式
 */
public class MapViewFragment extends Fragment {

    private static final String TAG = "MapViewFragment";

    // 介面元件
    @BindView(R.id.mapView) MapView mMapView;        // 地圖
    // 狀態列
    @BindView(R.id.txvLocation) TextView mTxvLocation;     // 經緯度文字
    @BindView(R.id.txvZoomValue) TextView mTxvZoom;        // 縮放比文字
    @BindView(R.id.txvAzimuthValue) TextView mTxvAzimuth;  // 方位角文字
    @BindView(R.id.txvHint) TextView mTxvHint;             // 地圖放大提示訊息
    // 按鈕列
    @BindView(R.id.btPosition) Button mBtPosition;         // 定位按鈕
    @BindView(R.id.btMeasure) Button mBtMeasure;           // 測量風水按鈕
    // POI 詳細資訊
    @BindView(R.id.txvSummaryContent) TextView txvSummaryContent;
    @BindView(R.id.txvURLContent) TextView txvURLContent;
    @BindView(R.id.glyPointInfo) ViewGroup vgInfoContainer;


    // 資源元件
    private SQLiteDatabase mUnluckyHouseDB;
    private SQLiteDatabase mUnluckyLaborDB;

    // ButterKnife
    private Unbinder unbinder;

    /**
     * 準備動作
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map_view, container, false);
        unbinder = ButterKnife.bind(this, view);

        // 地圖
//        mMapView.setMyLocationImage(R.drawable.arrow_up);
//        mMapView.setInfoView(vgInfoContainer, txvSummaryContent, txvURLContent);
        org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants.setUserAgentValue(BuildConfig.APPLICATION_ID);
        mMapView.setTileSource(TileSourceFactory.MAPNIK);
        mMapView.setBuiltInZoomControls(true);
        mMapView.setMultiTouchControls(true);

        // 事件配置
//        mBtPosition.setOnClickListener(mClickListener);
//        mBtMeasure.setOnClickListener(mClickListener);
//        mMapView.setStateChangeListener(mMapStateListener);

        // 資料庫配置
        try {
            String path;
            path = MainUtils.getFilePath(getActivity(), 1).getAbsolutePath();
            mUnluckyHouseDB = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
            path = MainUtils.getFilePath(getActivity(), 2).getAbsolutePath();
            mUnluckyLaborDB = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
        } catch (IOException ex) {
            Log.e(TAG, ex.getMessage());
        }

        return view;
    }

    /**
     * 善後動作
     */
    @Override
    public void onDestroyView() {
        mMapView.destroyAll();

        if (mUnluckyHouseDB != null) {
            mUnluckyHouseDB.close();
        }
        if (mUnluckyLaborDB != null) {
            mUnluckyLaborDB.close();
        }

        super.onDestroyView();

        unbinder.unbind();
    }

    /**
     * 定位與測量風水按鈕事件處理
     */
    private View.OnClickListener mClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            // 定位
            if (v == mBtPosition) {
                mMapView.gotoMyPosition();
            }

            // 測量風水
            if (v == mBtMeasure) {
                // 查詢前準備
                int uhcnt = 0;
                int ulcnt = 0;

                BoundingBox bbox = mMapView.getBoundingBox();

                String[] params = {
                        Double.toString(bbox.minLatitude),
                        Double.toString(bbox.maxLatitude),
                        Double.toString(bbox.minLongitude),
                        Double.toString(bbox.maxLongitude)
                };

                String sql;
                Cursor cur;

                List<PointInfo> infolist = new ArrayList<>();

                // FIXME maybe use query is better
                // 查詢凶宅
                sql = "SELECT id,approach,area,address,lat,lng FROM unluckyhouse " +
                        "WHERE state>1 AND lat>=? AND lat<=? AND lng>=? AND lng<=?";
                cur = mUnluckyHouseDB.rawQuery(sql, params);
                try {
                    while (cur.moveToNext()) {
                        String pat = getString(R.string.pattern_unluckyhouse_subject);
                        String url = String.format(Locale.getDefault(), "http://unluckyhouse.com/showthread.php?t=%d", cur.getInt(0));
                        String subject = String.format(Locale.getDefault(), pat, cur.getString(1));
                        String address = cur.getString(3);
                        double lat = cur.getDouble(4);
                        double lng = cur.getDouble(5);
                        PointInfo p = new PointInfo(lat, lng, subject, R.drawable.pin_unluckyhouse, R.drawable.pin_unluckyhouse_bright);
                        p.setDescription(address);
                        p.setURL(url);
                        infolist.add(p);

                        uhcnt++;
                    }
                } finally {
                    cur.close();
                }

                // FIXME maybe use query is better
                // 查詢血汗工廠
                sql = "SELECT id,doc_id,corp,law,boss,dt_exe,gov,lat,lng FROM unluckylabor " +
                        "WHERE lat>=? AND lat<=? AND lng>=? AND lng<=?";
                cur = mUnluckyLaborDB.rawQuery(sql, params);
                try {
                    while (cur.moveToNext()) {
                        String corp = cur.getString(cur.getColumnIndex("corp"));
                        String doc_id = cur.getString(cur.getColumnIndex("doc_id"));
                        String law = cur.getString(cur.getColumnIndex("law"));
                        String boss = cur.getString(cur.getColumnIndex("boss"));
                        String dt_exe = cur.getString(cur.getColumnIndex("dt_exe"));
                        String gov = cur.getString(cur.getColumnIndex("gov"));
                        double lat = cur.getDouble(cur.getColumnIndex("lat"));
                        double lng = cur.getDouble(cur.getColumnIndex("lng"));

                        StringBuilder law_desc = new StringBuilder();
                        String[] rules = law.split(";");
                        for (String rule : rules) {
                            if (law_desc.length() > 0) {
                                law_desc.append('\n');
                            }
                            law_desc.append(rule.replaceFirst("(\\d+)", "勞動基準法第$1條").replaceFirst("\\-(\\d+)", "第$1項"));
                        }

                        String pat = getString(R.string.pattern_unluckylabor_subject);
                        String subject = String.format(Locale.getDefault(), pat, corp);
                        String detail = String.format(Locale.getDefault(), "%s (%s)\n%s %s\n違反勞基法項目：%s", doc_id, dt_exe, corp, boss, law_desc.toString());
                        String url = "";
                        if (gov.equals("臺北市")) {
                            url = "http://web2.bola.taipei/bolasearch/chhtml/page/20?q47=" + corp;
                        }
                        if (gov.equals("新北市")) {
                            url = "http://ilabor.ntpc.gov.tw/cloud/Violate/filter?name1=" + corp;
                        }
                        if (gov.equals("高雄市")) {
                            url = "http://labor.kcg.gov.tw/IllegalList.aspx?appname=IllegalList";
                        }
                        PointInfo p = new PointInfo(lat, lng, subject, R.drawable.pin_unluckylabor, R.drawable.pin_unluckylabor_bright);
                        p.setDescription(detail);
                        p.setURL(url);
                        infolist.add(p);
                        ulcnt++;
                    }
                } finally {
                    cur.close();
                }

                // 配置 POI Marker
                if (infolist.size() > 0) {
                    PointInfo[] infoary = new PointInfo[infolist.size()];
                    infolist.toArray(infoary);
                    infolist.clear();

                    mMapView.showPoints(infoary);
                    String pat = getString(R.string.pattern_measure_result);
                    String msg = String.format(Locale.getDefault(), pat, uhcnt, ulcnt);
                    Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity(), R.string.term_peace, Toast.LENGTH_SHORT).show();
                }
            }
        }

    };

    /**
     * 同步地圖狀態值 (經緯度、縮放比、方位角)，限制 Z>=15 才允許測量風水
     */
    private TaiwanMapView.StateChangeListener mMapStateListener = new TaiwanMapView.StateChangeListener() {

        @Override
        public void onStateChanged(TaiwanMapView.State state) {
            String txtLoc = String.format(Locale.getDefault(), "(%.4f, %.4f)", state.cLat, state.cLng);
            mTxvLocation.setText(txtLoc);

            String txtZoom = String.format(Locale.getDefault(), "%s", state.zoom);
            mTxvZoom.setText(txtZoom);

            String txtAzimuth = String.format(Locale.getDefault(), "%.2f", state.myAzimuth);
            mTxvAzimuth.setText(txtAzimuth);

//            if (state.zoom >= 15) {
            mTxvHint.setVisibility(View.INVISIBLE);
            mBtMeasure.setEnabled(true);
//            } else {
//                mTxvHint.setVisibility(View.VISIBLE);
//                mBtMeasure.setEnabled(false);
//            }
        }

    };

}
