// SPDX-License-Identifier: Apache-2.0
/*
 * Compatibility export for the legacy MSM8953 camera blob.
 * Bridge its pre-R GraphicBuffer constructor ABI to current libui.
 * Its legacy GL references are renamed during extraction.
 */

#include <stdint.h>

#define PPEISCORE_EXPORT __attribute__((visibility("default")))

struct native_handle;
using native_handle_t = native_handle;

/*
 * Keep the C++ names readable while binding them to the exact constructor
 * symbols referenced by the pre-R blob and exported by current libui.
 */
extern "C" void graphic_buffer_constructor_r(
        void* thisptr, uint32_t width, uint32_t height, int32_t format,
        uint32_t layer_count, uint32_t usage, uint32_t stride,
        native_handle_t* handle, bool keep_ownership)
        __asm__("_ZN7android13GraphicBufferC1EjjijjjP13native_handleb");

extern "C" PPEISCORE_EXPORT void graphic_buffer_constructor_legacy(
        void* thisptr, uint32_t width, uint32_t height, int32_t format,
        uint32_t usage, uint32_t stride, native_handle_t* handle,
        bool keep_ownership)
        __asm__("_ZN7android13GraphicBufferC1EjjijjP13native_handleb");

extern "C" PPEISCORE_EXPORT void graphic_buffer_constructor_legacy(
        void* thisptr, uint32_t width, uint32_t height, int32_t format,
        uint32_t usage, uint32_t stride, native_handle_t* handle,
        bool keep_ownership) {
    graphic_buffer_constructor_r(
            thisptr, width, height, format, 1, usage, stride, handle,
            keep_ownership);
}

#undef PPEISCORE_EXPORT
