package net.radekw8733.antygarb;

import java.util.Map;

public interface KeypointsReturn {
    void returnKeypoints(Map<String, CameraInferenceUtil.Keypoint> keypoints);
}
