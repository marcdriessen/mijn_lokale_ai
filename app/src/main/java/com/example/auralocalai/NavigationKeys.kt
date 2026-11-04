package com.example.auralocalai

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data object ModelManager : NavKey
@Serializable data object Settings : NavKey
@Serializable data object Briefing : NavKey
@Serializable data object Medical : NavKey

@Serializable data object Home : NavKey
@Serializable data object Translate : NavKey
@Serializable data object Coach : NavKey
