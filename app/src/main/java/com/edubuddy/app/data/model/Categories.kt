package com.edubuddy.app.data.model

import com.edubuddy.app.data.db.TxnType

/** Money categories. Keys are persisted — never rename an existing key. */
data class Category(val key: String, val label: String, val type: TxnType)

object Categories {
    val expense = listOf(
        Category("food", "Food & drinks", TxnType.EXPENSE),
        Category("transport", "Transport", TxnType.EXPENSE),
        Category("study", "Books & study", TxnType.EXPENSE),
        Category("rent", "Rent & hostel", TxnType.EXPENSE),
        Category("recharge", "Mobile & internet", TxnType.EXPENSE),
        Category("shopping", "Shopping", TxnType.EXPENSE),
        Category("fun", "Entertainment", TxnType.EXPENSE),
        Category("health", "Health", TxnType.EXPENSE),
        Category("other", "Other", TxnType.EXPENSE),
    )
    val income = listOf(
        Category("pocket_money", "Pocket money", TxnType.INCOME),
        Category("scholarship", "Scholarship", TxnType.INCOME),
        Category("part_time", "Part-time / freelance", TxnType.INCOME),
        Category("gift", "Gift", TxnType.INCOME),
        Category("other_income", "Other income", TxnType.INCOME),
    )
    private val byKey = (expense + income).associateBy { it.key }

    fun of(key: String): Category = byKey[key] ?: Category(key, key.replaceFirstChar { it.uppercase() }, TxnType.EXPENSE)
    fun forType(type: TxnType) = if (type == TxnType.EXPENSE) expense else income
}
