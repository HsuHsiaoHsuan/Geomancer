package tacoball.com.geomancer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import tacoball.com.geomancer.checkupdate.FileUpdateManager;

/**
 * 地圖與資料庫更新程式
 */
public class UpdateToolFragment extends Fragment {

    private static final String TAG = "MapUpdaterFragment";

    // 進入主畫面前的刻意等待時間
    private static final long RESTART_DELAY = 3000;

    // 介面元件
    TextView mTxvAction; // 步驟說明文字
    ProgressBar mPgbAction; // 進度條
    Button mBtnRepair; // 修復按鈕

    // 資源元件
    Handler mHandler;
    FileUpdateManager fum;

    // 狀態值
    int fileIndex = 0;

    /**
     * 準備動作
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.fragment_updater, container, false);

        mTxvAction = (TextView) layout.findViewById(R.id.txvAction);

        mPgbAction = (ProgressBar) layout.findViewById(R.id.pgbAction);
        mPgbAction.setProgress(0);

        mBtnRepair = (Button) layout.findViewById(R.id.btnRepair);
        mBtnRepair.setVisibility(View.INVISIBLE);
        mBtnRepair.setOnClickListener(repairListener);

        mHandler = new Handler();

        // 設定版本字串
        try {
            PackageInfo packageInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
            TextView txvVersion = (TextView) layout.findViewById(R.id.txvVersion);
            txvVersion.setText(packageInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, e.getMessage());
        }

        // 檢查網路連線
        boolean hasNetwork = false;
        ConnectivityManager connMgr = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        for (NetworkInfo ni : connMgr.getAllNetworkInfo()) {
            if (ni.isConnected()) {
                hasNetwork = true;
                break;
            }
        }

        if (hasNetwork) {
            try {
                // 啟動檔案更新循環
                String fileURL = MainUtils.getRemoteURL(fileIndex);
                File saveTo = MainUtils.getSavePath(getActivity(), fileIndex);
                fum = new FileUpdateManager();
                fum.setListener(listener);
                fum.checkVersion(fileURL, saveTo);
            } catch (IOException ex) {
                mTxvAction.setText(R.string.prompt_cannot_access_storage);
            }
        } else {
            mTxvAction.setText(R.string.prompt_cannot_access_network);
        }

        return layout;
    }

    /**
     * 善後動作
     */
    @Override
    public void onDestroy() {
        // 取消線上更新，並且不理會後續事件，避免 Activity 結束後閃退
        if (fum != null) {
            fum.cancel();
            fum.unsetListener();
        }

        super.onDestroy();
    }

    /**
     * 顯示新進度
     *
     * @param step     步驟
     * @param progress 進度
     */
    private void setProgress(final String step, final int progress) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                String msg = String.format(Locale.getDefault(), "%s %d%%", step, progress);
                mTxvAction.setText(msg);
                mPgbAction.setProgress(progress);
            }
        });
    }

    /**
     * 顯示錯誤訊息與修復按鈕
     *
     * @param msg 錯誤訊息
     */
    private void setErrorMessage(final String msg) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mTxvAction.setText(msg);
                mBtnRepair.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * 重新啟動 App
     */
    private void gotoMap() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Activity activity = getActivity();
                if (activity == null) return;

                Intent restartIntent = new Intent(activity, MainActivity.class);
                activity.finish();
                activity.startActivity(restartIntent);
            }
        }, RESTART_DELAY);
    }

    // 自動更新非同步流程管理
    private FileUpdateManager.ProgressListener listener = new FileUpdateManager.ProgressListener() {

        @Override
        public void onCheckVersion(long length, long mtime) {
            try {
                File saveTo = MainUtils.getSavePath(getActivity(), fileIndex);
                if (length > 0) {
                    String fileURL = MainUtils.getRemoteURL(fileIndex);
                    File gzfile = new File(saveTo, MainUtils.REQUIRED_FILES[fileIndex] + ".gz");

                    if (gzfile.exists()) {
                        fum.repair(fileURL, saveTo);
                    } else {
                        fum.update(fileURL, saveTo);
                    }
                } else {
                    long mtimeMin = MainUtils.REQUIRED_MTIME[fileIndex];
                    if (fum.isRequired(MainUtils.getRemoteURL(fileIndex), saveTo, mtimeMin)) {
                        fum.update(MainUtils.getRemoteURL(fileIndex), saveTo);
                    } else {
                        checkNext();
                    }
                }
            } catch (IOException ex) {
                Log.e(TAG, MainUtils.getReason(ex));
            }
        }

        @Override
        public void onNewProgress(int step, int percent) {
            setProgress(getStepName(step, true), percent);
        }

        @Override
        public void onComplete() {
            checkNext();
        }

        @Override
        public void onCancel() {
            Log.i(TAG, getString(R.string.log_cancel_update));
        }

        @Override
        public void onError(int step, String reason) {
            String pat = getString(R.string.pattern_update_error);
            String msg = String.format(Locale.getDefault(), pat, getStepName(step, false));
            setErrorMessage(msg);
            Log.e(TAG, reason);
        }

        // 繼續處理下一個檔案
        private void checkNext() {
            fileIndex++;
            if (fileIndex < MainUtils.REQUIRED_FILES.length) {
                try {
                    String fileURL = MainUtils.getRemoteURL(fileIndex);
                    File saveTo = MainUtils.getSavePath(getActivity(), fileIndex);
                    fum.checkVersion(fileURL, saveTo);
                } catch (IOException ex) {
                    Log.e(TAG, MainUtils.getReason(ex));
                }
            } else {
                // If total length is zero, activity would restart infinitely.
                gotoMap();
            }
        }

        // 步驟值轉換步驟名稱
        private String getStepName(int step, boolean withTarget) {
            String stepname = getString(R.string.term_preparing);
            switch (step) {
                case FileUpdateManager.STEP_DOWNLOAD:
                    stepname = getString(R.string.term_downloading);
                    break;
                case FileUpdateManager.STEP_EXTRACT:
                    stepname = getString(R.string.term_extracting);
                    break;
                case FileUpdateManager.STEP_REPAIR:
                    stepname = getString(R.string.term_repairing);
            }

            if (withTarget) {
                String[] targets = {
                        getString(R.string.term_map),
                        getString(R.string.term_unluckyhouse_db),
                        getString(R.string.term_unluckylabor_db)
                };
                return String.format(Locale.getDefault(), "%s%s", stepname, targets[fileIndex]);
            }

            return stepname;
        }

    };

    // 修復按鈕點擊事件，開始修復檔案與隱藏修復按鈕
    private View.OnClickListener repairListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mBtnRepair.setVisibility(View.INVISIBLE);
            String fileURL = MainUtils.getRemoteURL(fileIndex);
            try {
                File saveTo = MainUtils.getSavePath(getActivity(), fileIndex);
                fum.repair(fileURL, saveTo);
            } catch (IOException ex) {
                Log.e(TAG, MainUtils.getReason(ex));
            }
        }
    };

}
