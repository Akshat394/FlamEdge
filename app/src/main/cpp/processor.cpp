#include "processor.h"

#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

namespace edge {

namespace {
constexpr int CHANNELS_RGBA = 4;
}

bool process_nv21_to_rgba(const uint8_t* nv21, const FrameInfo& info, uint8_t* rgbaOut) {
    if (!nv21 || !rgbaOut || info.width <= 0 || info.height <= 0) {
        return false;
    }

    // Wrap NV21 as a single-channel plane with height * 1.5
    const cv::Mat yuv(info.height + info.height / 2, info.width, CV_8UC1, const_cast<uint8_t*>(nv21));

    // Convert NV21 directly to grayscale (avoids intermediate BGR)
    cv::Mat gray;
    cv::cvtColor(yuv, gray, cv::COLOR_YUV2GRAY_NV21);

    cv::Mat edges;
    cv::Canny(gray, edges, 80.0, 160.0);

    cv::Mat rgba(info.height, info.width, CV_8UC4, rgbaOut);
    cv::cvtColor(edges, rgba, cv::COLOR_GRAY2RGBA);

    return true;
}

}  // namespace edge

