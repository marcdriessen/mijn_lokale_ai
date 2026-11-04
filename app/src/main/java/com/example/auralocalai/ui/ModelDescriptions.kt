package com.example.auralocalai.ui

import androidx.annotation.StringRes
import com.example.auralocalai.R

/** Translated descriptions of the built-in models (English text in ModelPreset is the fallback). */
@StringRes
fun modelDescriptionRes(presetId: String): Int? = when (presetId) {
    "qwen3-0.6b" -> R.string.model_desc_qwen3_0_6b
    "qwen-1.5b" -> R.string.model_desc_qwen_1_5b
    "qwen3-1.7b" -> R.string.model_desc_qwen3_1_7b
    "qwen2.5-coder-1.5b" -> R.string.model_desc_qwen2_5_coder_1_5b
    "deepseek-1.5b" -> R.string.model_desc_deepseek_1_5b
    "gemma4-e2b" -> R.string.model_desc_gemma4_e2b
    "gemma4-e2b-uncensored" -> R.string.model_desc_gemma4_e2b_uncensored
    "supergemma4-e4b-abliterated" -> R.string.model_desc_supergemma4_e4b_abliterated
    "qwen3-4b" -> R.string.model_desc_qwen3_4b
    "gemma4-e4b" -> R.string.model_desc_gemma4_e4b
    "medgemma-1.5-4b" -> R.string.model_desc_medgemma
    "hy-mt2-1.8b" -> R.string.model_desc_hy_mt2
    "ministral-3-3b" -> R.string.model_desc_ministral3
    "qwen3.5-4b" -> R.string.model_desc_qwen3_5_4b
    "phi-4-mini" -> R.string.model_desc_phi4_mini
    "qwen3-8b" -> R.string.model_desc_qwen3_8b
    "gemma4-12b" -> R.string.model_desc_gemma4_12b
    else -> null
}
