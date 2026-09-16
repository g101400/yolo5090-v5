package ncnn.v5lite.demo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity implements SurfaceHolder.Callback {

    public static final int REQUEST_CAMERA = 100;
    public static final int REQUEST_STORAGE = 101;

    private static final String TAG = "MainActivity";
    /** 同一批目标最短写盘间隔，避免刷屏写文件 */
    private static final long SAVE_INTERVAL_MS = 1500;

    private Ncnnv5lite ncnnyolov5 = new Ncnnv5lite();
    private int facing = 0;

    private Spinner spinnerModel;
    private Spinner spinnerCPUGPU;
    private int current_model = 0;
    private int current_cpugpu = 0;

    private SurfaceView cameraView;
    private TextView statusView;
    private TextView titleView;

    private DetectionStore store;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private long lastSaveMs = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        store = new DetectionStore(this);

        statusView = (TextView) findViewById(R.id.statusView);
        titleView = (TextView) findViewById(R.id.titleView);

        cameraView = (SurfaceView) findViewById(R.id.cameraview);
        cameraView.getHolder().setFormat(PixelFormat.RGBA_8888);
        cameraView.getHolder().addCallback(this);

        Button buttonSwitchCamera = (Button) findViewById(R.id.buttonSwitchCamera);
        buttonSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                int new_facing = 1 - facing;
                ncnnyolov5.closeCamera();
                ncnnyolov5.openCamera(new_facing);
                facing = new_facing;
            }
        });

        spinnerModel = (Spinner) findViewById(R.id.spinnerModel);
        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id) {
                if (position != current_model) {
                    current_model = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        spinnerCPUGPU = (Spinner) findViewById(R.id.spinnerCPUGPU);
        spinnerCPUGPU.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id) {
                if (position != current_cpugpu) {
                    current_cpugpu = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        ImageButton menuButton = (ImageButton) findViewById(R.id.menuButton);
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showOverflowMenu(v);
            }
        });

        ncnnyolov5.setDetectionListener(new Ncnnv5lite.DetectionListener() {
            @Override
            public void onDetections(final int[] labels, final float[] probs) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        updateStatus(labels, probs);
                        maybeSave(labels, probs);
                    }
                });
            }
        });

        reload();
        updateTitle();
    }

    private void reload() {
        boolean ret_init = ncnnyolov5.loadModel(getAssets(), current_model, current_cpugpu);
        if (!ret_init) {
            Log.e(TAG, "ncnnyolov5 loadModel failed");
            Toast.makeText(this, R.string.msg_model_load_failed, Toast.LENGTH_SHORT).show();
        } else {
            ncnnyolov5.setDetectionCallback();
        }
    }

    // ---------- 状态栏 ----------
    private void updateStatus(int[] labels, float[] probs) {
        if (statusView == null) return;
        if (labels == null || labels.length == 0) {
            statusView.setText(R.string.status_none);
            return;
        }
        // label -> 数量 / 最高置信度
        Map<Integer, int[]> agg = new LinkedHashMap<Integer, int[]>(); // [count, maxProb*100]
        for (int i = 0; i < labels.length; i++) {
            int[] v = agg.get(labels[i]);
            int p = Math.round((probs != null && i < probs.length ? probs[i] : 0f) * 100);
            if (v == null) {
                agg.put(labels[i], new int[]{1, p});
            } else {
                v[0]++;
                if (p > v[1]) v[1] = p;
            }
        }
        List<String> seg = new ArrayList<String>();
        for (Map.Entry<Integer, int[]> e : agg.entrySet()) {
            String name = Labels.cn(e.getKey());
            if (e.getValue()[0] > 1) {
                seg.add(name + " ×" + e.getValue()[0]);
            } else {
                seg.add(name);
            }
        }
        statusView.setText(getString(R.string.status_detected, labels.length, TextUtils.join("、", seg)));
    }

    // ---------- 结果落盘 ----------
    private void maybeSave(int[] labels, float[] probs) {
        if (labels == null || labels.length == 0) return;
        long now = System.currentTimeMillis();
        if (now - lastSaveMs < SAVE_INTERVAL_MS) return;
        lastSaveMs = now;

        String time = DetectionStore.now();
        List<DetectionStore.Item> items = new ArrayList<DetectionStore.Item>();
        for (int i = 0; i < labels.length; i++) {
            float p = (probs != null && i < probs.length) ? probs[i] : 0f;
            items.add(new DetectionStore.Item(time, Labels.cn(labels[i]), p));
        }
        store.append(items);
    }

    // ---------- 菜单 ----------
    private void showOverflowMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenuInflater().inflate(R.menu.main_menu, pm.getMenu());
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.menu_help) {
                    showHelp();
                    return true;
                } else if (id == R.id.menu_about) {
                    showAbout();
                    return true;
                }
                return false;
            }
        });
        pm.show();
    }

    private void updateTitle() {
        if (titleView != null) {
            titleView.setText(getString(R.string.app_title) + "  v" + versionName());
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.2.1";
        }
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_help)
                .setMessage(R.string.help_content)
                .setPositiveButton(R.string.btn_ok, null)
                .show();
    }

    private void showAbout() {
        String msg = getString(R.string.about_content,
                versionName(),
                "Downloads/" + "YOLOv5Lite" + "/detect_" + DetectionStore.today() + ".csv");
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_about)
                .setMessage(msg)
                .setPositiveButton(R.string.btn_ok, null)
                .show();
    }

    // ---------- 生命周期 ----------
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        ncnnyolov5.setOutputWindow(holder.getSurface());
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
        // Android 9- 写公共目录需要存储权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
        ncnnyolov5.openCamera(facing);
    }

    @Override
    public void onPause() {
        super.onPause();
        ncnnyolov5.closeCamera();
    }
}
