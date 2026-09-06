#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

from extract_utils.fixups_lib import (
    lib_fixups,
)
from extract_utils.fixups_blob import (
    blob_fixup,
    blob_fixups_user_type,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)

namespace_imports = [
    'device/motorola/sanders',
    'hardware/motorola',
    'hardware/qcom-caf/msm8996',
    'hardware/qcom-caf/wlan',
    'vendor/qcom/opensource/dataservices',
]

blob_fixups: blob_fixups_user_type = {

    'vendor/bin/charge_only_mode': blob_fixup()
        .add_needed('libmemset_shim.so'),

    'vendor/bin/hw/android.hardware.biometrics.fingerprint@2.1-fpcservice': blob_fixup()
        .binary_regex_replace(b'/firmware/image', b'/vendor/f/image'),

    'vendor/etc/init/android.hardware.biometrics.fingerprint@2.1-service.rc': blob_fixup()
        .regex_replace('group system input 9015', 'group system uhid input 9015'),

    (
        'vendor/bin/hw/android.hardware.biometrics.fingerprint@2.1-fpcservice',
        'vendor/lib/com.fingerprints.extension@1.0_vendor.so',
    ): blob_fixup()
        .replace_needed('libhidlbase.so', 'libhidlbase-v32.so'),

    'vendor/lib/hw/camera.msm8953.so': blob_fixup()
        .binary_regex_replace(b'service.bootanim.exit', b'service.bootanim.hold'),

    'vendor/lib/libmmcamera2_sensor_modules.so': blob_fixup()
        .binary_regex_replace(b'/system/etc/camera/', b'/vendor/etc/camera/'),

    'vendor/lib/libmot_gpu_mapper.so': blob_fixup()
        .replace_needed('libgui.so', 'libgui_shim_vendor.so'),

    (
        'vendor/lib/libmmcamera_vstab_module.so',
        'vendor/lib/libmmcamera2_stats_modules.so',
        'vendor/lib/libmmcamera_ppeiscore.so',
    ): blob_fixup()
        .replace_needed('libgui.so', 'libgui_shim_vendor.so')
        .remove_needed('libandroid.so'),

    (
        'vendor/lib/libchromaflash.so',
        'vendor/lib/libmmcamera_hdr_gb_lib.so',
        'vendor/lib/liboptizoom.so',
        'vendor/lib/libseemore.so',
        'vendor/lib/libtrueportrait.so',
        'vendor/lib/libts_detected_face_hal.so',
        'vendor/lib/libts_face_beautify_hal.so',
        'vendor/lib/libubifocus.so',
    ): blob_fixup()
        .replace_needed('libstdc++.so', 'libstdc++_vendor.so'),
}

module = ExtractUtilsModule(
    'sanders',
    'motorola',
    blob_fixups=blob_fixups,
    lib_fixups=lib_fixups,
    namespace_imports=namespace_imports,
)

if __name__ == '__main__':
    utils = ExtractUtils.device(module)
    utils.run()
