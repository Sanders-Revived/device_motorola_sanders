#!/bin/sh
#
# Copyright (C) 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

sku=$(getprop ro.boot.hardware.sku)

if [ "$sku" = "XT1802" ]; then
    echo "DTV variant detected ($sku) - keeping DTV files"
    exit 0
fi

echo "Non-DTV variant detected ($sku) - removing DTV files"

mounted=0
if ! mountpoint -q /product 2>/dev/null; then
    mkdir -p /product
    mount /dev/block/mapper/product /product 2>/dev/null || mount /product 2>/dev/null
    if mountpoint -q /product 2>/dev/null; then
        mounted=1
    fi
fi

mount -o remount,rw /product 2>/dev/null

if mountpoint -q /product 2>/dev/null; then
    rm -rf /product/app/DTVPlayer
    rm -rf /product/app/DTVService
    rm -f  /product/lib64/libdtvhal.so
    rm -f  /product/lib64/libdtvtuner.so
    rm -f  /product/etc/permissions/sku_XT1802/com.motorola.hardware.dtv.xml
    rm -f  /product/etc/permissions/sku_XT1802/mot_dtv_permissions.xml
    rmdir  /product/etc/permissions/sku_XT1802 2>/dev/null

    if [ $mounted -eq 1 ]; then
        umount /product 2>/dev/null
    fi
    echo "DTV files removed successfully"
else
    echo "Warning: /product is not mounted rw in recovery"
fi
