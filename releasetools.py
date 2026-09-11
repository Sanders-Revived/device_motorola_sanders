#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

def FullOTA_InstallEnd(info):
  OTA_InstallEnd(info)

def IncrementalOTA_InstallEnd(info):
  OTA_InstallEnd(info)

def OTA_InstallEnd(info):
  info.script.Print("Configuring device-specific features...")
  info.script.AppendExtra('package_extract_file("install/bin/check_device.sh", "/tmp/check_device.sh");')
  info.script.AppendExtra('set_metadata("/tmp/check_device.sh", "uid", 0, "gid", 0, "mode", 0755);')
  info.script.AppendExtra('run_program("/tmp/check_device.sh");')
  info.script.AppendExtra('delete("/tmp/check_device.sh");')
