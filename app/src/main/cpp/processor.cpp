#include "processor.h"

#ifndef NO_OPENCV
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#endif

namespace edge {

namespace {
constexpr int CHANNELS_RGBA = 4;
}

bool process_nv21_to_rgba(const uint8_t* nv21, const FrameInfo& info, uint8_t* rgbaOut) {
    if (!nv21 || !rgbaOut || info.width <= 0 || info.height <= 0) {
        return false;
    }

#ifdef NO_OPENCV
    return false;
#else
    const cv::Mat yuv(info.height + info.height / 2, info.width, CV_8UC1, const_cast<uint8_t*>(nv21));
    cv::Mat gray;
    cv::cvtColor(yuv, gray, cv::COLOR_YUV2GRAY_NV21);
    cv::Mat edges;
    cv::Canny(gray, edges, 80.0, 160.0);
    cv::Mat rgba(info.height, info.width, CV_8UC4, rgbaOut);
    cv::cvtColor(edges, rgba, cv::COLOR_GRAY2RGBA);
    return true;
#endif
}

}  // namespace edge

