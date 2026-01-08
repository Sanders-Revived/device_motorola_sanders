$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, vendor/lineage/config/common_full_phone.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/non_ab_device.mk)

# Device
$(call inherit-product, $(LOCAL_PATH)/device.mk)

PRODUCT_BRAND := motorola
PRODUCT_DEVICE := sanders
PRODUCT_MANUFACTURER := motorola
PRODUCT_MODEL := Moto G (5S) Plus
PRODUCT_NAME := lineage_sanders

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildFingerprint="motorola/sanders/sanders:8.1.0/OPSS28.65-36-11-4/b6557:user/release-keys" \
    BuildDesc="sanders-user 8.1.0 OPSS28.65-36-11-4 b6557 release-keys" \
    DeviceName=sanders

