package net.radekw8733.antygarb.ai;

import net.radekw8733.antygarb.ai.CameraInferenceUtil;

import java.util.Map;

public interface KeypointsReturn {
    void returnKeypoints(Map<String, CameraInferenceUtil.Keypoint> keypoints);
}
