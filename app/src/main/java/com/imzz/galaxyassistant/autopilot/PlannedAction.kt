package com.imzz.galaxyassistant.autopilot

data class PlannedAction(
    val type: String,
    val x: Double = 0.5,
    val y: Double = 0.5,
    val x2: Double = 0.5,
    val y2: Double = 0.5,
    val text: String = "",
    val waitMs: Long = 1500L,
    val reason: String = "",
    val speech: String = ""
)
