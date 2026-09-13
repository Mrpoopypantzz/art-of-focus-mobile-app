package com.artoffocus.app.domain.achievements

/**
 * Which achievements a just-finished painting actually earned.
 *
 * Pure and separate from the ViewModel so the rule can be tested without a database, and so
 * the notification can never invent an achievement: this only ever *filters* what
 * [earnedAchievements] already reports as earned.
 *
 * I15 — an earned achievement is never removed. Nothing here can un-earn one; an achievement
 * held before is simply absent from the result, which means "no notification", not "lost".
 */
fun newlyEarned(before: Set<Achievement>, after: List<Achievement>): List<Achievement> =
    after.filterNot { it in before }
