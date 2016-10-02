package tacoball.com.geomancer;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import tacoball.com.geomancer.checkupdate.FileUpdateManager;
import tacoball.com.geomancer.checkupdate.NetworkReceiver;
import tacoball.com.geomancer.event.Event_Update;
import tacoball.com.geomancer.view.TaiwanMapView;

/**
 * 前端程式進入點
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final boolean SIMULATE_OLD_MTIME = false;

    private Fragment current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 配置 Android 繪圖資源，必須在 inflate 之前完成
        AndroidGraphicFactory.createInstance(getApplication());
        setContentView(R.layout.activity_main);

        // 清理儲存空間
        MainUtils.cleanStorage(this);

        // 配置 Fragment
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

        try {
            // 使用者要求更新檢查
            boolean userRequest = MainUtils.hasUpdateRequest(this);
            MainUtils.clearUpdateRequest(this);

            // 模擬第二個檔案太舊
            if (SIMULATE_OLD_MTIME) {
                File f = MainUtils.getFilePath(this, 2);
                long otime = MainUtils.REQUIRED_MTIME[2] - 86400000;
                if (!f.setLastModified(otime)) {
                    Log.e(TAG, "Cannot set mtime.");
                }
            }

            // 必要檔案更新檢查
            int cnt = 0;
            FileUpdateManager fum = new FileUpdateManager();
            for (int i = 0; i < MainUtils.REQUIRED_FILES.length; i++) {
                File saveTo = MainUtils.getSavePath(this, i);
                long mtimeMin = MainUtils.REQUIRED_MTIME[i];
                if (fum.isRequired(MainUtils.getRemoteURL(i), saveTo, mtimeMin)) {
                    String msg = String.format(Locale.getDefault(), "檔案 %d 需要強制更新", i);
                    Log.d(TAG, msg);
                    cnt++;
                }
            }
            boolean needRequirements = (cnt > 0);

            if (needRequirements || userRequest) {
                // 更新程式
                current = UpdateToolFragment.newInstance();
            } else {
                // 主畫面程式
                current = new MapViewFragment();
            }
            ft.replace(R.id.frag_container, current);
            ft.addToBackStack(null);
            ft.commit();
        } catch (IOException ex) {
            // MainUtils.getFilePath() 發生錯誤
            Log.e(TAG, ex.getMessage());
        } finally {
            checkDebugParameters();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // 配置廣播接收器
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    // 地毯式檢查用到的除錯參數
    private void checkDebugParameters() {
        int cnt = 0;

        if (FileUpdateManager.forceDownloadFailed) {
            Log.w(TAG, getString(R.string.log_simulate_download_failed));
            cnt++;
        }

        if (FileUpdateManager.forceRepairFailed) {
            Log.w(TAG, getString(R.string.log_simulate_repair_failed));
            cnt++;
        }

        if (!NetworkReceiver.ENABLE_INTERVAL) {
            Log.w(TAG, getString(R.string.log_disable_interval_limit));
            cnt++;
        }

        if (!NetworkReceiver.ENABLE_PROBABILITY) {
            Log.w(TAG, getString(R.string.log_disable_probability_limit));
            cnt++;
        }

        if (MainUtils.MIRROR_NUM != 0) {
            Log.w(TAG, getString(R.string.log_use_debugging_mirror));
            cnt++;
        }

        if (TaiwanMapView.SEE_DEBUGGING_POINT) {
            Log.w(TAG, getString(R.string.log_see_debugging_point));
            cnt++;
        }

        if (SIMULATE_OLD_MTIME) {
            Log.w(TAG, getString(R.string.log_simulate_old_mtime));
            cnt++;
        }

        if (cnt > 0) {
            String pat = getString(R.string.pattern_enable_debugging);
            String msg = String.format(Locale.getDefault(), pat, cnt);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    // 從地圖介面切換到更新介面
    private void swapToUpdateUI() {
        Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (current instanceof MapViewFragment) {
                    FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                    ft.remove(current);
                    ft.add(R.id.frag_container, new UpdateToolFragment());
                    ft.commit();
                }
            }
        });
    }

    // 廣播接收器，處理使用者更新要求用
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(Event_Update event) {
        Log.d(TAG, "Request update from broadcast.");
        MainUtils.clearUpdateRequest(MainActivity.this);
        swapToUpdateUI();
    }
}
