package com.example.auralocalai.ui.screens

import com.example.auralocalai.R

/** Explanations for the scale items: how to assess and what each score means. */
internal object ScaleAnchors {
    /** Score descriptions (anchors) per item; generated from the official CIWA-Ar, COWS and AUDIT forms. */
    val ANCHORS: Map<String, List<Pair<Int, Int>>> = mapOf(
        "ciwa_1" to listOf(0 to R.string.scale_ciwa_1_a0, 1 to R.string.scale_ciwa_1_a1, 4 to R.string.scale_ciwa_1_a4, 7 to R.string.scale_ciwa_1_a7),
        "ciwa_2" to listOf(0 to R.string.scale_ciwa_2_a0, 1 to R.string.scale_ciwa_2_a1, 4 to R.string.scale_ciwa_2_a4, 7 to R.string.scale_ciwa_2_a7),
        "ciwa_3" to listOf(0 to R.string.scale_ciwa_3_a0, 1 to R.string.scale_ciwa_3_a1, 4 to R.string.scale_ciwa_3_a4, 7 to R.string.scale_ciwa_3_a7),
        "ciwa_4" to listOf(0 to R.string.scale_ciwa_4_a0, 1 to R.string.scale_ciwa_4_a1, 4 to R.string.scale_ciwa_4_a4, 7 to R.string.scale_ciwa_4_a7),
        "ciwa_5" to listOf(0 to R.string.scale_ciwa_5_a0, 1 to R.string.scale_ciwa_5_a1, 4 to R.string.scale_ciwa_5_a4, 7 to R.string.scale_ciwa_5_a7),
        "ciwa_6" to listOf(0 to R.string.scale_ciwa_6_a0, 1 to R.string.scale_ciwa_6_a1, 2 to R.string.scale_ciwa_6_a2, 3 to R.string.scale_ciwa_6_a3, 4 to R.string.scale_ciwa_6_a4, 5 to R.string.scale_ciwa_6_a5, 6 to R.string.scale_ciwa_6_a6, 7 to R.string.scale_ciwa_6_a7),
        "ciwa_7" to listOf(0 to R.string.scale_ciwa_7_a0, 1 to R.string.scale_ciwa_7_a1, 2 to R.string.scale_ciwa_7_a2, 3 to R.string.scale_ciwa_7_a3, 4 to R.string.scale_ciwa_7_a4, 5 to R.string.scale_ciwa_7_a5, 6 to R.string.scale_ciwa_7_a6, 7 to R.string.scale_ciwa_7_a7),
        "ciwa_8" to listOf(0 to R.string.scale_ciwa_8_a0, 1 to R.string.scale_ciwa_8_a1, 2 to R.string.scale_ciwa_8_a2, 3 to R.string.scale_ciwa_8_a3, 4 to R.string.scale_ciwa_8_a4, 5 to R.string.scale_ciwa_8_a5, 6 to R.string.scale_ciwa_8_a6, 7 to R.string.scale_ciwa_8_a7),
        "ciwa_9" to listOf(0 to R.string.scale_ciwa_9_a0, 1 to R.string.scale_ciwa_9_a1, 2 to R.string.scale_ciwa_9_a2, 3 to R.string.scale_ciwa_9_a3, 4 to R.string.scale_ciwa_9_a4, 5 to R.string.scale_ciwa_9_a5, 6 to R.string.scale_ciwa_9_a6, 7 to R.string.scale_ciwa_9_a7),
        "ciwa_10" to listOf(0 to R.string.scale_ciwa_10_a0, 1 to R.string.scale_ciwa_10_a1, 2 to R.string.scale_ciwa_10_a2, 3 to R.string.scale_ciwa_10_a3, 4 to R.string.scale_ciwa_10_a4),
        "cows_1" to listOf(0 to R.string.scale_cows_1_a0, 1 to R.string.scale_cows_1_a1, 2 to R.string.scale_cows_1_a2, 4 to R.string.scale_cows_1_a4),
        "cows_2" to listOf(0 to R.string.scale_cows_2_a0, 1 to R.string.scale_cows_2_a1, 2 to R.string.scale_cows_2_a2, 3 to R.string.scale_cows_2_a3, 4 to R.string.scale_cows_2_a4),
        "cows_3" to listOf(0 to R.string.scale_cows_3_a0, 1 to R.string.scale_cows_3_a1, 3 to R.string.scale_cows_3_a3, 5 to R.string.scale_cows_3_a5),
        "cows_4" to listOf(0 to R.string.scale_cows_4_a0, 1 to R.string.scale_cows_4_a1, 2 to R.string.scale_cows_4_a2, 5 to R.string.scale_cows_4_a5),
        "cows_5" to listOf(0 to R.string.scale_cows_5_a0, 1 to R.string.scale_cows_5_a1, 2 to R.string.scale_cows_5_a2, 4 to R.string.scale_cows_5_a4),
        "cows_6" to listOf(0 to R.string.scale_cows_6_a0, 1 to R.string.scale_cows_6_a1, 2 to R.string.scale_cows_6_a2, 4 to R.string.scale_cows_6_a4),
        "cows_7" to listOf(0 to R.string.scale_cows_7_a0, 1 to R.string.scale_cows_7_a1, 2 to R.string.scale_cows_7_a2, 3 to R.string.scale_cows_7_a3, 5 to R.string.scale_cows_7_a5),
        "cows_8" to listOf(0 to R.string.scale_cows_8_a0, 1 to R.string.scale_cows_8_a1, 2 to R.string.scale_cows_8_a2, 4 to R.string.scale_cows_8_a4),
        "cows_9" to listOf(0 to R.string.scale_cows_9_a0, 1 to R.string.scale_cows_9_a1, 2 to R.string.scale_cows_9_a2, 4 to R.string.scale_cows_9_a4),
        "cows_10" to listOf(0 to R.string.scale_cows_10_a0, 1 to R.string.scale_cows_10_a1, 2 to R.string.scale_cows_10_a2, 4 to R.string.scale_cows_10_a4),
        "cows_11" to listOf(0 to R.string.scale_cows_11_a0, 3 to R.string.scale_cows_11_a3, 5 to R.string.scale_cows_11_a5),
        "audit_q1" to listOf(0 to R.string.scale_audit_q1_a0, 1 to R.string.scale_audit_q1_a1, 2 to R.string.scale_audit_q1_a2, 3 to R.string.scale_audit_q1_a3, 4 to R.string.scale_audit_q1_a4),
        "audit_q2" to listOf(0 to R.string.scale_audit_q2_a0, 1 to R.string.scale_audit_q2_a1, 2 to R.string.scale_audit_q2_a2, 3 to R.string.scale_audit_q2_a3, 4 to R.string.scale_audit_q2_a4),
        "audit_freq" to listOf(0 to R.string.scale_audit_freq_a0, 1 to R.string.scale_audit_freq_a1, 2 to R.string.scale_audit_freq_a2, 3 to R.string.scale_audit_freq_a3, 4 to R.string.scale_audit_freq_a4),
        "audit_yn" to listOf(0 to R.string.scale_audit_yn_a0, 2 to R.string.scale_audit_yn_a2, 4 to R.string.scale_audit_yn_a4),
    )
    val HOW: Map<String, Int> = mapOf(
        "ciwa_1" to R.string.scale_ciwa_1_how,
        "ciwa_2" to R.string.scale_ciwa_2_how,
        "ciwa_3" to R.string.scale_ciwa_3_how,
        "ciwa_4" to R.string.scale_ciwa_4_how,
        "ciwa_5" to R.string.scale_ciwa_5_how,
        "ciwa_6" to R.string.scale_ciwa_6_how,
        "ciwa_7" to R.string.scale_ciwa_7_how,
        "ciwa_8" to R.string.scale_ciwa_8_how,
        "ciwa_9" to R.string.scale_ciwa_9_how,
        "ciwa_10" to R.string.scale_ciwa_10_how,
        "cows_1" to R.string.scale_cows_1_how,
        "cows_2" to R.string.scale_cows_2_how,
        "cows_3" to R.string.scale_cows_3_how,
        "cows_4" to R.string.scale_cows_4_how,
        "cows_5" to R.string.scale_cows_5_how,
        "cows_6" to R.string.scale_cows_6_how,
        "cows_7" to R.string.scale_cows_7_how,
        "cows_8" to R.string.scale_cows_8_how,
        "cows_9" to R.string.scale_cows_9_how,
        "cows_10" to R.string.scale_cows_10_how,
        "cows_11" to R.string.scale_cows_11_how,
    )
}
