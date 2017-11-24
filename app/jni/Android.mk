LOCAL_PATH:= $(call my-dir)

#编译好的 FFmpeg 头文件目录
INCLUDE_PATH:=/home/guoyuefeng/Desktop/jiangwu/codes/ffmpeg-3.3.5/Android/arm/include

#编译好的 FFmpeg 动态库目录
FFMPEG_LIB_PATH:=/home/guoyuefeng/Desktop/jiangwu/codes/ffmpeg-3.3.5/Android/arm/lib

include $(CLEAR_VARS)
LOCAL_MODULE:= libavcodec
LOCAL_SRC_FILES:= $(FFMPEG_LIB_PATH)/libavcodec-57.so
LOCAL_EXPORT_C_INCLUDES := $(INCLUDE_PATH)
include $(PREBUILT_SHARED_LIBRARY)
 
include $(CLEAR_VARS)
LOCAL_MODULE:= libavformat
LOCAL_SRC_FILES:= $(FFMPEG_LIB_PATH)/libavformat-57.so
LOCAL_EXPORT_C_INCLUDES := $(INCLUDE_PATH)
include $(PREBUILT_SHARED_LIBRARY)
 
include $(CLEAR_VARS)
LOCAL_MODULE:= libswscale
LOCAL_SRC_FILES:= $(FFMPEG_LIB_PATH)/libswscale-4.so
LOCAL_EXPORT_C_INCLUDES := $(INCLUDE_PATH)
include $(PREBUILT_SHARED_LIBRARY)
 
include $(CLEAR_VARS)
LOCAL_MODULE:= libavutil
LOCAL_SRC_FILES:= $(FFMPEG_LIB_PATH)/libavutil-55.so
LOCAL_EXPORT_C_INCLUDES := $(INCLUDE_PATH)
include $(PREBUILT_SHARED_LIBRARY)
 
include $(CLEAR_VARS)
LOCAL_MODULE:= libavfilter
LOCAL_SRC_FILES:= $(FFMPEG_LIB_PATH)/libavfilter-6.so
LOCAL_EXPORT_C_INCLUDES := $(INCLUDE_PATH)
include $(PREBUILT_SHARED_LIBRARY)
 
include $(CLEAR_VARS)
LOCAL_MODULE:= libswresample
LOCAL_SRC_FILES:= $(FFMPEG_LIB_PATH)/libswresample-2.so
LOCAL_EXPORT_C_INCLUDES := $(INCLUDE_PATH)
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE:= libpostproc
LOCAL_SRC_FILES:= $(FFMPEG_LIB_PATH)/libpostproc-54.so
LOCAL_EXPORT_C_INCLUDES := $(INCLUDE_PATH)
#include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE:= libavdevice
LOCAL_SRC_FILES:= $(FFMPEG_LIB_PATH)/libavdevice-57.so
LOCAL_EXPORT_C_INCLUDES := $(INCLUDE_PATH)
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := ffmpeg
LOCAL_SRC_FILES := net_majorkernelpanic_jni_FFmpegJni.c \
                  cmdutils.c \
                  ffmpeg.c \
                  ffmpeg_opt.c \
                  ffmpeg_filter.c   
LOCAL_C_INCLUDES := /home/guoyuefeng/Desktop/jiangwu/codes/ffmpeg-3.3.5
LOCAL_LDLIBS := -lm -llog
LOCAL_SHARED_LIBRARIES := libavcodec libavfilter libavformat libavutil libswresample libswscale libavdevice
include $(BUILD_SHARED_LIBRARY)
