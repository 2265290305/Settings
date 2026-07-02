LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := AutoBluetoothService
LOCAL_SRC_FILES := $(call all-java-files-under, src)  # 包含 src 目录下所有 Java 文件
LOCAL_PRIVATE_PLATFORM_APIS := true
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true

include $(BUILD_PACKAGE)
