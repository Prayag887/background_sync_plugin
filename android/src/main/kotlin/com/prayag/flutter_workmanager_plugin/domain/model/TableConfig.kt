package com.prayag.flutter_workmanager_plugin.domain.model

data class TableConfig(
    val name: String,
    val apiTableName: String,
    val selectQuery: String,
    val insertQuery: String,
    val apiEndpoint: String
)