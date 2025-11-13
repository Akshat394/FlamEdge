#pragma once

#include <cstdint>

namespace edge {

struct FrameInfo {
    int width{};
    int height{};
};

bool process_nv21_to_rgba(const uint8_t* nv21, const FrameInfo& info, uint8_t* rgbaOut);

}

