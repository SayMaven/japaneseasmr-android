package com.saymaven.downloader.japaneseasmr.data.model

enum class SourceType(val value: String) {
    DL_SITE("DL_SITE"),
    LOCAL_SINGLE("LOCAL_SINGLE"),
    LOCAL_FOLDER("LOCAL_FOLDER");

    companion object {
        fun fromValue(value: String?): SourceType {
            return when (value?.uppercase()) {
                "LOCAL_SINGLE" -> LOCAL_SINGLE
                "LOCAL_FOLDER" -> LOCAL_FOLDER
                else -> DL_SITE
            }
        }
    }
}
