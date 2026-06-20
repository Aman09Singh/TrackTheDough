package com.example.t1.ui.navigation

object AppRoute {
    const val HOME = "home"
    const val TRANSACTION_LIST = "transaction_list"
    const val ANALYTICS = "analytics"
    const val PERMISSIONS = "permissions"
    const val TRANSACTION_DETAIL = "transaction_detail/{id}"

    fun transactionDetail(id: Long) = "transaction_detail/$id"
}
