/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#include <atomic>
#include <cstddef>
#include <new>
#include <string>
#include <utility>

#include <ui/ANativeObjectBase.h>
#include <ui/GraphicBuffer.h>
#include <utils/RefBase.h>

namespace {

using android::GraphicBuffer;
using android::RefBase;
using android::sp;
using android::status_t;

std::atomic<const void*> proxyVtable{nullptr};

// The EIS blob allocates only 0x88 bytes; the Motorola GPU mapper allocates
// 0xa0 bytes. Never construct a current GraphicBuffer in either allocation.
// Keep a small RefBase object there and own a separately allocated buffer.
class LegacyGraphicBuffer final
    : public android::ANativeObjectBase<ANativeWindowBuffer, LegacyGraphicBuffer, RefBase> {
public:
    explicit LegacyGraphicBuffer(const sp<GraphicBuffer>& buffer) : mBuffer(buffer) {
        const ANativeWindowBuffer* native = buffer->getNativeBuffer();
        width = native->width;
        height = native->height;
        stride = native->stride;
        format = native->format;
        usage_deprecated = native->usage_deprecated;
        usage = native->usage;
        layerCount = native->layerCount;
        handle = native->handle;
        proxyVtable.store(*reinterpret_cast<const void* const*>(this), std::memory_order_release);
    }

    GraphicBuffer* buffer() const { return mBuffer.get(); }

private:
    ~LegacyGraphicBuffer() override = default;
    sp<GraphicBuffer> mBuffer;
};

static_assert(sizeof(void*) == 4, "These camera blobs are ARM32 only");
// The mapper reads the native handle directly at object + 0x44. RefBase is
// the primary base at offset zero and the native buffer follows its 8 bytes.
static_assert(sizeof(RefBase) == 8, "Must preserve the stock primary base layout");
static_assert(offsetof(ANativeWindowBuffer, handle) == 0x3c,
        "Must preserve the stock native handle offset");
static_assert(sizeof(LegacyGraphicBuffer) <= 0x88, "Must fit the smallest stock allocation");

GraphicBuffer* unwrap(const void* object) {
    if (*reinterpret_cast<const void* const*>(object) ==
        proxyVtable.load(std::memory_order_acquire)) {
        return static_cast<const LegacyGraphicBuffer*>(object)->buffer();
    }
    // BufferQueue/GLConsumer also return real GraphicBuffers to the mapper.
    return const_cast<GraphicBuffer*>(static_cast<const GraphicBuffer*>(object));
}

void* wrap(void* object, const sp<GraphicBuffer>& buffer) {
    return new (object) LegacyGraphicBuffer(buffer);
}

}  // namespace

// Only the two blobs' GraphicBuffer imports are renamed to SandersBuffer.
// This avoids interposing libui methods used by platform libui/libgui.
extern "C" {

void* _ZN7android13SandersBufferC1EjjijjP13native_handleb(
        void* object, uint32_t width, uint32_t height, int32_t format,
        uint32_t usage, uint32_t stride, native_handle_t* handle, bool keepOwnership) {
    return wrap(object, sp<GraphicBuffer>::make(handle,
            keepOwnership ? GraphicBuffer::TAKE_HANDLE : GraphicBuffer::WRAP_HANDLE,
            width, height, format, 1, static_cast<uint64_t>(usage), stride));
}

void* _ZN7android13SandersBufferC1EjjijjjP13native_handleb(
        void* object, uint32_t width, uint32_t height, int32_t format,
        uint32_t layerCount, uint32_t usage, uint32_t stride,
        native_handle_t* handle, bool keepOwnership) {
    return wrap(object, sp<GraphicBuffer>::make(handle,
            keepOwnership ? GraphicBuffer::TAKE_HANDLE : GraphicBuffer::WRAP_HANDLE,
            width, height, format, layerCount, static_cast<uint64_t>(usage), stride));
}

void* _ZN7android13SandersBufferC1EjjijNSt3__112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEE(
        void* object, uint32_t width, uint32_t height, int32_t format,
        uint32_t usage, std::string requestorName) {
    return wrap(object, sp<GraphicBuffer>::make(width, height, format, 1,
            static_cast<uint64_t>(usage), std::move(requestorName)));
}

ANativeWindowBuffer* _ZNK7android13SandersBuffer15getNativeBufferEv(const void* object) {
    return unwrap(object)->getNativeBuffer();
}

status_t _ZNK7android13SandersBuffer9initCheckEv(const void* object) {
    return unwrap(object)->initCheck();
}

status_t _ZN7android13SandersBuffer4lockEjPPvPiS3_(
        void* object, uint32_t usage, void** address, int32_t* bytesPerPixel,
        int32_t* bytesPerStride) {
    return unwrap(object)->lock(usage, address, bytesPerPixel, bytesPerStride);
}

status_t _ZN7android13SandersBuffer6unlockEv(void* object) {
    return unwrap(object)->unlock();
}

void _ZN7android13SandersBuffer26dumpAllocationsToSystemLogEv() {
    GraphicBuffer::dumpAllocationsToSystemLog();
}

}  // extern "C"
