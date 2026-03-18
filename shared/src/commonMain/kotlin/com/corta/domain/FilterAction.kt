package com.corta.domain

enum class FilterAction {
    ALLOW,
    BLOCK,
    MUTE,
    WARN;

    companion object {
        fun fromString(value: String): FilterAction {
            return entries.find { it.name == value } ?: ALLOW
        }
    }
}
