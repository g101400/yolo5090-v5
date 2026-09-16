package ncnn.v5lite.demo;

import android.content.res.AssetManager;
import android.util.Log;
import android.view.Surface;

public class Ncnnv5lite {

    /** 检测结果回调（由 native 相机线程调用，不可直接操作 UI）。 */
    public interface DetectionListener {
        /**
         * @param labels 类别下标数组
         * @param probs  对应置信度 0~1
         */
        void onDetections(int[] labels, float[] probs);
    }

    private DetectionListener listener;

    public native boolean loadModel(AssetManager mgr, int modelid, int cpugpu);

    public native boolean openCamera(int facing);

    public native boolean closeCamera();

    public native boolean setOutputWindow(Surface surface);

    /** 开启逐帧检测结果回传；必须在 loadModel 之后调用。 */
    public native boolean setDetectionCallback();

    public void setDetectionListener(DetectionListener l) {
        this.listener = l;
    }

    /** native 回调入口：payload 形如 "0:0.92;2:0.87;"，空串表示本帧无目标。 */
    private void onDetections(String payload) {
        if (listener == null || payload == null || payload.isEmpty()) {
            if (listener != null) listener.onDetections(new int[0], new float[0]);
            return;
        }
        try {
            String[] parts = payload.split(";");
            int n = parts.length;
            int[] labels = new int[n];
            float[] probs = new float[n];
            int m = 0;
            for (String p : parts) {
                if (p.isEmpty()) continue;
                int idx = p.indexOf(':');
                if (idx <= 0) continue;
                labels[m] = Integer.parseInt(p.substring(0, idx));
                probs[m] = Float.parseFloat(p.substring(idx + 1));
                m++;
            }
            if (m != n) {
                int[] l2 = new int[m];
                float[] p2 = new float[m];
                System.arraycopy(labels, 0, l2, 0, m);
                System.arraycopy(probs, 0, p2, 0, m);
                labels = l2;
                probs = p2;
            }
            listener.onDetections(labels, probs);
        } catch (Exception e) {
            Log.w("Ncnnv5lite", "解析检测结果失败: " + payload, e);
        }
    }

    static {
        System.loadLibrary("ncnnv5lite");
    }
}
