package com.example.t1.domain.usecase

import com.example.t1.domain.model.Category
import com.example.t1.domain.model.TransactionType
import javax.inject.Inject

class AutoCategorizer @Inject constructor() {

    fun categorize(
        merchant: String?,
        description: String,
        type: TransactionType = TransactionType.DEBIT,
    ): Category {
        val text = buildString {
            merchant?.let { append(it.lowercase()); append(" ") }
            append(description.lowercase())
        }

        // INCOME: credit transactions with salary/payroll keywords
        if (type == TransactionType.CREDIT) {
            if (INCOME_KEYWORDS.any { it in text }) return Category.INCOME
        }

        return KEYWORD_MAP.entries
            .firstOrNull { (keywords, _) -> keywords.any { it in text } }
            ?.value
            ?: Category.OTHERS
    }

    companion object {
        private val INCOME_KEYWORDS = listOf("salary", "payroll", "stipend", "dividend", "bonus")

        // Order matters — more specific keywords first to avoid false matches
        private val KEYWORD_MAP = linkedMapOf(
            // Food delivery
            listOf("swiggy", "zomato", "magicpin", "magic pin", "dunzo") to Category.FOOD_ORDER,
            // Food dining
            listOf("restaurant", "cafe", "coffee", "bistro", "dhaba", "eatery", "pizz") to Category.FOOD_DINING,
            // Transport
            listOf("uber", "ola cab", "rapido", "dmrc", "metro", "irctc train") to Category.TRANSPORT,
            // Travel
            listOf("irctc", "makemytrip", "goibibo", "indigo", "air india", "spicejet",
                "akasa", "vistara", "oyo", "airbnb", "yatra", "easemytrip", "airline") to Category.TRAVEL,
            // Groceries
            listOf("bigbasket", "blinkit", "zepto", "instamart", "grofer", "jiomart",
                "nature basket", "dmart", "more supermarket") to Category.GROCERIES,
            // Shopping
            listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "snapdeal",
                "nykaa fashion", "tata cliq", "reliance retail") to Category.SHOPPING,
            // Recurring subscriptions
            listOf("netflix", "spotify", "hotstar", "disney", "youtube premium",
                "prime video", "apple subscription", "zee5", "jiocinema", "lenskart") to Category.RECURRING,
            // Utilities
            listOf("electricity", "bescom", "mseb", "tata power", "bses", "cesc",
                "water bill", "gas bill", "piped gas", "broadband", "jio fiber",
                "airtel", "vi ", "vodafone", "bsnl", "act fibernet") to Category.UTILITIES,
            // Rent
            listOf("rent", "landlord", "house rent", "flat rent") to Category.RENT,
            // Maintenance
            listOf("maintenance", "housing society", "society fee", "flat maintenance", "repair") to Category.MAINTENANCE,
            // Healthcare
            listOf("pharmacy", "hospital", "clinic", "apollo", "medplus", "1mg",
                "netmeds", "doctor", "medical", "lab test", "diagnostics") to Category.HEALTHCARE,
            // Self care
            listOf("salon", "spa", "nykaa", "beauty", "parlour", "barber",
                "wellness", "mani", "pedi") to Category.SELF_CARE,
            // Education
            listOf("school", "college", "university", "udemy", "coursera",
                "unacademy", "byju", "vedantu", "tuition", "exam fee", "fees") to Category.EDUCATION,
        )
    }
}
